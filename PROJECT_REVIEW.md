# Eaton Drive technical review — 25 October 2026

Project: an offline-first custom Android car launcher and sat-nav for a 2015–18 Suzuki Celerio with Rimoodly 9-inch, 1024×600 Android head unit advertised as Android 11, 1GB RAM and 16GB flash.

**Scope:** code-level audit and current Android CI/test results, not in-vehicle driving safety verification. The exact MCU, CPU, free flash and OEM camera/radio app interfaces are not yet known.

## What's genuinely working in the source/build

- Small native Java Android launcher with navy/teal styling, clock and offline GPS speed.
- Mapsforge tiled local .map renderer, multi-region map import and persisted viewport.
- Indexed 1.76m GB postcode database bundled with APK; SQLite exact lookup without data connection.
- Embedded MIT-licensed BRouter engine, local road-tile importer, driving route previews and optional second choice.
- BRouter route avoidance inputs, offline voice integration, journey estimate, basic foreground rerouting and GPS tracking.
- West Midlands offline street/house/place SQLite FTS4 index. **New v2 data** adds nearest OSM locality and approximate postcode district: e.g. Crayford Road, Kingstanding, B44. The app supports both old v1 and new v2 indexes.
- Size-adjustable light-blue car marker, with a safeguard to hide it when the map has been manually panned.

**Limitations:** A passing GitHub build and local/unit tests do not prove route guidance is accurate, lane-level, safe or usable while driving.

## P0 — fix before any reliance on navigation

1. **One permanent application signing identity.** GitHub's debug APK builds may be signed with different debug keys. Updates can fail and uninstalling erases imported .map/.rd5/address data. Configure a securely stored release keystore as GitHub Actions secrets and retain/backup the key. Do not commit signing secrets to the repo.
2. **End-to-end real-world driving tests on actual head unit, initially as passenger.** Check current position, snapping, turning, roundabouts, road closures, rerouting, wrong-way starts, ETA, voice and stationary arrival. Include both good and weak satellite reception. Keep a trusted navigation alternative during testing.
3. **Retain guidance when OEM radio/music/phone app opens.** Navigation currently lives inside MapActivity and GPS stops on onPause. Route progress/voice therefore do not reliably continue after switching apps; use a properly designed Android foreground service with user-visible persistent navigation controls, audio focus, and a clear stop action.
4. **Detect weak and lost GPS conservatively.** Never invent speed or keep a car marker at the centre after panning. Add last-fix age, satellite/accuracy readout, degraded-signal notification, and direction smoothing; validate car marker transform when viewport offsets/rotation vary.
5. **Head unit rollback and OEM system checks.** Test reverse camera override, FM/RDS, Bluetooth phone, steering wheel controls, ignition sleep/wake, power loss, volume, night brightness and default HOME selection *before* changing launcher defaults. Never flash generic firmware.

## P1 — highest-value functional upgrades

6. **Reduce complexity on a 1024×600 screen.** Eight small action buttons currently compete with map area. Replace with one large destination search box and a small number of prominent actions (GO, stop, mute, GPS); move import/setup under an advanced menu. Target comfortably tappable controls.
7. **A clearer navigation instruction panel.** Show next turn, distance to turn, remaining miles/time, real arrival clock-time estimate and muted/voice status. Route preview and live guidance should be clearly distinguishable.
8. **Better route estimate and rerouting.** Current live ETA scales linearly with remaining route distance, without segment speed/time or live traffic. Add per-segment time and rerouting state; verify BRouter's `car-vario` profiles and alternative choices, and don't claim a unique fastest/shortest route without comparing candidates. Avoid tolled/motorway route preferences should be tested against example UK journeys.
9. **Upgrade offline address results.** New v2 indices offer mapped neighbourhood and approximate outward postcode district; never represent a district as an exact property postcode. Place names inferred from closest OSM place can occasionally cross locality boundaries. House-number coverage is limited by mapped OSM data and the current index only covers West Midlands. Provide nearby result ordering and clear area labels.
10. **Fast offline first-run installation.** Include simple storage meter, dataset checksums, completeness checks, resumable imports, and download/USB update instructions. For 16GB advertised storage, a full GB map+road+search pack is a significant fraction of usable flash. Prefer the regional datasets actually needed.
11. **Save useful journeys.** Home, frequent destinations, last route, favourites and recently searched places should persist safely through normal app restarts.
12. **More resilient app lifecycle.** Restore a route after an Android process restart or ignition power cycle without automatically continuing navigation until the user approves; avoid recalculating a route on every reopen.

## P2 — polish after reliability

13. **GPS car personalisation:** marker size options added (40–120dp), later choose hatchback colours or arrow mode; do not make vehicle marker seem precisely road snapped without map matching.
14. **Accessibility:** high contrast by day/night, spoken controls, large-font mode, one-tap return home, colour-blind-safe road and traffic line styles.
15. **Expand local indexes:** prepare named UK regional address packs with versioning and a map showing installed coverage; use OSM + licensed data appropriately, don't assume all house numbers exist in OSM.
16. **Supply-chain and maintenance:** update dependencies after compatibility testing; cache or verify third-party data by checksum, keep reproducible build provenance, run unit tests in CI, add integration tests for SQLite v1/v2, routing profiles, and importing ZIPs on Android emulators. Archive/source licences and data attributions.
17. **Device profiles:** adapt to unknown ARM chipset, 1GB available RAM, Android version misreporting, and proprietary OEM intents. Add low-memory profiling under thermal/load and without Wi-Fi.
18. **No network by default:** preserve no-Internet-permission design for the sat-nav. If future optional data updates use Wi-Fi, require an explicit opt-in and don't silently send locations or searches.

## Release quality gates

- CI must build one signed APK with deterministic identity, successful tests and source/data licence notices.
- App must load installed maps/road/address packs and save route/marker settings without reimport.
- Verify sample GB destinations (Crayford Road, Kingstanding, B44; postcode lookups) fully offline.
- Verify motorway/toll settings, alternative routes, voice prompts, approach/arrival, rerouting and GPS-loss conditions on an Android test device, then test on the actual stereo under controlled conditions.
- Confirm radio, Bluetooth, reversing camera and Home rollback; only then consider selecting Eaton Drive as the default launcher.

This is a prioritised engineering plan, **not** an assurance of in-car navigation safety.
