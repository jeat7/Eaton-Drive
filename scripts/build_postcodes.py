#!/usr/bin/env python3
"""Prepare a small offline postcode CSV.GZ suitable for Eaton Drive's IMPORT PCs.

Input: GetTheData's attribution-required Open Postcode Geo CSV ZIP (Aug 2023).
Output: postcode,latitude,longitude; no easting/northing-only files.
No postcode coordinates are invented. NI is omitted by this open dataset.
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
import zipfile
from pathlib import Path

SOURCE = "https://download.getthedata.com/open_postcode_geo.csv.zip"
VALID = re.compile(r"^[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}$")
OUTPUT = "eaton-drive-gb-postcodes.csv.gz"


def download(url: str, destination: Path):
    request = urllib.request.Request(url, headers={"User-Agent": "EatonDriveOfflineData/0.4 (personal offline map preparation)"})
    with urllib.request.urlopen(request, timeout=90) as response, destination.open("wb") as sink:
        if response.status != 200:
            raise RuntimeError(f"Download failed: HTTP {response.status}")
        shutil.copyfileobj(response, sink, 256 * 1024)


def build(outdir: Path):
    outdir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="eaton-postcode-") as temp:
        archive = Path(temp) / "source.zip"
        download(SOURCE, archive)
        print("SOURCE_DOWNLOAD_CHECK", archive.stat().st_size, repr(archive.open("rb").read(300)), flush=True)
        if archive.stat().st_size < 100_000 or archive.stat().st_size > 300_000_000:
            raise RuntimeError("Postcode archive had an unexpected size")
        with zipfile.ZipFile(archive) as container:
            candidates = [f for f in container.infolist() if f.filename.lower().endswith(".csv")
                          and not f.is_dir() and f.file_size > 100_000]
            if not candidates:
                raise RuntimeError("No CSV in postcode archive")
            csv_file = max(candidates, key=lambda f: f.file_size)
            out = outdir / OUTPUT
            imported, skipped, scanned = 0, 0, 0
            examples = set()
            with container.open(csv_file) as binary:
                reader = csv.DictReader(io.TextIOWrapper(binary, encoding="utf-8-sig", newline=""))
                fields = {x.strip().lower(): x for x in (reader.fieldnames or [])}
                required = {"postcode", "latitude", "longitude"}
                if not required.issubset(fields):
                    raise RuntimeError("Unexpected CSV header: " + str(reader.fieldnames))
                status = fields.get("status")
                with gzip.open(out, "wt", encoding="utf-8", newline="", compresslevel=6) as zipout:
                    writer = csv.writer(zipout, lineterminator="\n")
                    writer.writerow(["postcode", "latitude", "longitude"])
                    for item in reader:
                        scanned += 1
                        if status and item.get(status, "").strip().lower() not in {"live", ""}:
                            skipped += 1
                            continue
                        pc = re.sub(r"[^A-Z0-9]", "", item.get(fields["postcode"], "").upper())
                        if not VALID.fullmatch(pc):
                            skipped += 1
                            continue
                        try:
                            lat = float(item[fields["latitude"]])
                            lon = float(item[fields["longitude"]])
                            if not 49 <= lat <= 61 or not -9 <= lon <= 3:
                                raise ValueError("coordinate out of range")
                        except (ValueError, TypeError):
                            skipped += 1
                            continue
                        writer.writerow([pc, f"{lat:.7f}", f"{lon:.7f}"])
                        imported += 1
                        if pc in {"SW1A1AA", "B11AA", "EH11YZ", "CF101EP"}:
                            examples.add(pc)
            if imported < 1_000_000:
                raise RuntimeError(f"Insufficient valid postcodes: {imported} ({scanned} rows seen)")
    sha = hashlib.sha256(out.read_bytes()).hexdigest()
    details = {
        "dataset": "Eaton Drive Great Britain postcode import",
        "original_source": SOURCE,
        "original_version": "ONS Postcode Directory August 2023, via GetTheData",
        "source_landing_page": "https://www.getthedata.com/open-postcode-geo",
        "file": OUTPUT,
        "postcode_records": imported,
        "scanned": scanned,
        "skipped": skipped,
        "verified_samples_found": sorted(examples),
        "sha256": sha,
        "compressed_bytes": out.stat().st_size,
        "import_format": "CSV.GZ with postcode,latitude,longitude",
        "copyright": "Contains public sector information licensed under the Open Government Licence v3.0 and Ordnance Survey OpenData Licence; source GetTheData. Northern Irish postcode coordinates excluded.",
        "warning": "August 2023 coordinates are stale; newer postcodes may not be present; use only for trip planning, not exact property entrances.",
    }
    (outdir / "POSTCODE-MANIFEST.json").write_text(json.dumps(details, indent=2) + "\n", encoding="utf-8")
    (outdir / "HOW-TO-IMPORT.txt").write_text(
        "EATON DRIVE OFFLINE POSTCODES\n"
        "1. Extract this ZIP on your PC, but leave the CSV.GZ file compressed.\n"
        "2. Copy " + OUTPUT + " onto a USB stick.\n"
        "3. On the head unit: Eaton Drive -> NAVIGATION -> IMPORT PCs.\n"
        "4. Select the CSV.GZ. The head unit builds its own index; this may take time.\n"
        "5. Search by postcode with NAVIGATION -> POSTCODE.\n"
        "Covers Great Britain only; data source August 2023.\n"
        "Do not import the ZIP directly; IMPORT PCs accepts CSV or CSV.GZ.\n"
        "Attribution: GetTheData, ONS, Ordnance Survey, Crown copyright and database right.\n",
        encoding="utf-8",
    )
    print(json.dumps(details, indent=2))


if __name__ == "__main__":
    build(Path(sys.argv[1] if len(sys.argv) > 1 else "out/postcodes"))
