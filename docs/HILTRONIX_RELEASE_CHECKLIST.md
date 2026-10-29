# Thunderbird 10.1 — Hiltronix production release verification

Release candidate: Thunderbird 10.1, `net.thunderbird.android.hiltronix`
Branch: `thunderbird-10.1-custom`
Prepared: 2026-10-29

## Release-build gate

- [ ] Confirm the latest **signed** workflow succeeded at the intended commit
  (not merely the unsigned build or separate audit workflow).
- [ ] `:mail:common:test` and `:mail:testing:test` pass.
- [ ] `:legacy:storage:testDebugUnitTest`, including the sensitive-logging
  regression test, passes.
- [ ] `:app-thunderbird:lintFossRelease` passes.
- [ ] Release `apksigner verify`, application ID, non-debuggable manifest,
  absence of the credential-export provider, and Android `versionCode`
  verification all pass.
- [ ] Review the latest OSV and CodeQL findings, documenting accepted
  residual risk before tagging a production revision.
- [ ] Download the artifact `Thunderbird-10.1-Hiltronix-SIGNED` and
  preserve its SHA-256 digest and signing-certificate fingerprint.

## On-device functional gate (Pixel 6, non-primary account first)

- [ ] Install the APK and verify Settings > About > About Thunderbird
  shows **Hiltronix Revision** between Version and Authors, matching CI.
- [ ] Add an account using IMAP with TLS/STARTTLS and SMTP with TLS/STARTTLS.
- [ ] Test incoming mail, folder refresh, read/unread sync, delete,
  move, sent/drafts saving and SMTP delivery.
- [ ] Open HTML and plaintext messages. Verify the older **email body
  text-size setting** still works; test inline images and explicitly
  enabled remote content.
- [ ] Open received attachments, share an attachment to another app,
  and send messages with attachments. Check shared attachments are complete.
- [ ] Receive a notification and verify background synchronization and
  restart after phone reboot.
- [ ] Export and import account settings as needed. Check that the previous
  account-export content-provider functionality is not available to other apps.
- [ ] Test password visibility: hide/reveal using biometric/device
  authentication, background the app, reopen, and verify a new unlock
  is required to reveal an existing password.
- [ ] Install a later APK signed by the **same** key with a higher
  `versionCode`; verify it updates in place with account data intact.
- [ ] Test OAuth logins separately if needed; the Hiltronix package/signature
  does not automatically inherit upstream Google/Microsoft redirect
  registrations.

## Accepted deferrals / non-blocking architecture work

- **Application-level encryption of account passwords and OAuth state in
  SQLite** is explicitly deferred to a later revision at the owner's request.
  The private app database and Android OS protections remain in use.
  Existing app-backup/device-transfer exclusions are retained.
- Biometric display authentication is not cryptographically coupled to a
  Keystore operation. UI-state mitigation is implemented and the residual
  CodeQL warning is tracked. A cryptographic credential-reveal redesign
  requires separate migration/compatibility work.
- Full platform/SDK upgrades and sweeping dependency updates are deferred
  unless required to fix a confirmed release-blocking issue.

## Release decision

Do not label a build **production verified** solely because CI completed.
Only sign off once the current signed APK passes the release-build gate
and the required device tests. Record the revision, GitHub Actions URL,
certificate SHA-256 fingerprint and known limitations in release notes.
