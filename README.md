# Eaton Drive v0.11.0 — Signature polish, saved Home and favourites

A native Java Android launcher and experimental offline sat-nav for the 2015–2018 Suzuki Celerio's third-party Rimoodly head unit, advertised as Android 11, 1024×600, 1GB RAM, 16GB storage.

**Status:** Experimental. CI compilation and unit tests have passed for prior builds; route accuracy, voice guidance and OEM integration are **not validated in the vehicle**. Do not use as the sole source of driving directions.

## What it does

- Navy/teal home launcher with GPS speedometer (MPH), clock and user-configured shortcuts for OEM radio, music and phone.
- Native offline Mapsforge map viewer; England, Scotland and Wales can remain imported between launches.
- Embedded MIT BRouter Java routing engine, including numeric avoid-motorways/toll preferences; one-time import of local .rd5 road files. No separate BRouter Android installation.
- Built-in indexed offline GB postcode database (~1.76 million records), automatically installed from the APK.
- Offline West Midlands road names, mapped house numbers and place names through optional address database; updated v2 search results can show **Crayford Road, Kingstanding, B44**. Locality is based on nearby mapped places and postcode district is approximate, not an exact property postcode.
- Route preview with mileage and approximate travel time; an alternative route on request; GO for basic foreground GPS following, supported voice turns and rudimentary rerouting. All still require end-to-end testing.
- Light-blue top-down car marker with user-selected **OPTIONS → CAR SIZE** (six sizes, 40–120dp) saved across restarts. Marker hides when GPS is unreliable or the user pans the map.

## Installing datasets for offline navigation

1. Install a recent APK as an ordinary Android app; do not set it as default HOME until the vehicle's camera/radio/phone controls have been tested.
2. Navigation → SETUP → Import map regions. Select a compatible Mapsforge `.map` file separately for each region you need.
3. Navigation → SETUP → Import driving roads. Select the prepared BRouter routing ZIP or individual `.rd5` segments once. These files are different from Mapsforge `.map`.
4. Postcode search is built into the APK; no raw postcode CSV import is needed.
5. Navigation → SETUP → Import street/address search index. Download the **new West Midlands v2** database artifact from [the address workflow](https://github.com/jeat7/Eaton-Drive/actions/workflows/offline-addresses.yml), extract `west-midlands-addresses.db`, and select it once. Earlier v1 files remain readable but show fewer details.
6. For spoken turns, an **offline English TTS voice** must be installed and configured in Android settings.

Maps and routing files need ample free space on the 16GB unit; no file is downloaded from within Eaton Drive. No internet permission or online search is used. Regional search has incomplete OSM house-number coverage.

## Build and source

- [Android APK CI](https://github.com/jeat7/Eaton-Drive/actions/workflows/android-debug.yml) (Java 17, Android SDK 35, Gradle 8.7).
- [West Midlands address pack CI](https://github.com/jeat7/Eaton-Drive/actions/workflows/offline-addresses.yml).
- Local build: `gradle :app:testDebugUnitTest :app:assembleDebug`, after preparing the pinned vendor BRouter sources and postcode asset as in the CI workflow.
- See [OFFLINE-DATA.md](OFFLINE-DATA.md) and [PROJECT_REVIEW.md](PROJECT_REVIEW.md) for preparation and the prioritised project audit.

## Limitations and credits

Navigation is foreground-only and stops GPS when MapActivity loses focus; it does not yet reliably provide directions while listening to OEM FM/phone apps. The Suzuki car artwork is original, not an official Suzuki logo. Offline ETA is not traffic aware. Debug signing keys may change between GitHub builds, making upgrades fail; **back up map and routing downloads before uninstalling**.

© OpenStreetMap contributors (ODbL), Geofabrik regional data; Postcodes UK, ONS, OS and Royal Mail sources with applicable public sector notices. Embedded BRouter core used under its MIT licence (licence bundled in the APK). Device MCU, GPS antenna and radio/reversing compatibility must be verified separately; do not flash unknown head-unit firmware.


## v0.9 — destination-first navigation and continued spoken guidance

- **WHERE TO?** searches full GB postcodes, offline street/town/house addresses in one large entry. PIN remains for manual location picking. PLAN → optionally ROUTES → GO; STOP finishes navigation.
- Prominent next-turn banner shows true BRouter manoeuvres, signal loss or missing instructions; a separate live status panel shows estimated miles and minutes remaining.
- Android **location foreground service** owns GPS, off-route recalculation and speech after GO. Persistent notification has current guidance, opens Eaton Drive when tapped and has a STOP action.
- The service is started only by GO in a visible activity after precise-location permission. No automatic restart after an ignition or process restart. It is intended to continue spoken navigation when OEM radio/music/phone apps are opened, subject to their firmware and audio behaviour.
- Reopening the map reconnects to the running journey without recalculating the initial route. A validated app-private route file hands the chosen route to the service; route calculations and TTS are lazily allocated.
- VOICE / MUTE setting is shared with the service and persisted.
- Automated unit tests cover next-turn banner labels and remaining-time calculations.
- **Not validated in the car:** Android ROM may restrict services/audio, and spoken hints, actual navigation reliability, reverse camera and OEM radio must be checked before driving. Continue testing stationary/passenger-only.


## v0.9.1 address search correction
- A query like `218 Kingstanding Road` is now treated as **house number 218 on Kingstanding Road**, not a generic full-text match across house, locality, postcode district and other unrelated columns.
- The local FTS4 query limits street words to the `name` field and an optional house number to the `house` field. The Android-compatible SQL fallback makes the same distinction, even if the phone's FTS implementation behaves differently.
- Generic street searches such as `Kingstanding Road` show a street/road location before hundreds of individual house numbers.
- Removed the old generic "Crayford Road" error from unrelated searches. If an exact house isn't in the installed region, the destination search offers a **SEARCH STREET** fallback.
- Tested the exact indexed SQL against **both** released West Midlands address databases (v1 and v2): 218 Kingstanding Road, Kingstanding Road and Crayford Road all resolve. Java tests assert the new column-specific query expressions.
- **No map, postcode, routing tile or address index reimport required** when an APK can be installed over the existing package. Back up offline files before an uninstall as debug APK signatures may change.


## v0.9.3 — missing house numbers
- Direct searches such as **218 Kingstanding Road** resolve exactly when OpenStreetMap contains a house entry.
- **40 Crayford Road** is not in either West Midlands OSM-derived address index (there are zero recorded house numbers for Crayford Road), although the street itself is present. The correct public postcode can be entered separately if known; the database cannot infer a verified building entrance.
- When exact address search returns no matching house, the destination-first search now offers **FIND STREET** or **USE POSTCODE**. For the latter, an explicit notice says that the postcode centre is approximate and not the verified entrance. It never presents a road centre or interpolated house as a confirmed building location.
- Existing map, postcode, address and route packs remain compatible; no data-file changes are required after an in-place update. Prior debug builds may still need uninstall due to temporary signing keys.
- Suggested next milestones: better offline address coverage and saved favourites/Home, followed by continued real-device GPS, spoken turn and foreground service validation.


## v0.10.0 — Eaton Drive Signature launcher
- Matches the user-approved **EATON DRIVE** wordmark: spaced white EATON and luminous teal DRIVE. **No redundant E logo** next to the name. System apps-grid control remains on the opposite side of the top bar.
- Main dashboard now follows the 1024×600 premium automotive design: GPS instrument/speed panel on the left; a large teal outlined NAVIGATION tile on the right; and a three-column RADIO / MUSIC / PHONE row.
- A small **transparent light-blue Suzuki Celerio side-profile WebP** (original design generated for Eaton Drive) is shown inside the speed panel. It is a static, approximately 5.6KB image asset, not a video, animated sprite or expensive blur overlay. The car artwork is a visual illustration, not an official Suzuki logo.
- Compact, antialiased Canvas-drawn icons (navigation arrow, radio, music, phone, apps grid) are bundled as native code. No custom font file, network requests, WebView, live animations or extra heavyweight dependencies are needed.
- Accurate GPS continues to show live MPH. In the absence of a trustworthy fix, the speed display is a dash instead of a false 0 MPH and the GPS status indicates searching.
- Toolbar apps-grid opens other installed Android apps; SETTINGS provides app assignment and Android settings. Long-pressing RADIO/MUSIC/PHONE still allows selecting the appropriate OEM app for each tile.
- Native navigation screen, BRouter engine, postcodes, address index, road tiles and all offline files are unchanged.
- **Known limitation:** visual layout is CI-tested but still needs installation and visual inspection on the exact head unit. OEM app launch behaviour and navigation features are unvalidated in a moving vehicle. Do not use for sole driving guidance.


## v0.11.0: unified UI and saved destinations
- **Launcher layout polish:** one adaptive native Android layout, compact typography/icons for low-dp landscape phones; optimised for 1024×600 vehicle displays. GPS speed digits use a fixed vertical slot, the MPH legend no longer jumps, and the light-blue Suzuki Celerio expands into available height with correct aspect ratio.
- **Signature navigation styling:** navy gradient toolbar/footer, teal-accented EATON DRIVE heading, refined rounded buttons and a prominent destination search field. Route preview, GPS speed, pin/dropper, BRouter avoidance, alternative route and foreground voice remain unchanged.
- **Compact HOME button:** sets a previously saved home as the destination and centres the map; if none is saved, offers to save the currently selected point, or choose a destination first. HOME does not consume map viewport space.
- **SAVED button:** opens Home and a list of up to 24 named favourites; tapping an entry selects it as the next destination. SAVE CURRENT gives a selected map pin, postcode or street a descriptive label. MANAGE supports rename, delete and setting a favourite as Home. Clear Home has a confirmation dialogue.
- Places are saved **fully offline** in a small dedicated Android SharedPreferences file using lossless double-coordinate encoding. Name lengths and coordinate ranges are validated, and changes are committed as a single transaction. No network, account, database download or new Android permission.
- Shortcuts remain across app restarts and normal in-place updates. **Uninstalling temporary debug APKs may erase saved Home/favourites and maps**; preserve the original offline data packs and record important favourite addresses before uninstalling. Permanent signing remains optional and unconfigured.
- Safety: selecting Home/favourites does not automatically start navigation; driver must use PLAN/GO. House numbers and postcode-centre approximations remain approximate when their underlying offline data does not identify a specific entrance. Continue stationary/passenger testing until verified.

### Phone and head-unit test checklist
1. On a test device, choose a postcode or PIN, use HOME > SAVE HOME, exit and reopen; HOME should recall the saved point.
2. Choose another location, open SAVED > SAVE CURRENT, name it. Close/reopen and select it from SAVED. Use MANAGE to rename, make it Home, and delete the favourite.
3. PLAN an offline journey with previously imported .map and .rd5 files; confirm the saved point is shown before starting GO.
4. Compare the launcher at landscape phone, tablet and 1024×600: no clipped MPH digits, no oversized car, tappable NAVIGATION/RADIO/MUSIC/PHONE and SETTINGS.
5. Check that background voice, GPS position and directions still behave as in v0.10.0.
