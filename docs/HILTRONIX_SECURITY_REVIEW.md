# Thunderbird 10.1 - Hiltronix security review

Review date: 2026-10-28

Base revision: Thunderbird 10.1, upstream commit `8a4fefcc1b8511f415708d507bf86c5cae58ad2a`.

## Scope and status

This is a **targeted initial security review**, not a certification or a completed
audit of all source files and transitive dependencies. The source was inspected for
the Android identity, selected manifests, exported account migration, WebView
settings, TLS certificate validation, direct dependencies, and release signing.

Do not install or rely on a patched APK until the GitHub build, unit tests,
merged-manifest validation, and on-device mail/account import checks pass.

## Changes in the Hiltronix branch

- The independent Android package ID is
  `net.thunderbird.android.hiltronix`. The source namespace remains
  `net.thunderbird.android` to avoid unnecessary refactoring.
- The test package ID is `net.thunderbird.android.hiltronix.tests`.
- The app's outbound `SettingsProvider` is removed from its merged manifest.
  That exported provider can return settings **including passwords** to
  allowlisted official Thunderbird and K-9 packages. Removing it blocks that
  transfer from the custom app. It does **not** remove file-based account-settings
  import. The official app's own provider is not modified.
- Direct dependency upgrades: Apache Mime4j `0.8.9 -> 0.8.14`,
  jsoup `1.18.1 -> 1.23.2`, OkHttp `4.12.0 -> 4.12.1`, and
  Apache HttpClient5 `5.4.1 -> 5.6.5`.
  These require compatibility testing before deployment.
- The mail TLS trust manager now checks the SHA-256 of the encoded public key
  (SPKI) of every server certificate against eight compromised eMudhra keys.
  A match raises `CertificateException` before normal trust validation or the
  user-approved certificate fallback. No change is made to how unrelated
  self-hosted server certificates are accepted.
- The signed-release GitHub Actions workflow uses four encrypted repository
  secrets (see below), reconstructs the private keystore only on the Actions
  runner, verifies the package name and APK signature, and uploads a signed
  artifact. The keystore and its passwords must never be committed or logged.
  The local signing script remains an optional alternative.

## Evidence and findings

### Confirmed vulnerabilities addressed in code (testing pending)

1. **Apache Mime4j header injection - CVE-2024-21742.**
   The vulnerable `apache-mime4j-dom` version `0.8.9` can compose messages with
   unintended injected headers. Apache lists the fix in `0.8.10`; this branch
   selects `0.8.14`. Reference:
   https://github.com/apache/james-mime4j/blob/master/CHANGELOG.md
2. **jsoup HTML/XML processing.**
   The original `1.18.1` predates the fix for custom-Safelist raw-text
   sanitization (GHSA-pmhh-3w7g-xqp8) and the XML namespace allocation flaw
   (CVE-2026-75140). This branch selects `1.23.2`. The first issue affects
   custom Safelists permitting raw-text elements; applicability to Thunderbird's
   specific sanitizer has not yet been confirmed.
   References:
   https://github.com/advisories/GHSA-pmhh-3w7g-xqp8
   https://advisories.gitlab.com/maven/org.jsoup/jsoup/CVE-2026-75140/
3. **eMudhra compromised certificate public keys.**
   Platform certificate trusts can be vulnerable on Android devices missing the
   October 2026 Play Services key blocklist update. OkHttp `4.12.1` adds a
   relevant blocklist for its HTTPS requests, and a custom mail TLS-layer
   blocklist guards IMAP/POP3/SMTP independently.
   References:
   https://github.com/thunderbird/thunderbird-android/issues/11789
   https://lysine.dev/okhttp/changelogs/changelog_4x/

4. **Apache HttpClient hostname validation - CVE-2025-27820 and CVE-2026-71290.**
   The original `5.4.1` is within the affected range for incorrect public
   suffix-list verification. A separate 2026 vulnerability affects async
   HttpClient in versions before `5.6.4`; this mail stack uses the classic
   `DefaultHostnameVerifier`, but the upgrade avoids retaining the affected
   version. This branch selects `5.6.5`. Verify this API remains compatible
   with the mail module.
   References:
   https://github.com/advisories/GHSA-73m2-qfq3-56cx
   https://advisories.gitlab.com/maven/org.apache.httpcomponents.client5/httpclient5/CVE-2026-71290/

### Additional reviewed areas

- **Email HTML display:** `MessageWebView` has JavaScript disabled and blocks
  remote network loads by default. A user preference can allow network images,
  so remote-content privacy still depends on that setting.
- **App backup:** `app-common` sets `android:allowBackup="false"`. A
  Thunderbird settings-file export is a separate feature and should still work.
- **Telemetry:** `app-thunderbird` uses the no-op telemetry implementation for
  its release build. This does not constitute an audit of every network request.
- **Signing and update isolation:** Unique package ID prevents Play Store from
  treating this package as official Thunderbird. The local signing key must
  remain available for every future custom update.

### Open risks and validation requirements

1. **OAuth provider configuration:** Release configuration uses upstream OAuth
   client IDs. The Microsoft redirect explicitly references
   `net.thunderbird.android`; some other providers authorize a known
   package/signature or redirect URI. Re-register OAuth clients and redirect
   URIs before expecting Google, Microsoft, Yahoo, AOL, or Fastmail OAuth to
   work in this fork. Ordinary IMAP/SMTP account import does not by itself fix
   that mismatch.
2. **Cleartext traffic allowed:** The shared manifest and
   `network_security_config.xml` explicitly permit cleartext traffic. This
   supports existing account/autodiscovery behavior but can expose data if an
   insecure connection is configured. Keep IMAP/SMTP on TLS or STARTTLS, and
   inspect on-device connections. A global network-policy change was deferred
   because it could break some account setup paths.
3. **User-installed CA trust:** The network security configuration also trusts
   user-added CA certificates. This may be intentional for self-hosted servers;
   changing it could break configured accounts.
4. **Camera permission:** The QR-account-migration feature requests
   `android.permission.CAMERA`. It was not removed because that would change
   account-migration functionality.
5. **Age of platform targeting:** The 10.1 build targets Android SDK 34.
   Do not jump to SDK 36 or newer without exercising notification, foreground
   sync, account setup, and storage behavior.
6. **Dependency closure:** The full resolved runtime dependency tree, build
   plugins, native libraries, and lockfile/baseline changes still need an
   automated CVE scan and Gradle dependency guard verification. Not all
   transitive dependencies were reviewed.
7. **Unfinished tests:** Verify `:mail:common:test`, assemble the
   `fossRelease` variant, check the merged manifest for exported providers,
   and perform real IMAP/SMTP sync, attachment, HTML rendering, and
   account-settings import tests before installation as a primary mail client.

## GitHub Actions signed APK

The dedicated workflow is
`.github/workflows/build-thunderbird-10.1-signed.yml`.
It is restricted to pushes on `thunderbird-10.1-custom` in the Hiltronix
repository (and manual dispatch when available). It does not run on pull
requests. The secrets must be **repository Actions secrets** with these names:

- `THUNDERBIRD_KEYSTORE_BASE64`: Base64-encoded .p12/.jks file data.
- `THUNDERBIRD_KEYSTORE_PASSWORD`: keystore password.
- `THUNDERBIRD_KEY_ALIAS`: signing key alias in the keystore.
- `THUNDERBIRD_KEY_PASSWORD`: password for the signing key.

The workflow performs release lint and mail TLS tests before restoring the
keystore, writes the transient Gradle
`.signing/tb.release.upload.properties`, runs
`:app-thunderbird:assembleFossRelease`, and checks the signature with
`apksigner verify --verbose --print-certs` and application ID with
`aapt dump badging`. The key and Gradle signing properties are deleted at
the end of the job; the runner is ephemeral. Store the original keystore
offline as well. People with write access to the workflow and branch can
potentially modify it to expose secrets: limit write access accordingly.

Download the artifact named `Thunderbird-10.1-Hiltronix-SIGNED` from the
successful GitHub Actions run. No local signing step is needed for this
artifact.

## Local signing on Windows

Download the **unsigned** APK artifact from GitHub Actions for branch
`thunderbird-10.1-custom`, and extract it to a local directory.

In PowerShell from the repository checkout:

```powershell
.\scripts\Sign-ThunderbirdApk.ps1 `
    -ApkPath 'C:\Temp\app-thunderbird-foss-release-unsigned.apk' `
    -KeyStorePath 'D:\PrivateKeys\thunderbird.jks' `
    -KeyAlias 'my-key-alias'
```

Replace the example paths/alias. The script uses installed Android SDK build
tools to check the package ID, align the APK, prompt for keystore passwords,
sign with your own private key, and verify the signature. It writes
`thunderbird-10.1-hiltronix-signed.apk` beside the unsigned APK.

A public certificate alone is insufficient: APK signing requires its
corresponding **private key**, usually stored in a Java or PKCS#12 keystore.
Do not commit or upload that keystore or its password to GitHub, and do not
send the private key in chat.

Install the locally signed APK only after verifying the signing fingerprint
and recording a backup of the keystore. Future updates to this custom package
must use the same signing key and a higher `versionCode`.
