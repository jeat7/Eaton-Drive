# Eaton Drive — permanent signing and in-place Android updates

**One-time key setup, then no uninstall/reinstall for each new version.**

Android accepts an in-place APK update only when the application ID and signing
certificate remain the same and the incoming versionCode is not lower.
Eaton Drive's Android app ID is `uk.co.eaton.drive`, and the signed release
workflow guarantees one permanent signing certificate and a monotonically
increasing versionCode (`200000 + the workflow run number`).

## One-time creation on your own Windows/Mac/Linux computer

**Never upload your private keystore or password to chat, commit them to Git,
email them, or publish them as GitHub Actions artifacts. Back up both privately.**

Install a JDK such as Eclipse Temurin 17 if `keytool` is unavailable. Open a
terminal or PowerShell in a folder that is **outside the GitHub repository**.

Run:

```sh
keytool -genkeypair -v -keystore eaton-drive-release.jks -storetype PKCS12 -alias eaton-drive -keyalg RSA -keysize 3072 -validity 12000 -dname "CN=Eaton Drive, OU=Personal, O=Eaton, C=GB"
```

Choose a strong password when prompted. Use the **same key password and
keystore password**. Record the password privately; the keystore cannot be
recovered by GitHub or by ChatGPT if it is lost.

Create the following two **GitHub repository Actions secrets** using
[Settings → Secrets and variables → Actions](https://github.com/jeat7/Eaton-Drive/settings/secrets/actions)
then **New repository secret**:

- `EATON_DRIVE_KEYSTORE_B64`: entire base64-encoded contents of `eaton-drive-release.jks`
- `EATON_DRIVE_STORE_PASSWORD`: the exact password entered in keytool

To place the keystore's base64 on the clipboard:

**Windows PowerShell** (in the directory with the keystore):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path ".\eaton-drive-release.jks").Path)) | Set-Clipboard
```

**macOS:**

```sh
base64 -i eaton-drive-release.jks | tr -d '\n' | pbcopy
```

**Linux** (with xclip installed):

```sh
base64 -w0 eaton-drive-release.jks | xclip -selection clipboard
```

Paste clipboard data into the **secret** field in GitHub, then clear your
clipboard. Do not put any passwords or base64 key material in an issue,
commit, Markdown file or ChatGPT message.

## Every new signed APK

1. Open [Actions → Eaton Drive permanent signed APK](https://github.com/jeat7/Eaton-Drive/actions/workflows/android-signed.yml).
2. Choose **Run workflow** on `main`.
3. On a successful run, download the **Eaton-Drive-PERMANENTLY-SIGNED-APK** artifact.
4. Extract the GitHub Actions artifact ZIP and open `app-release.apk` on Android.
5. After the first permanently signed install, future signed releases replace the
   app **without clearing its private map, road, postcode or address files**.

The workflow keeps the same `applicationId` and signing certificate and
automatically increases versionCode on successive signed workflow runs. App
versions / features continue to be changed in `app/build.gradle`.

**Important: transition from v0.9.1 debug:** Previous GitHub debug APKs were
signed with temporary runner keys. You cannot upgrade from those to this new
permanently signed APK in place unless you happen to have the original private
debug key. **One final uninstall is necessary** to switch signing identities.
Back up the ORIGINAL downloaded England/Wales/Scotland .map, GB routing .rd5,
and West Midlands address .db files before the uninstall, because app-private
copies will be erased. The built-in postcode database will restore automatically
after installing the permanent release. Import your maps, road tiles and
address index again **only once** after that transition.

**Do not use the debug APK artifacts for subsequent installations**: they may
still be signed with unrelated keys. Use only the
`Eaton-Drive-PERMANENTLY-SIGNED-APK` artifact.

**Security:** The workflow only runs when manually triggered on your repo, and
restores the private key from encrypted GitHub Actions secrets into a temporary
file on the ephemeral GitHub runner. It never prints or publishes the private
key. Store an offline backup of the .jks and password yourself. GitHub's
download artifacts expire, but rebuilding with the SAME key remains safe.

**Navigation safety:** Updating app software does not establish sat-nav accuracy.
Continue stationary/passenger testing of routing, voice, GPS and OEM components
before using it while driving.
