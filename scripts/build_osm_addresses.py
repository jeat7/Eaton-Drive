#!/usr/bin/env python3
"""Generate compact regional offline street/house number search from OpenStreetMap.
West Midlands includes Stoke-on-Trent, Wolverhampton, Dudley and Birmingham.
OpenStreetMap has incomplete address coverage: only mapped objects are searchable.
The app uses SQLite FTS4 and never calls an online geocoder.
"""
import argparse
import hashlib
import json
import os
import sqlite3
import tempfile
import urllib.request
from pathlib import Path
import osmium

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
            self.record(tags.get("name"),"",place,"place",lat,lon);return
        if tags.get("amenity") and tags.get("name"):
            self.record(tags.get("name"),"",place,"place",lat,lon)
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
        db.close()
        sha=hashlib.sha256(dest.read_bytes()).hexdigest()
        manifest={"region":region,"entries":handler.count,"road_segments":handler.streets,"houses":handler.houses,
                  "places":handler.places,"file":dest.name,"bytes":dest.stat().st_size,
                  "sha256":sha,"source":url,"licence":"© OpenStreetMap contributors, ODbL; Geofabrik regional extract.",
                  "warning":"Search contains only mapped streets and mapped house-number addresses, often with approximate road-centre locations. Not a Royal Mail address database."}
        (out/"ADDRESS-MANIFEST.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
        (out/"IMPORT-ADDRESSES.txt").write_text(
            "EATON DRIVE OFFLINE STREET/ADDRESS SEARCH\n"
            "Extract this artifact ZIP on your phone or computer. Keep the .db file.\n"
            "In Eaton Drive open Navigation -> SETUP -> Import address database.\n"
            "Select the .db once. Search via Navigation -> ADDRESS.\n"
            "This regional dataset covers West Midlands only. Addresses are limited to OSM-mapped points.\n"
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
