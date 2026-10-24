#!/usr/bin/env python3
"""Download and verify BRouter tiles and Mapsforge maps for Eaton Drive USB setup.

Run: python scripts/fetch_offline_maps.py --region great-britain --mode routing out/maps
Modes: routing (RD5), maps (.map), both.
Each downloaded file is SHA-256 hashed and size checked. Files stay unchanged.
"""
import argparse
import hashlib
import json
import os
import shutil
import urllib.request
from pathlib import Path

MAP_ROOT = "https://download.mapsforge.org/maps/v5/europe/united-kingdom/"
RD5_ROOT = "https://brouter.de/brouter/segments4/"

TILES = {
    # SW tile codes represent each 5°×5° square; include border crossings.
    "england": ["W10_N50", "W5_N50", "E0_N50", "W5_N55", "E0_N55"],
    "wales": ["W10_N50", "W5_N50"],
    "scotland": ["W10_N50", "W5_N50", "W10_N55", "W5_N55", "E0_N55", "W10_N60", "W5_N60", "E0_N60"],
}
MAPS = {
    "england": ["england.map"],
    "wales": ["wales.map"],
    "scotland": ["scotland.map"],
}
EXPECTED_MIN = {
    "england.map": 600_000_000,
    "wales.map": 50_000_000,
    "scotland.map": 120_000_000,
}
def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for part in iter(lambda: f.read(1024*1024), b""):
            digest.update(part)
    return digest.hexdigest()

def download(url, target, minimum):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.stat().st_size >= minimum:
        print("Using existing " + target.name, flush=True)
    else:
        candidate = target.with_suffix(target.suffix + ".partial")
        if candidate.exists():
            candidate.unlink()
        req = urllib.request.Request(url, headers={"User-Agent": "EatonDrive OfflineData 0.4", "Accept": "*/*"})
        with urllib.request.urlopen(req, timeout=120) as response, candidate.open("wb") as dest:
            if response.status != 200:
                raise RuntimeError(f"HTTP {response.status}: {url}")
            while True:
                chunk = response.read(1024*1024)
                if not chunk: break
                dest.write(chunk)
        actual = candidate.stat().st_size
        if actual < minimum:
            candidate.unlink(missing_ok=True)
            raise RuntimeError(f"{target.name} is too small ({actual} bytes); source may be blocked or unavailable")
        candidate.rename(target)
    return {"path": str(target), "source": url, "bytes": target.stat().st_size,
            "sha256": sha256(target)}

def build(region, mode, out):
    countries = list(TILES) if region == "great-britain" else [region]
    selected_maps = sorted({f for c in countries for f in MAPS[c]})
    selected_tiles = sorted({t + ".rd5" for c in countries for t in TILES[c]})
    requested = []
    if mode in ("maps", "both"):
        requested.extend(("maps", name, MAP_ROOT + name, EXPECTED_MIN[name])
                         for name in selected_maps)
    if mode in ("routing", "both"):
        requested.extend(("brouter-segments4", name, RD5_ROOT + name, 1_000)
                         for name in selected_tiles)
    free = shutil.disk_usage(out.parent if out.parent.exists() else ".").free
    if free < 2_000_000_000:
        print("Warning: less than 2GB free on downloader", flush=True)
    details = []
    for i, (folder, filename, url, minimum) in enumerate(requested, 1):
        print(f"Downloading {i}/{len(requested)}: {filename} from {url}", flush=True)
        item = download(url, out / folder / filename, minimum)
        item["path"] = folder + "/" + filename
        details.append(item)
        print(f"Verified {filename}: {item['bytes']:,} bytes", flush=True)
    out.mkdir(parents=True, exist_ok=True)
    metadata = {
        "product": "Eaton Drive USB offline map and routing files",
        "region": region,
        "mode": mode,
        "data": details,
        "total_bytes": sum(i["bytes"] for i in details),
        "note": "These files are third-party data and must remain unmodified; download times and dataset dates vary.",
        "licence": "Maps/routing derived from OpenStreetMap; © OpenStreetMap contributors, ODbL. Review Mapforge and BRouter distributions for notices."
    }
    (out / "OFFLINE-MAPS-MANIFEST.json").write_text(json.dumps(metadata,indent=2) + "\n",encoding="utf-8")
    (out / "INSTALL-MAPS-AND-ROUTING.txt").write_text(
        "EATON DRIVE - OFFLINE MAPS AND ROUTING DATA\n"
        "MAPS: Copy .map file(s) to the USB stick. In Eaton Drive open NAVIGATION -> IMPORT MAP.\n"
        "Note: Eaton Drive v0.3 supports only one map at once; GB multi-map support is in development.\n"
        "ROUTING: .rd5 files must go to BRouter's configured brouter/segments4 directory.\n"
        "Android 11 permits BRouter's directory in Android/media/btools.routingapp/brouter/segments4.\n"
        "Install and initialise the signed BRouter app first. The BRouter download manager can\n"
        "also download these segments directly (a temporary hotspot may simplify this).\n"
        "Do NOT choose .rd5 files in Eaton Drive's IMPORT MAP picker.\n"
        "The APK, .map, .rd5, postcode data and offline text-to-speech voice are independent.\n"
        "Crediting data: © OpenStreetMap contributors; https://www.openstreetmap.org/copyright\n",
        encoding="utf-8"
    )
    print(json.dumps({"region":region,"mode":mode,"total_MB":round(metadata["total_bytes"]/1_000_000,1),
                      "files":[i["path"] for i in details]},indent=2), flush=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", choices=["england","wales","scotland","great-britain"],
                        default="great-britain")
    parser.add_argument("--mode", choices=["routing","maps","both"], default="routing")
    parser.add_argument("output", nargs="?", default="out/maps")
    args = parser.parse_args()
    build(args.region,args.mode,Path(args.output))
