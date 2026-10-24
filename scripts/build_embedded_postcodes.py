#!/usr/bin/env python3
"""Create an indexed Great Britain postcode SQLite asset before building Eaton Drive.

Data: Postcodes UK, September 2026, licensed per third-party source notices.
GB only (BT excluded); uses bounded batches, not an in-memory 1.7M-row list.
The resulting .db is packaged inside the Android APK and installed once.
"""
import csv
import gzip
import hashlib
import io
import os
import re
import sqlite3
import sys
import urllib.request
from pathlib import Path

SOURCE = "https://postcodesuk.com/downloads/data/uk-postcodes.csv.gz"
VALID = re.compile(r"^[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}$")
ASSET_PATH = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/gb-postcodes.db")

def build():
    ASSET_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary = ASSET_PATH.with_name("gb-postcodes-incomplete.db")
    temporary.unlink(missing_ok=True)
    request = urllib.request.Request(SOURCE, headers={"User-Agent":"EatonDrive offline postcode build 0.5"})
    conn = None
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            if response.status != 200:
                raise RuntimeError(f"Postcode source returned HTTP {response.status}")
            compressed = gzip.GzipFile(fileobj=response)
            rows = csv.DictReader(io.TextIOWrapper(compressed, encoding="utf-8-sig", newline=""))
            names = {name.strip().lower():name for name in (rows.fieldnames or [])}
            pc_key = names.get("full_postcode") or names.get("postcode")
            lat_key, lon_key = names.get("latitude"), names.get("longitude")
            if not all((pc_key,lat_key,lon_key)):
                raise RuntimeError("Unexpected postcode source headers")
            conn = sqlite3.connect(temporary)
            conn.execute("PRAGMA page_size=4096")
            conn.execute("PRAGMA journal_mode=OFF")
            conn.execute("PRAGMA synchronous=OFF")
            conn.execute("PRAGMA cache_size=-50000")
            conn.execute("CREATE TABLE postcodes(code TEXT PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL) WITHOUT ROWID")
            conn.execute("PRAGMA user_version=5")
            batch = []
            seen = included = 0
            for row in rows:
                seen += 1
                code = re.sub("[^A-Z0-9]", "", row.get(pc_key,"").upper())
                if not VALID.fullmatch(code) or code.startswith("BT"):
                    continue
                try:
                    lat, lon = float(row[lat_key]), float(row[lon_key])
                    if not (49 <= lat <= 61 and -9 <= lon <= 3):
                        continue
                except (ValueError, TypeError):
                    continue
                batch.append((code, lat, lon))
                included += 1
                if len(batch) >= 15000:
                    conn.executemany("INSERT OR REPLACE INTO postcodes VALUES(?,?,?)", batch)
                    conn.commit()
                    batch.clear()
                    if included % 150000 < 15000:
                        print("Processed", included, "GB postcode records",flush=True)
            if batch:
                conn.executemany("INSERT OR REPLACE INTO postcodes VALUES(?,?,?)",batch)
                conn.commit()
            actual = conn.execute("SELECT COUNT(*) FROM postcodes").fetchone()[0]
            if actual < 1_500_000 or seen < 1_700_000:
                raise RuntimeError(f"Too few postcode records: scanned={seen} accepted={included} indexed={actual}")
            if conn.execute("PRAGMA quick_check").fetchone()[0] != "ok":
                raise RuntimeError("SQLite database failed quick_check")
            print(f"Offline postcode database: scanned={seen}, indexed={actual}, size={temporary.stat().st_size:,}",flush=True)
        conn.close(); conn = None
        os.replace(temporary,ASSET_PATH)
        digest = hashlib.sha256()
        with ASSET_PATH.open("rb") as stream:
            for block in iter(lambda: stream.read(1024*1024), b""):
                digest.update(block)
        print("APK postcode asset sha256:",digest.hexdigest(),flush=True)
    finally:
        if conn is not None:
            conn.close()
        if temporary.exists():
            temporary.unlink()

if __name__=="__main__":
    build()
