# Eaton Drive: offline data preparation

Three **different** datasets are needed and do not replace each other:

1. **Map viewer:** Mapsforge `.map` (England 1.1GB; Scotland 269MB; Wales 105MB) from https://download.mapsforge.org/maps/v5/europe/united-kingdom/
2. **Car route calculation:** BRouter `.rd5` (tiles are 5×5 degrees) from https://brouter.de/brouter/segments4/. BRouter itself must be installed; use its Android Download Manager or see the offline downloader workflow.
3. **Postcode search:** `eaton-drive-gb-postcodes.csv.gz` generated using Open Postcode Geo. Get it from **Actions → Prepare Eaton Drive offline postcode database → successful run → Artifacts**. Copy the **CSV.GZ** (not the downloaded artifact ZIP) to a USB stick, then use **IMPORT PCs** inside Eaton Drive.

Maps and routing files can be much larger than the APK; do not commit them to GitHub. Check free space on the actual 16GB head unit. When BRouter starts for the first time on Android 11, select its permitted base directory; its routing tiles are stored in the `brouter/segments4` subdirectory. It may be simpler to give the head unit a temporary phone hotspot **once**, let BRouter download its own data, and then disconnect Wi-Fi forever. The Android BRouter app package is available from https://f-droid.org/packages/btools.routingapp/ or its official release page.

The postcode source is based on the **August 2023** ONS release, so some modern postcodes will be missing. It excludes Northern Ireland. A postcode centre is an approximate destination, not necessarily a drivable entrance. Data from GetTheData (https://www.getthedata.com/open-postcode-geo), ONS and Ordnance Survey; Crown copyright and database right; Open Government Licence v3.0 and OS OpenData terms. © OpenStreetMap contributors for maps/routing.

For repeatable downloading on a computer, see `scripts/fetch_offline_maps.py` and the **Prepare Eaton Drive UK map/routing packs** workflow. The stock v0.3 app can display just one .map at once; combined GB maps require the planned multi-map update.
