#!/usr/bin/env python3
"""Create a validated Great Britain postcode import file for Eaton Drive.

2026 source: Postcodes UK live postcode CSV.GZ, derived from public sector data.
Northern Ireland BT postcodes are deliberately excluded pending LPS licence review.
Output is CSV.GZ with only postcode,latitude,longitude.
"""
import csv
import gzip
import hashlib
import io
import json
import re
import shutil
import sys
import tempfile
import urllib.request
from pathlib import Path

SOURCE = "https://postcodesuk.com/downloads/data/uk-postcodes.csv.gz"
SOURCE_LANDING = "https://postcodesuk.com/data-downloads/uk-postcodes"
EXPECTED_ROWS = 1_827_826
VALID = re.compile(r"^[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}$")
OUTPUT = "eaton-drive-gb-postcodes.csv.gz"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build(outdir: Path):
    outdir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="eaton-postcodes-") as scratch:
        original = Path(scratch) / "uk-postcodes.csv.gz"
        request = urllib.request.Request(SOURCE, headers={"User-Agent": "EatonDrive/0.4 OfflineDataPack"})
        with urllib.request.urlopen(request, timeout=90) as response, original.open("wb") as sink:
            if response.status != 200:
                raise RuntimeError("Postcode download HTTP " + str(response.status))
            shutil.copyfileobj(response, sink, 256 * 1024)
        source_size = original.stat().st_size
        print("Source downloaded:", source_size, "bytes", flush=True)
        if not 5_000_000 < source_size < 120_000_000:
            raise RuntimeError("Postcode source too small or large: " + str(source_size))
        if original.open("rb").read(2) != b"\x1f\x8b":
            raise RuntimeError("Postcode provider did not return GZIP")
        out = outdir / OUTPUT
        imported = 0
        skipped = 0
        scanned = 0
        samples = set()
        with gzip.open(original, "rt", encoding="utf-8-sig", newline="") as read:
            reader = csv.DictReader(read)
            headers = {h.strip().lower(): h for h in reader.fieldnames or []}
            postcode_col = headers.get("full_postcode") or headers.get("postcode")
            lat_col, lon_col = headers.get("latitude"), headers.get("longitude")
            if not all((postcode_col, lat_col, lon_col)):
                raise RuntimeError("Incorrect source CSV headers: " + str(reader.fieldnames))
            with gzip.open(out, "wt", encoding="utf-8", newline="", compresslevel=6) as compressed:
                writer = csv.writer(compressed, lineterminator="\n")
                writer.writerow(["postcode", "latitude", "longitude"])
                for line in reader:
                    scanned += 1
                    postcode = re.sub(r"[^A-Z0-9]", "", line.get(postcode_col, "").upper())
                    if not VALID.fullmatch(postcode) or postcode.startswith("BT"):
                        skipped += 1
                        continue
                    try:
                        lat, lon = float(line[lat_col]), float(line[lon_col])
                        if not (49 <= lat <= 61 and -9 <= lon <= 3):
                            raise ValueError("outside GB")
                    except (ValueError, TypeError):
                        skipped += 1
                        continue
                    writer.writerow([postcode, f"{lat:.7f}", f"{lon:.7f}"])
                    imported += 1
                    if postcode in {"SW1A1AA", "B11AA", "EH11YZ", "CF101EP"}:
                        samples.add(postcode)
        if scanned < 1_700_000 or imported < 1_500_000 or imported > EXPECTED_ROWS:
            out.unlink(missing_ok=True)
            raise RuntimeError(f"Bad record counts scanned={scanned}, included={imported}")
    info = {
        "dataset": "Eaton Drive Great Britain postcodes",
        "source": SOURCE,
        "source_page": SOURCE_LANDING,
        "release": "September 2026",
        "original_rows": scanned,
        "included_GB_postcodes": imported,
        "excluded_or_invalid_records": skipped,
        "sample_codes_found": sorted(samples),
        "filename": OUTPUT,
        "output_bytes": out.stat().st_size,
        "output_sha256": sha256(out),
        "source_sha256": sha256(original) if original.exists() else None,
        "copyright": "Source: Postcodes UK, UK Postcodes, September 2026. Contains public sector information licensed under Open Government Licence 3.0; contains OS and Royal Mail data subject to applicable rights. Northern Ireland BT entries deliberately excluded.",
        "notice": "Only approximate postcode centroids, never specific building entrances; update periodically.",
    }
    (outdir/"POSTCODE-MANIFEST.json").write_text(json.dumps(info,indent=2)+"\n",encoding="utf-8")
    (outdir/"INSTALL-POSTCODES.txt").write_text(
        "EATON DRIVE OFFLINE POSTCODE IMPORT\n"
        "Copy " + OUTPUT + " to a USB stick. Do NOT unzip the CSV.GZ.\n"
        "In Eaton Drive: NAVIGATION -> IMPORT PCs -> choose the CSV.GZ.\n"
        "The head unit will create a searchable SQLite index. This is an offline step.\n"
        "Source: Postcodes UK (postcodesuk.com), UK Postcodes, September 2026.\n"
        "Contains public sector information licensed under OGL v3.0.\n"
        "Contains OS data © Crown copyright and database right 2026.\n"
        "Contains Royal Mail data © Royal Mail copyright and database right 2026.\n"
        "Northern Ireland postcodes are excluded. Check any source terms before redistribution.\n",
        encoding="utf-8",
    )
    print(json.dumps(info,indent=2))


if __name__ == "__main__":
    build(Path(sys.argv[1] if len(sys.argv) > 1 else "out/postcodes"))
