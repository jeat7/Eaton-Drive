#!/usr/bin/env python3
"""Generate compact regional offline street/house number search from OpenStreetMap.
West Midlands includes Stoke-on-Trent, Wolverhampton, Dudley and Birmingham.
OpenStreetMap has incomplete address coverage: only mapped objects are searchable.
The app uses SQLite FTS4 and never calls an online geocoder.
"""
import argparse
import csv
import gzip
import io
import math
import collections
import re
import hashlib
import json
import os
import sqlite3
import tempfile
import urllib.request
from pathlib import Path
import osmium

POSTCODE_SOURCE="https://postcodesuk.com/downloads/data/uk-postcodes.csv.gz"
REGIONS={
    "west-midlands":"https://download.geofabrik.de/europe/united-kingdom/england/west-midlands-latest.osm.pbf",
}
class Indexer(osmium.SimpleHandler):
    def __init__(self,db):
        super().__init__()
        self.db=db
        self.batch=[]
        self.count=0
        self.streets=0
        self.houses=0
        self.places=0
        self.seen=set()
    def record(self,name,house,place,kind,lat,lon):
        if not name or not -10 < lon < 3 or not 49 < lat < 60: return
        name=name[:160].strip()
        house=(house or "")[:35].strip()
        place=(place or "")[:160].strip()
        if not name: return
        # Avoid writing duplicate road segment names extremely close together.
        key=(name.lower(),house.lower(),round(lat,4),round(lon,4))
        if key in self.seen: return
        # Limit dedup memory. High street sections remain searchable when separate.
        if len(self.seen)<400_000: self.seen.add(key)
        self.batch.append((name,house,place,kind,lat,lon))
        self.count+=1
        if kind=="house": self.houses+=1
        elif kind=="road": self.streets+=1
        else: self.places+=1
        if len(self.batch)>=8000:self.flush()
    def flush(self):
        if not self.batch:return
        self.db.executemany("INSERT INTO locations(name,house,place,kind,lat,lon) VALUES(?,?,?,?,?,?)",self.batch)
        self.db.commit()
        self.batch.clear()
        if self.count%80000<8000: print("indexed",self.count,"locations",flush=True)
    def process(self, tags,lat,lon):
        if not tags:return
        place=tags.get("addr:city") or tags.get("addr:town") or tags.get("addr:village") or ""
        house=tags.get("addr:housenumber")
        road=tags.get("addr:street")
        if road and house:
            self.record(road,house,place,"house",lat,lon);return
        if tags.get("highway") and tags.get("name"):
            self.record(tags.get("name"),"",place,"road",lat,lon);return
        if tags.get("place") and tags.get("name"):
            self.record(tags.get("name"),"",place,"locality",lat,lon);return
        if tags.get("amenity") and tags.get("name"):
            self.record(tags.get("name"),"",place,"poi",lat,lon)
    def node(self, n):
        try:
            if n.location.valid():self.process(n.tags,n.location.lat,n.location.lon)
        except (ValueError,RuntimeError): pass
    def way(self, w):
        try:
            if not len(w.nodes):return
            locations=[n.location for n in w.nodes if n.location.valid()]
            if not locations:return
            loc=locations[len(locations)//2]
            self.process(w.tags,loc.lat,loc.lon)
        except (ValueError,RuntimeError): pass


# Coordinates and postcodes are approximate; a district is NOT a full address.
# A geo bucket provides quick local enrichment without loading the postcode dataset
# on the 1GB Android head unit.
def postcode_district(code):
    code=re.sub("[^A-Z0-9]","",(code or "").upper())
    return code[:-3] if len(code) >= 5 and len(code)<=7 else ""

def build_district_cells():
    print("Preparing local postcode district grid", flush=True)
    req=urllib.request.Request(POSTCODE_SOURCE,headers={"User-Agent":"EatonDrive Offline Address Index/2"})
    grid={}
    found=0
    with urllib.request.urlopen(req,timeout=120) as response:
        if response.status!=200: raise RuntimeError("Postcodes HTTP "+str(response.status))
        csv_data=csv.DictReader(io.TextIOWrapper(gzip.GzipFile(fileobj=response),
                             encoding="utf-8-sig",newline=""))
        cols={s.lower():s for s in (csv_data.fieldnames or [])}
        pc=cols.get("full_postcode") or cols.get("postcode")
        la=cols.get("latitude");lo=cols.get("longitude")
        if not all((pc,la,lo)):raise RuntimeError("Unexpected postcode headers")
        for row in csv_data:
            try:
                lat=float(row[la]);lon=float(row[lo])
            except (ValueError,TypeError,KeyError):continue
            # West Midlands and surrounding border so nearby district matches work.
            if not (51.5<=lat<=54.5 and -4.3<=lon<=-0.5):continue
            district=postcode_district(row.get(pc))
            if not district or district.startswith("BT"):continue
            key=(round(lat*200),round(lon*200),district)
            item=grid.get(key)
            if item is None:grid[key]=[lat,lon,1]
            else:item[0]+=lat;item[1]+=lon;item[2]+=1
            found+=1
    if found<100_000:raise RuntimeError("Too few postcodes for Midlands region")
    result={}
    for (x,y,district),(la,lo,n) in grid.items():
        result.setdefault((x,y),[]).append((la/n,lo/n,district))
    print("District samples:",found,"grid cells:",len(result),flush=True)
    return result

def enrich_database(db, dest, district_grid):
    # Only OSM place-tag features become locality candidates; POIs are excluded.
    candidates={}
    for name,lat,lon in db.execute("SELECT name,lat,lon FROM locations WHERE kind='locality'"):
        try:lat=float(lat);lon=float(lon)
        except (ValueError,TypeError):continue
        if len(name)<2:continue
        key=(round(lat*25),round(lon*25))
        candidates.setdefault(key,[]).append((name,lat,lon))
    if len(candidates)<50:raise RuntimeError("Locality index unexpectedly empty")
    def locality(lat,lon):
        x=round(lat*25);y=round(lon*25)
        best=(float("inf"),"")
        for i in range(x-2,x+3):
            for j in range(y-2,y+3):
                for name,a,b in candidates.get((i,j),()):
                    delta=(a-lat)**2 + ((b-lon)*math.cos(math.radians(lat)))**2
                    if delta<best[0]:best=(delta,name)
        # Avoid labelling rural roads with towns >~6km away.
        return best[1] if best[0]<(.055)**2 else ""
    def district(lat,lon):
        x=round(lat*200);y=round(lon*200)
        best=(float("inf"),"")
        for i in range(x-3,x+4):
            for j in range(y-3,y+4):
                for a,b,pc in district_grid.get((i,j),()):
                    delta=(a-lat)**2 + ((b-lon)*math.cos(math.radians(lat)))**2
                    if delta<best[0]:best=(delta,pc)
        return best[1] if best[0]<(.017)**2 else ""
    tmp=dest.with_suffix(".new")
    if tmp.exists():tmp.unlink()
    enriched=sqlite3.connect(tmp)
    enriched.execute("PRAGMA journal_mode=OFF")
    enriched.execute("PRAGMA synchronous=OFF")
    enriched.execute("PRAGMA user_version=2")
    enriched.execute("CREATE VIRTUAL TABLE locations USING fts4(name,house,place,kind,lat,lon,locality,district,notindexed=lat,notindexed=lon,notindexed=kind,tokenize=unicode61)")
    batch=[]
    assigned_locality=assigned_district=0
    examples={}
    for name,house,place,kind,lat,lon in db.execute("SELECT name,house,place,kind,lat,lon FROM locations"):
        lat=float(lat);lon=float(lon)
        area=(place or "").strip()
        # Places found through tags may give a more specific neighbourhood than city.
        nearby=locality(lat,lon)
        if nearby and (not area or area.casefold()!=nearby.casefold()):area=nearby
        pc=district(lat,lon)
        if area:assigned_locality+=1
        if pc:assigned_district+=1
        if name=="Crayford Road":
            examples[name]={"locality":area,"postcode_district":pc}
        batch.append((name,house,place,kind,lat,lon,area,pc))
        if len(batch)>=2500:
            enriched.executemany("INSERT INTO locations(name,house,place,kind,lat,lon,locality,district) VALUES(?,?,?,?,?,?,?,?)",batch)
            enriched.commit();batch.clear()
    if batch:
        enriched.executemany("INSERT INTO locations(name,house,place,kind,lat,lon,locality,district) VALUES(?,?,?,?,?,?,?,?)",batch)
        enriched.commit()
    enriched.execute("INSERT INTO locations(locations) VALUES('optimize')")
    enriched.commit()
    check=enriched.execute("PRAGMA quick_check").fetchone()[0]
    if check!="ok":raise RuntimeError("Enriched index failed integrity check")
    test=enriched.execute("SELECT name,locality,district FROM locations WHERE locations MATCH 'crayford* AND road*' LIMIT 1").fetchone()
    if not test or test[1]!="Kingstanding" or test[2]!="B44":
        raise RuntimeError("Crayford Road reference not correctly enriched: "+repr(test))
    count=enriched.execute("SELECT COUNT(*) FROM locations").fetchone()[0]
    enriched.close()
    db.close()
    os.replace(tmp,dest)
    return {"localities_attached":assigned_locality,"districts_attached":assigned_district,
            "index_records":count,"verification_crayford_road":examples.get("Crayford Road")}

def create(region,out):
    out=Path(out)
    out.mkdir(parents=True,exist_ok=True)
    url=REGIONS[region]
    with tempfile.TemporaryDirectory(prefix="eaton-addr-") as workspace:
        raw=Path(workspace)/"region.osm.pbf"
        print("Downloading",url,flush=True)
        req=urllib.request.Request(url,headers={"User-Agent":"EatonDrive/0.7 address index creator (OSM attribution)"})
        with urllib.request.urlopen(req,timeout=120) as response,raw.open("wb") as output:
            if response.status!=200: raise RuntimeError("Download HTTP "+str(response.status))
            while True:
                block=response.read(1024*1024)
                if not block:break
                output.write(block)
        if raw.stat().st_size<15_000_000:raise RuntimeError("Suspiciously small OSM extract")
        dest=out/(region+"-addresses.db")
        if dest.exists():dest.unlink()
        db=sqlite3.connect(dest)
        db.execute("PRAGMA page_size=4096")
        db.execute("PRAGMA journal_mode=OFF")
        db.execute("PRAGMA synchronous=OFF")
        db.execute("PRAGMA cache_size=-25000")
        db.execute("PRAGMA user_version=1")
        db.execute("CREATE VIRTUAL TABLE locations USING fts4(name,house,place,kind,lat,lon,notindexed=lat,notindexed=lon,notindexed=kind,tokenize=unicode61)")
        handler=Indexer(db)
        handler.apply_file(str(raw),locations=True,idx="flex_mem")
        handler.flush()
        if handler.count<3000: raise RuntimeError("Insufficient indexed addresses/roads: "+str(handler.count))
        db.execute("INSERT INTO locations(locations) VALUES('optimize')")
        db.commit()
        if db.execute("PRAGMA quick_check").fetchone()[0]!="ok":raise RuntimeError("Search DB corrupt")
        sample=db.execute("SELECT name FROM locations WHERE locations MATCH 'street*' LIMIT 1").fetchone()
        if not sample:raise RuntimeError("FTS verification failed")
        enrichment=enrich_database(db,dest,build_district_cells())
        sha=hashlib.sha256(dest.read_bytes()).hexdigest()
        manifest={"region":region,"entries":handler.count,"road_segments":handler.streets,"houses":handler.houses,
                  "places":handler.places,"file":dest.name,"bytes":dest.stat().st_size,
                  "schema":2,"enrichment":enrichment,"postcode_source":POSTCODE_SOURCE,
                  "sha256":sha,"source":url,"licence":"© OpenStreetMap contributors, ODbL; Geofabrik regional extract.",
                  "warning":"Search contains only mapped streets and mapped house-number addresses, often with approximate road-centre locations. Not a Royal Mail address database."}
        (out/"ADDRESS-MANIFEST.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
        (out/"IMPORT-ADDRESSES.txt").write_text(
            "EATON DRIVE OFFLINE STREET/ADDRESS SEARCH\n"
            "Extract this artifact ZIP on your phone or computer. Keep the .db file.\n"
            "In Eaton Drive open Navigation -> SETUP -> Import address database.\n"
            "Select the .db once. Search via Navigation -> ADDRESS.\n"
            "This regional dataset covers West Midlands only. Addresses are limited to OSM-mapped points.\n"
            "Results include nearest mapped locality and an approximate postal district (e.g. Kingstanding, B44).\n"
            "Road-name-only entries may point to the centre of one road segment, not a specific property.\n"
            "© OpenStreetMap contributors, Open Database Licence (ODbL)\n"
            "https://www.openstreetmap.org/copyright\n",
            encoding="utf-8")
        print(json.dumps(manifest,indent=2),flush=True)

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--region",choices=list(REGIONS),default="west-midlands")
    p.add_argument("output",nargs="?",default="out/addresses")
    args=p.parse_args()
    create(args.region,args.output)
