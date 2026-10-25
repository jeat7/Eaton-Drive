# Eaton Drive v0.7.1 — route planning, live following and optional offline address search

Native Android 11-compatible landscape launcher for the Rimoodly 9-inch 1024×600 Suzuki Celerio head unit (advertised 1GB RAM, 16GB flash).

## Implemented
- Eaton navy/teal launcher; improved nav-pin road/arrow scalable launcher icon.
- GPS MPH speedometer on Home and Maps even without data connection; lost fix displays an em dash.
- Offline Mapsforge .map renderer with multiple maps loaded together. Import England, Scotland and Wales separately; they remain installed together.
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


## Prepared offline data packages (October 2026)

- [Great Britain postcodes](https://github.com/jeat7/Eaton-Drive/actions/runs/40099254057): 1,764,718 GB records based on September 2026 release; 16.1 MB CSV.GZ. Northern Ireland excluded.
- [Great Britain BRouter data](https://github.com/jeat7/Eaton-Drive/actions/runs/40099368053): 9 RD5 files, ~295 MB.
- [England, Scotland and Wales Mapsforge packs](https://github.com/jeat7/Eaton-Drive/actions/workflows/offline-map-files.yml): separate downloadable map artifacts.
- [BRouter signed APK](https://f-droid.org/packages/btools.routingapp/): install official application separately; configure motorcar_fast.

Copy files onto USB; in navigation choose IMPORT MAP for each .map and IMPORT PCs for the postcode CSV.GZ (keep compressed). BRouter RD5 tiles must be installed in BRouter's own configured segment directory. An offline English TTS voice must also be installed through Android speech settings. See OFFLINE-DATA.md.

WARNING: v0.4 is an untested driving-navigation prototype. Test while parked before setting as HOME or relying on directions.


## v0.5 performance improvements
- No postcode CSV import required. The APK contains a pre-indexed database of ~1.76 million Great Britain postcodes, prepared in CI and copied to app storage only on first use.
- MAP DATA remains stored after closing Navigation. The map opens at the last saved viewport and automatically centres on a good first GPS fix, unless the user manually panned the map.
- Mapsforge uses efficient tile-based reading for zoom and viewport; a GB map is NOT held entirely in RAM.
- The DATA button reports postcode status; this replaces the old IMPORT PCs operation which was slow and could fail during large imports.
- The built-in database increases APK size. The CI workflow generates the asset from https://postcodesuk.com/data-downloads/uk-postcodes (September 2026) and caches it.
- Road/street names and house numbers still need an independent searchable index sourced from permitted OpenStreetMap extracts. Visible map labels do not provide text search.
- Updating the APK should keep maps in app-specific storage. Uninstalling or clearing app data can remove all offline maps.
- Not yet tested with the car hardware or during driving; do not rely on this prototype for live directions.


## Verified sample postcode
Use **B1 1BB** (Birmingham) or **SW1A 1AA** (London) to verify the embedded lookup. Earlier setup messages suggested B1 1AA; it is not present in the September 2026 data, so please do not use it as the installation test.

## v0.6: built-in BRouter engine
- The MIT-licensed BRouter Java core v1.7.10 is compiled into Eaton Drive. No separate BRouter Android installation or service-mode configuration is required.
- Upstream sources pinned to git commit 4d2639af77ea5ed9c30d3e400764eb6f9e8522da, compiled in GitHub Actions, with upstream MIT notice included as an APK asset.
- The car-vario.brf driving profile is included in the APK.
- For offline road data, in Eaton Drive go to Navigation > ROADS > Choose routing files, and select the downloaded Eaton-Drive-GB-BRouter-Routing.zip or individually selected .rd5 tiles. Routing tiles persist in app storage.
- Regional Mapsforge .map files and built-in GB postcodes continue working without change.
- No Internet permission, no firmware flashing, no account, no vendor BRouter installation.
- You may need to back up your maps and other app data before uninstalling a debug build; debug APK signing keys may differ between builds.
- Warning: route correctness on an actual vehicle is not yet validated. Use only for stationary testing until verified.


## v0.6.1 routing startup fix
The BRouter offline engine requires a matching `lookups.dat` beside its car driving profile. v0.6 omitted that configuration and could report `FileNotFoundException: .../routing-profiles/lookups.dat` even when road tiles were installed. The pinned BRouter 1.7.10 file is now bundled automatically at APK build time and installed into Eaton Drive's private routing profile directory before the route engine starts. Reimporting `.rd5` files is **not** required to correct the missing lookup table.

After updating from v0.6 to v0.6.1, open Navigation, select a postcode and press START ROUTE. If you see a new error, capture its exact wording. Route calculation, road snapping, voice directions and turn validation still need phone and in-car tests before driving use.


## v0.7.1 sat-nav improvements
- Route preferences in Navigation > OPTIONS: avoid motorways and toll roads, saved between sessions; BRouter car-vario profile receives both switches.
- PLAN shows a route preview with miles and calculated journey time. OTHER ROUTE calculates BRouter's second-choice routing path **on demand**, then lets the driver compare the two by miles and travel time. Some journeys have no second distinct route.
- GO begins foreground-only guidance and keeps the map centred on sufficiently accurate GPS; pan pauses auto-follow until pressing GPS. The light-blue top-down vehicle drawing uses GPS heading and location. Never show an inaccurate/stale position as if valid.
- PIN enables a visible destination marker while moving the map; SET PIN confirms the spot under the marker tip.
- ROUTE ETA uses BRouter's travel-time calculation when provided; otherwise a clearly approximate fallback. Live remaining figures are distance-proportional estimates, not traffic aware.
- ADDRESS searches an optional FTS4 SQLite index created from a regional OpenStreetMap extract. The available first region is West Midlands (covers Stoke, Dudley, Birmingham etc). It is not included in APK or Mapsforge map data. Build it with Actions > Prepare Eaton Drive offline street and address search, download artifact, extract ZIP, then choose SETUP > Import street/address search index and select the .db file. Enter road name, road + house number, or mapped place; select a result to set destination.
- Address search only finds OSM-mapped names and house numbers. Road-centre results can be approximate; check the pin before going. © OpenStreetMap contributors, ODbL. Source https://download.geofabrik.de/europe/united-kingdom/england/west-midlands.html.
- Still experimental. Maps, routing, voice and marker behaviour are not validated on the actual head unit or in a moving car. Keep testing while parked.
