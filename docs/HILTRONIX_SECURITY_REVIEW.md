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

## Build revisions and ongoing October 29 security work

- The About Thunderbird page now includes a **Hiltronix Revision** row
  immediately after the existing Version row and before Authors. It displays
  the build-embedded string (not today's date at runtime).
- Signed GitHub Actions runs call `scripts/generate_hiltronix_revision.py`
  before compilation. It counts attempted signed workflow runs for the
  America/Winnipeg calendar date and generates `YYYY.MM.DD.Rev`.
  The same build gets a monotonically increasing Android `versionCode`
  (`YYYYMMDD * 100 + Rev`), allowing same-key updates.
- Local unsigned builds without generated CI metadata are marked
  **Development build**, rather than being misidentified as signed releases.
- GitHub Actions has automatic tests for the date boundary and daily revision
  sequence.
- A separate OSV dependency scan now queries the real resolved release
  Maven dependencies. The generated JSON results require manual triage;
  a list of old dependencies alone is not evidence of exploitability.
- **New finding:** Apache Commons Lang3 `3.7` was in the original resolved
  dependency inventory. It is within the affected range of
  `CVE-2025-48924` (uncontrolled recursion via `ClassUtils.getClass`).
  The app now requests a patched `3.18.0` release. CI compilation and
  final dependency resolution must confirm that it is selected.
  Reference: https://commons.apache.org/proper/commons-lang/security.html
- **Not affected by the notorious Text4Shell CVE:** the old dependency
  `commons-text:1.3` predates the dangerous default lookups introduced
  in `1.5`; `CVE-2022-42889` affects `1.5` through `1.9`. Upgrading
  `1.3` purely on that advisory would be a false positive.
  Reference: https://commons.apache.org/proper/commons-text/security.html
- **jsoup `1.23.2`:** upstream release notes include the XML namespace
  efficiency patch (`jhy/jsoup#2556`) corresponding to
  `CVE-2026-75140`, despite third-party advisory wording that ambiguously
  says "through 1.23.2." Verify library contents before classifying the
  released version as affected.
  Reference: https://jsoup.org/news/release-1.23.2

## CodeQL SARIF triage: security findings (2026-10-29)

The CodeQL SARIF artifact from run
https://github.com/Hiltronix/thunderbird-android/actions/runs/40883976224
contained exactly **two** Java/Kotlin findings. This is the actual analyzer
result, not simply the workflow success indicator.

1. **`java/android/insecure-local-authentication`**
   at `TextFieldOutlinedPasswordBiometric.kt`.
   The saved email-server password is already loaded in application memory,
   and a `BiometricPrompt` success callback controls whether it appears in
   plain text. The callback alone is *not* cryptographically bound to Android
   Keystore. It can potentially be bypassed on a modified or compromised
   device. This is an authentic architectural limitation rather than proof
   that server passwords are encrypted at rest with biometric protection.
   Mitigation now committed: password visibility and authentication state are
   ephemeral (not `rememberSaveable`), and the component clears those values
   on activity `ON_STOP`. The need for authentication is now based on whether
   a stored password is nonempty. A true cryptographic fix would require
   reworking how credentials are stored/revealed using a Keystore-backed
   operation; it is **not** claimed fixed by the UI-state change.
   Reference:
   https://codeql.github.com/codeql-query-help/java/java-android-insecure-local-authentication/

2. **`java/insecure-trustmanager`**
   at `mail/testing/.../TestTrustedSocketFactory.java`.
   The implicated implementation was `VeryTrustingTrustManager`, used only
   by `mail/testing` in mock-server integration tests, not the production
   TLS trust manager. It previously accepted any client/server certificate.
   It now rejects client certificates and accepts only the specific pinned
   mock-server certificate from `KeyStoreProvider`. Regression tests were
   added, and the signed-build workflow now includes `:mail:testing:test`.
   The new CodeQL result and tests must pass before considering this closed.

No other findings were reported by **that SARIF file**. CodeQL reported
partial extraction coverage (1589/2109 Kotlin and 292/345 Java files for one
invocation); this is **not** proof that all sources were assessed. The
production Android manifest, network communication, and credential storage
must continue to be reviewed.

## Continued review: attachment cache and message HTML (2026-10-29)

- **Attachment-cache incomplete writes:** `AttachmentTempFileProvider` previously
  copied an attachment directly to its final cache filename and leaked streams
  when opening or copying failed. A later request could reuse a truncated
  attachment. It now stages the copy under a temporary filename, closes both
  streams with try-with-resources, calls `FileDescriptor.sync()`, and
  atomically renames the fully written file into place. Incomplete files are
  deleted after errors. The app must pass a signed build and attachment-sharing
  runtime test before this hardening is considered validated.
- **Untrusted email WebView local file access:** The message HTML renderer
  already disables JavaScript and blocks remote network loads by default.
  It now explicitly disables `file://` filesystem access and both deprecated
  `file://` cross-origin access toggles on all supported API levels.
  It retains `content://` access for inline attachment rendering. This
  follows Android's recommendations for untrusted WebView content.
  Reference:
  https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion
- **FileProvider audit:** The decrypted and temporary attachment providers
  are declared `android:exported="false"`; they issue time-limited
  URI grants to external applications when sharing is requested. Their path
  mappings are limited to `cache/decrypted` and `cache/temp`, rather than
  granting access to the entire app-private filesystem.
- **CodeQL execution management:** Added per-branch concurrency cancellation
  so new scans supersede obsolete source revisions. SARIF artifact output has
  been enabled, but actual findings have not yet been reviewed.
- **Latest revision evidence:** The verified signed build at
  `e7ae44ae` was `2026.10.29.12`. New hardening changes on the branch
  have not yet been confirmed by a completed signed build.

## Follow-up review: TLS keystore durability and CodeQL evidence

- The signed APK and automated security audit at commit
  `e7ae44aef0ed8310b5b747be2d590bfd82b413e2` passed:
  signed-build run https://github.com/Hiltronix/thunderbird-android/actions/runs/40879248040
  and audit run https://github.com/Hiltronix/thunderbird-android/actions/runs/40879247994.
  The signed APK reported revision **2026.10.29.12** in its workflow logs.
- **Local trusted-TLS-certificate durability:** the former
  `LocalKeyStore.writeCertificateFile()` directly overwrote the existing
  keystore. A crash while writing could leave the saved certificate file
  truncated or corrupted. The update writes a temporary file in the same
  directory, calls `FileDescriptor.sync()`, and replaces the destination via
  `renameTo()` only after the new keystore has been completely serialized.
  It also avoids logging full certificate details on mismatch.
  Tests now verify persistence across reopening, sequential certificate saves,
  no left-over temporary files after a successful save, and recovery when an
  interrupted temporary file exists. CI verification is pending.
- **CodeQL coverage:** the first Java/Kotlin CodeQL run completed, but the
  log reports extraction of only 1589/2109 Kotlin and 292/345 Java files in
  that invocation. A successful workflow is **not** proof of zero alerts.
  CodeQL has therefore been configured to archive SARIF output for actual
  findings review independently of the GitHub code-scanning alerts API.
  Detailed findings review remains pending.
- **Exported components:** the exported launcher/message-list activity is
  required, the exported composition activity supports Android mailto/send
  intents, and the AppAuth redirect receiver supports OAuth deep links.
  Email attachment providers are not exported; the credential-export
  SettingsProvider was previously removed. Removing the remaining activities
  indiscriminately would break normal mail functions.
- **Open issues:** inspect SARIF findings and CodeQL gaps, test the updated
  keystore on Android, exercise account import and IMAP/SMTP/attachments,
  and evaluate the implications of globally permitted cleartext traffic.

## October 29 OSV and Android Lint findings

- **OSV inventory:** 253 resolved Maven dependencies scanned. The GitHub
  Actions OSV query returned **0 packages with published OSV advisories**.
  This confirms no advisory matches from that data source and run; it does
  **not** establish that all app code, build plugins, or native dependencies
  are vulnerability-free.
  Scan run: https://github.com/Hiltronix/thunderbird-android/actions/runs/40877637101
- **Resolved patches checked:** Apache Mime4j `0.8.14`, jsoup `1.23.2`,
  Apache HttpClient `5.6.5`, OkHttp `4.12.1`, and Commons Lang `3.18.0`
  appeared in the resolved release Gradle dependency tree.
- **Android Lint security warning:** The upstream code implements a custom
  `X509TrustManager`. The code calls the default system trust manager,
  verifies the hostname, and permits explicitly trusted certificates for
  an exact hostname and port if the default checks fail. This is a deliberate
  self-hosted-server feature and should remain under careful review.
- **Android backup/data-transfer exposure:** The upstream
  `android:allowBackup="false"` may not block device-to-device migration
  on some Android 12+ vendor devices. The custom branch now also declares
  `android:fullBackupContent="false"` for older Android versions and explicit
  `android:dataExtractionRules` excluding all app file/database/preferences
  domains from cloud and device transfers. Manual account-settings export
  and import remain separate features.
  Reference: https://developer.android.com/identity/data/autobackup
- **CodeQL:** The custom development branch is now scanned with GitHub CodeQL
  for Java/Kotlin security findings. Results require inspection.
  Run: https://github.com/Hiltronix/thunderbird-android/actions/runs/40879005152
- **Remaining:** verify CI after the backup-rule change, inspect the
  compiled Android manifest and Java/Kotlin CodeQL results, audit native
  code and unreported vulnerabilities, and test account import and actual
  mail operations on a device.

## CI verification completed on 2026-10-29

- Signed APK GitHub Actions run (second attempt): **success**, including
  `:mail:common:test`, `:app-thunderbird:lintFossRelease`,
  `:app-thunderbird:assembleFossRelease`, Android `apksigner verify`,
  application ID check, artifact upload, and signing-file cleanup:
  https://github.com/Hiltronix/thunderbird-android/actions/runs/40813491629
- Separate audit run: **success**, including resolved runtime dependency
  inventory and `:app-thunderbird:lintFossRelease`:
  https://github.com/Hiltronix/thunderbird-android/actions/runs/40813413945
- Signed artifact: `Thunderbird-10.1-Hiltronix-SIGNED`.
- These passing checks **do not** establish that every vulnerability, lint
  warning, exported component, or network/data flow has been audited.
- Pending: automated dependency-CVE and supply-chain scan, evaluation of
  transitive/native dependencies, review of Android Lint warnings,
  merged APK manifest validation, OAuth redirects for custom package
  identity, and on-device tests of account import and IMAP/SMTP behavior.

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

### Security findings addressed in code (CI tests passed; runtime verification pending)

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
- **App backup:** `app-common` sets `android:allowBackup="false"`
  and explicit Android backup/device-transfer exclusion rules. Thunderbird
  settings-file export is separate and should still work.
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
Never commit the keystore or passwords as repository files, and do not
send the private key in chat. The GitHub Actions release process instead stores
these values in encrypted GitHub repository secrets.

Install the locally signed APK only after verifying the signing fingerprint
and recording a backup of the keystore. Future updates to this custom package
must use the same signing key and a higher `versionCode`.
