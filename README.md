# Eaton Drive v0.3 — offline navigation prototype

Native Android 11-compatible landscape launcher for the Rimoodly 9-inch 1024×600 Suzuki Celerio head unit (advertised 1GB RAM, 16GB flash).

## Implemented
- Eaton navy/teal launcher; improved nav-pin road/arrow scalable launcher icon.
- GPS MPH speedometer on Home and Maps even without data connection; lost fix displays an em dash.
- Offline Mapsforge .map renderer with local import.
- BRouter offline motorcar route calculation: requires separately-installed BRouter, car profile and appropriate offline .rd5 files.
- **Postcode search**: on-device SQLite database; CSV/CSV.GZ **with postcode,latitude,longitude columns** imported once. No internet queries. Recommended public source: Open Postcode Geo (GB; NI not included). Lookup accepts spaces/case variations.
- **Spoken turn guidance** from BRouter OsmAnd-format GPX turn tags (when present), using an installed OFFLINE English Android voice; reports missing voices or missing turn data, never fabricates turns.
- **Automatic offline rerouting** while navigation screen remains in the foreground: after three sufficiently accurate GPS fixes more than ~85 metres away from nearby route points, with a 40-second cooldown. Route is recalculated by local BRouter.
- No Android internet permission or telemetry; preserves OEM radio, camera and MCU. Remains opt-in HOME launcher.
- Local Java unit tests for postcode parsing, turn parsing, guidance/reroute and speed conversion.

## IMPORTANT: status and safety
Build validation is not in-car validation. This is a **development prototype**, not production-quality car navigation. No live vehicle testing, route-hint compatibility testing against installed BRouter, road snapping, navigational error study, or real-world GPS reception validation yet. Never rely solely on it for navigation. No steering wheel controls, reverse camera, or OEM launch integration verified.

Turns are only spoken if BRouter GPX supplies supported turn tags (`<rtept><extensions><turn>TL</turn>...`). Missing hints mean no reliable spoken instructions. Route guidance is active only when the navigation screen remains open; it intentionally stops when leaving the app. It uses coarse thresholds, can miss turns at complex junctions, and cannot account for road closures or traffic.

## Build
GitHub Actions → Eaton Drive Android debug APK, or Java 17 + Android SDK 35 + Gradle 8.7:
```
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## Initial offline setup (on Wi-Fi during preparation only)
1. Install v0.3 debug APK (prefer Android 11 test device before head-unit installation). Grant precise location and enable GPS.
2. Download a compatible Mapsforge .map for your chosen region from https://download.mapsforge.org/maps/v5/europe/united-kingdom/ and choose **IMPORT MAP**. Large maps require substantial spare flash.
3. Install official BRouter from F-Droid or BRouter's official distribution; open BRouter, download the **.rd5** routing segments for the same region and configure **motorcar_fast** service mapping with an available car profile. .rd5 files are separate from .map files.
4. Download the free, attributable **Open Postcode Geo** CSV from https://www.getthedata.com/open-postcode-geo on a computer. It contains postcode, latitude and longitude. Copy to USB and choose **IMPORT PCs** (CSV or gzipped CSV). Import is performed in small memory batches but can be slow; SQLite storage is additional to the maps. **Great Britain only** with this data; Northern Ireland requires a separately licensed source. Other pre-geocoded CSVs using postcode,latitude,longitude headers are accepted if you have usage rights.
5. With GPS fix, enter a postcode via **POSTCODE**, then press **START ROUTE**. Or pan map to a location and choose **MAP DEST**. Keep navigation screen visible for voice and off-route detection.
6. Android must have an **offline British/English text-to-speech voice** installed to speak turns. The VOICE button toggles speech and reveals readiness status.

Postcode coordinates are centroids, not exact building entrances. If routing fails, check offline BRouter profile/segments first. No map data, routing segments or postcode datasets are bundled in the APK. Credits: © OpenStreetMap contributors, © Crown copyright and database right (Ordnance Survey), Open Government Licence v3.0, with source-specific attribution as applicable.

## Rollback
Test as a normal app; do not set as default launcher until radio, Bluetooth, reversing camera and GPS are tested while parked. To revert, open Eaton Drive → Android Settings → Change default home app and select the OEM launcher. Never flash firmware.

## Technical constraints
Android 11 advertised, 1GB RAM, 16GB storage, CPU and MCU unknown. Import database is disk-backed rather than loaded into memory. Avoid importing huge datasets if storage is low. Navigation response parser limits GPX size to 12MB/75,000 points. Routing and TTS remain experimental until real-device testing.
