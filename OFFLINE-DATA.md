# Eaton Drive: offline data preparation

Three **different** datasets are needed and do not replace each other:

1. **Map viewer:** Mapsforge `.map` (England ~1.21GB; Scotland ~282MB; Wales ~110MB) from https://download.mapsforge.org/maps/v5/europe/united-kingdom/
2. **Car route calculation:** BRouter `.rd5` (tiles are 5×5 degrees) from https://brouter.de/brouter/segments4/. BRouter itself must be installed; use its Android Download Manager or see the offline downloader workflow.
3. **Postcode search:** `eaton-drive-gb-postcodes.csv.gz` prepared from Postcodes UK September 2026 live postcode release, excluding Northern Ireland BT entries. Get it from **Actions → Prepare Eaton Drive offline postcode database → successful run → Artifacts**. Copy the **CSV.GZ** (not the downloaded artifact ZIP) to a USB stick, then use **IMPORT PCs** inside Eaton Drive.

Maps and routing files can be much larger than the APK; do not commit them to GitHub. Check free space on the actual 16GB head unit. When BRouter starts for the first time on Android 11, select its permitted base directory; its routing tiles are stored in the `brouter/segments4` subdirectory. It may be simpler to give the head unit a temporary phone hotspot **once**, let BRouter download its own data, and then disconnect Wi-Fi forever. The Android BRouter app package is available from https://f-droid.org/packages/btools.routingapp/ or its official release page.

Postcode data is the September 2026 edition from Postcodes UK (https://postcodesuk.com/data-downloads/uk-postcodes); it covers 1,764,718 Great Britain postcode points after excluding Northern Ireland and invalid entries. A postcode centre is approximate, not a building entrance. Attribution: Postcodes UK; contains OS data © Crown copyright and database right 2026; contains Royal Mail data © Royal Mail copyright and database right 2026; source ONS under Open Government Licence v3.0. Map and routing © OpenStreetMap contributors, ODbL.

For repeatable downloading on a computer, see `scripts/fetch_offline_maps.py` and the **Prepare Eaton Drive UK map/routing packs** workflow. Version 0.4 and later support England, Scotland and Wales at the same time. Import each .map individually in NAVIGATION, once per region.


## UK packages built with GitHub Actions

- Postcode data (16.1MB, September 2026, GB): https://github.com/jeat7/Eaton-Drive/actions/runs/40099254057
- BRouter RD5 routing (295.4 MB, GB): https://github.com/jeat7/Eaton-Drive/actions/runs/40099368053
- England/Scotland/Wales maps: https://github.com/jeat7/Eaton-Drive/actions/workflows/offline-map-files.yml
- BRouter APK (publisher/F-Droid signed): https://f-droid.org/packages/btools.routingapp/

Download Actions artifacts onto a computer and EXTRACT the artifacts (outer ZIP). Keep the .CSV.GZ compressed; its content is imported via IMPORT PCs. Copy the .map files to USB and IMPORT MAP separately for each region. For RD5 tiles, use BRouter's Download Manager if possible; direct file copies belong in BRouter's brouter/segments4 directory after first launching it. Select an offline UK English text-to-speech voice in Android settings. Once installed, disconnect from Wi-Fi and test only while parked. Artifacts can expire after 7 or 14 days; workflows let you regenerate them.
