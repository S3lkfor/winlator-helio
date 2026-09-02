# Code Signing — How to Set Up Your APK Signing Keystore

This fork's CI is configured to sign release APKs when you provide a
keystore. Without one, the release APK falls back to the **debug**
signing config and is still installable on your phone, but it is not
suitable for distribution to other users.

## One-time setup on your PC (5 minutes)

You need a Java JDK to run `keytool`. The Android Studio install
includes one, or you can install OpenJDK 17 directly.

### 1. Generate the keystore

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias winulator-helio
```

`keytool` will ask for:
- a keystore password (e.g. `hunter2-strong-password`)
- your name, organization, country (any values are fine — the cert
  metadata doesn't affect the APK)
- the key password (you can press **Enter** to reuse the keystore
  password)

This produces a file named `release.keystore` (~3 KB).

### 2. Base64-encode the keystore (GitHub Secrets can only hold strings)

```bash
# macOS / Linux
base64 -i release.keystore | tr -d '\n' > release.keystore.b64
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -NoNewline release.keystore.b64
```

The file `release.keystore.b64` will be a single very long line.

### 3. Add the four required GitHub Secrets

Go to: **https://github.com/S3lkfor/winulator-helio/settings/secrets/actions**

Click **New repository secret** for each of these:

| Name                          | Value                                  |
|-------------------------------|----------------------------------------|
| `RELEASE_KEYSTORE_BASE64`     | the entire contents of `release.keystore.b64` |
| `RELEASE_STORE_PASSWORD`      | the password you set in step 1         |
| `RELEASE_KEY_ALIAS`           | `winulator-helio` (or whatever alias you chose) |
| `RELEASE_KEY_PASSWORD`        | the key password from step 1           |

All four are required. If any are missing, the CI will still build —
it will just produce a **fallback unsigned release APK** instead of
a signed one.

### 4. Re-run the workflow

After adding the secrets, push any change (or click *Run workflow*
manually on the Actions tab) and the release APK will now be signed
and ready to install.

## Important security notes

- **Do not commit `release.keystore` to the repo.** Add it to `.gitignore`.
- **Losing the keystore = losing your signing identity.** If you later
  want to update the app on a user's phone with the same signature,
  you must use the *same* keystore. Back it up somewhere safe
  (password manager, encrypted USB stick).
- The keystore in this guide is valid for **10000 days (~27 years)**.
  Choose a shorter validity period if you want a more frequent key
  rotation.
- The CI uploads the signed APK as an artifact; it is **not** pushed
  to the public release page until you create a `v*` tag. See
  `helio-g88/README.md`.
