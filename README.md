# Eaton Drive
Native Android 11-compatible landscape launcher and offline map-viewer prototype for a Suzuki Celerio aftermarket 9-inch (1024 × 600) head unit with advertised 1 GB RAM and 16 GB storage.

**Status: v0.1 source only; not yet tested on the specific head unit. This is not a complete navigation product.**

## Included
- Eaton colour scheme (#07111F midnight navy, #0D1A2B panels, #2ED3B7 teal) with four oversized launch tiles.
- GPS-provided speed in **MPH**, independent of map use. Displays a dash when a fix is missing or stale (older than 6 seconds).
- Optional Android HOME launcher; no firmware modification, rooting, or disabling the OEM radio/reversing camera.
- Assignable Radio/Music/Phone shortcuts to launchable installed head-unit apps; long-press a tile to reassign.
- **Embedded offline Mapsforge map viewer**: import local .map data using Android's file picker, pan/zoom, centre on GPS, and read MPH.
- No internet permission, data collection, or cloud backend. Location is foreground-only.
- Android debug APK build in GitHub Actions, plus speed tests.

## Not yet implemented
Offline postcode/address search, turn-by-turn navigation, road routing, rerouting, spoken directions and GPS road-snapping. A map VIEWER is not a functioning sat-nav. Do not rely on this prototype for directions.

## Build
Requires Android SDK 35, Java 17 and Gradle 8.7:
```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
Alternatively use **Actions → Eaton Drive Android debug APK**. A successful run publishes an artifact named `Eaton-Drive-debug-APK`; unzip it to get `app-debug.apk`. Builds are unverified until the workflow reports success.

## Offline maps
The app does not bundle map data. Get a compatible **Mapsforge .map**, preferably a small area for testing, and choose **Navigation → Import map**. Do not select .osm.pbf, .mbtiles or an unrelated ZIP.
Map download index: https://download.mapsforge.org/maps/v5/europe/united-kingdom/

The app copies the file into its private external storage. A 1 GB map can require over 1 GB of free space; leave room for the operating system and temporary import file. Map data and overlays are credited to © OpenStreetMap contributors, with provider-specific licences to be reviewed.

## Install and rollback
1. Prefer testing on an Android 11 phone/tablet first.
2. Install the APK on the head unit from USB, if the OEM allows APK installation.
3. Launch Eaton Drive **as a normal app**, not as the HOME launcher. Grant precise-location permission and enable GPS.
4. While safely parked, verify GPS, map, steering controls, radio, Bluetooth and reverse-camera behaviour before making Eaton Drive the default HOME app.
5. If supported, change the home app via Eaton Drive → Android Settings → Change default home app.
6. Roll back via Android Settings → Apps → Default apps → Home app → the original OEM launcher. Do not flash or modify firmware.

Hardware processor/MCU, install permissions, GPS availability, OEM application package names and performance on 1 GB RAM remain unknown.

## Dependencies
Mapsforge 0.25.0 (LGPL licence) from Maven Central. Map data licences vary. No map download or map file is bundled.
