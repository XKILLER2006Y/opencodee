# Release keystore

`release.jks` + `keystore.properties` sign the release APK (local `assembleRelease` and the GitHub Actions workflow).
They are committed on purpose because this repo is **private**.

- Never make this repo public while these files are in it. To go public: `git rm` both, rotate the key, and move the
  password/keystore into GitHub secrets again.
- Keep a copy of `release.jks` somewhere safe. A new key means Android refuses to update an app signed with the old one
  (users would have to uninstall first; use Settings → Backup & move before that).
- Alias `ocmobile`, PKCS12, RSA 4096, valid ~100 years.

## Updating over APKs signed with the OLD key
Releases before this change were signed with the debug keystore stored in the secret `SIGNING_KEYSTORE_B64`.
If you want existing installs to keep updating, replace `release.jks` with that key and edit `keystore.properties`:

    echo "$SIGNING_KEYSTORE_B64" | base64 -d > keystore/release.jks
    # keystore.properties: storePassword=android  keyAlias=androiddebugkey  keyPassword=android

## Regenerate
    keytool -genkeypair -keystore keystore/release.jks -storetype PKCS12 -alias ocmobile \
      -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=OpenCode mobile, O=you, C=ID"
