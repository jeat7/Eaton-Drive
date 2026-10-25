# Eaton Drive v0.9 — destination-first navigation and foreground spoken guidance

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
