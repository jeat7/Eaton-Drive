# Suzuki Celerio — Eaton Drive installation and parked testing

Eaton Drive v0.12.0 is an **experimental offline Android home launcher**, not replacement head-unit firmware. The third-party Rimoodly screen is advertised as Android 11, 1024×600, 1GB RAM / 16GB storage. MCU, OEM radio components and installed GPS antenna remain unverified.

## Bring with you

- The latest `Eaton-Drive-v0.12.0-debug.apk` and a backup of each previously downloaded Mapsforge `.map` region, BRouter `.rd5` road pack ZIP, and West Midlands `.db` address index
- A USB stick as backup if the stereo's built-in browser/file manager cannot find the APK
- Phone hotspot **only if needed for permissions or additional downloads** — driving navigation is fully offline
- A second trusted sat-nav for verifying test routes; don't depend on unvalidated turn instructions

## Step-by-step parked checklist

1. With ignition on and vehicle **parked**, open stereo Settings > **About device**. Photograph Android version, build ID, processor/SoC, RAM/storage and **MCU version** if present. The new Eaton Drive Settings > **Head unit diagnostic details** also shows standard Android model, display, memory and default Home status; it **cannot read vendor MCU details**.
2. Install latest Eaton Drive APK via stereo file manager; allow installation from that file manager only if requested. No root access, ROM flashing, or system-app removal is needed.
3. Open Eaton Drive as an ordinary app. Check that radio, reversing camera, Bluetooth calling, steering-wheel controls, speakers and Android Settings still function **before** choosing default Home.
4. In Eaton Drive's Settings choose **Default home launcher**. If the system allows it, select Eaton Drive as Home / Always. Press physical Home. Then perform short sleep/wake, full startup and ignition restart tests. Some vendor head units override Android Home; don't force a hidden bootloader menu or disable the OEM launcher.
5. Long-press RADIO, MUSIC, PHONE tiles to assign their existing OEM apps. Verify each launches. If no suitable standard launcher icon exists, it may be a proprietary OEM activity; do not disable it.
6. Test the **optional** floating Home: Settings > **Floating Home for Radio/Music** > Continue > Android Draw over other apps permission. It is OFF by default. Open RADIO or MUSIC from Eaton Drive, then tap the small draggable Home icon to return. The separate service runs only after explicit launch from Eaton Drive; its notification provides HIDE, and a long press on the button also hides it. The feature does not automatically start for PHONE and does not run at boot. If the manufacturer forbids overlays, use the physical/Android Home button.
7. **IMPORTANT SAFETY CHECK WHILE PARKED:** with floating Home enabled, test reversing camera and Bluetooth phone interfaces. The overlay MAY remain above vendor camera or phone software, including if those programs become foreground. If it covers any safety-critical control or warning, disable **Floating Home** in Eaton Drive Settings and use the hardware Home key. Eaton Drive cannot automatically detect proprietary reversing-camera screens.
8. Import offline map and `.rd5` routing packs once, if lost when reinstalling; postcode DB is built in. Import `west-midlands-addresses-v2.db` once for offline roads and mapped house numbers. Ensure free storage remains.
9. Verify GPS, postcode search, Home/favourites, route PLAN/GO, turn banner and offline spoken directions while parked. Have a passenger assess driving tests with a trusted alternative navigator; verify mileage, route choice, camera/audio behaviour, ignition wake and Bluetooth integration over several journeys before relying on it.
10. In the event of trouble, use Android Home/Default Apps to select the original manufacturer launcher, then turn off floating Home. **Do not flash firmware or uninstall essential OEM radio/camera/system applications**.

## Quick reference

- Default Home: **Eaton Drive → SETTINGS → Default home launcher**
- Shortcut app choice: **long-press RADIO / MUSIC / PHONE**, or **SETTINGS**
- Floating Home setup: **SETTINGS → Floating Home for Radio/Music → Continue** (Android permission needed)
- Floating Home after app launch: **tap house** to return; **drag** to reposition; **hold** to hide
- If overlay blocks a control: **notification → HIDE**, or disable in Eaton Drive settings
- Android diagnostics: **SETTINGS → Head unit diagnostic details**
- Vendor firmware info: **manufacturer Android Settings → About device / MCU**
- The Android release-signing workflow exists but has not been configured; debug APKs may still require uninstalling and reimporting datasets

Eaton Drive's launcher icon and native service have no need for root, system modification or internet access.
