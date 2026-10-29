# Hiltronix Thunderbird 10.1 — customized Android build

> **Unofficial fork of [Thunderbird for Android](https://github.com/thunderbird/thunderbird-android).** The customization and signed APK are maintained on the [`thunderbird-10.1-custom` branch](https://github.com/Hiltronix/thunderbird-android/tree/thunderbird-10.1-custom). The `main` branch remains upstream-based source, not the custom application. This fork is not distributed or supported by Mozilla Thunderbird.

## Why this fork exists

The primary purpose of this customization is to **retain Thunderbird for Android 10.1 and its user-adjustable email message-body text size**, a control no longer available in newer Thunderbird Android releases. The fork also updates selected libraries and includes targeted reliability, privacy, and security improvements without changing Thunderbird's core mail experience.

## Improvements and fixes in the custom build

- **Preserved message-body text sizing:** Based on Thunderbird **10.1**, retaining the ability to adjust email body text size independently of the rest of the interface.
- **Independent installation and signing:** Uses Android application ID `net.thunderbird.android.hiltronix` and a separately controlled signing certificate, so official Play Store updates do not overwrite the custom app. The regular settings-file import workflow is retained.
- **Hiltronix revision identification:** Displays a date-based `YYYY.MM.DD.Rev` build revision in *Settings → About Thunderbird*, with a matching increasing Android version code for subsequent updates.
- **Updated libraries:** Apache Mime4j **0.8.14**, jsoup **1.23.2**, OkHttp **4.12.1**, Apache HttpClient 5 **5.6.5**, and Apache Commons Lang **3.18.0**, replacing older 10.1 dependencies.
- **Mail TLS protection:** Blocks known compromised eMudhra certificate public keys during IMAP/SMTP TLS verification while retaining support for explicitly trusted self-hosted mail-server certificates.
- **Safer trusted-certificate storage:** Stages and syncs certificate-keystore changes before atomically replacing the saved file, reducing corruption risk from interrupted writes. Added persistence and interrupted-write tests.
- **Safer attachment sharing:** Copies attachments into a temporary cache file before atomically publishing the completed copy; prevents failed or partial copies from being reused.
- **Hardened HTML email display:** Keeps JavaScript disabled, explicitly disables WebView local-file access, and retains default blocking of remote message images.
- **Restricted inter-app account-data exposure:** Removes the exported settings content provider that could share account credentials with other packages. Manual settings import/export remains available.
- **Backup and privacy safeguards:** Disables Android application backups and device-to-device data extraction for the app; uses a no-op telemetry implementation in the release build.
- **Sensitive-data logging fixes:** Stops logging stored preference values (which can include passwords and OAuth state), and redacts account details from `ServerSettings` diagnostic text.
- **Password-display and test hardening:** Clears password-reveal authentication state when the app is backgrounded; limits the testing-only TLS trust manager to its expected mock certificate.
- **Automated verification:** GitHub Actions builds and signs a non-debuggable APK using repository secrets, verifies the package ID and signature, runs mail/security regression tests and Android Lint, and performs OSV dependency and CodeQL scans.

## Builds, compatibility, and upstream credit

- **Custom source:** [`thunderbird-10.1-custom`](https://github.com/Hiltronix/thunderbird-android/tree/thunderbird-10.1-custom).
- **Last device-tested signed APK reported:** Hiltronix revision **2026.10.29.27** — [successful signed build](https://github.com/Hiltronix/thunderbird-android/actions/runs/40913098496). Installation, account-settings import, and sending/receiving mail have been tested; not every feature or OAuth provider has been verified.
- **Important:** This is **not an official Thunderbird release**. Official Google Play / F-Droid download links, release badges, and signing-certificate fingerprints in the original README below refer to the **upstream application**, not this custom build. Some OAuth providers may require separate client registrations or redirect configuration for the custom package.
- **Original project and developers:** [Mozilla Thunderbird for Android](https://github.com/thunderbird/thunderbird-android), built on [K-9 Mail](https://k9mail.app/). All original development credit remains with the upstream maintainers and contributors. Hiltronix is responsible only for the modifications described here. The original project licensing and notices remain in force.

For detailed changes and verification notes, see the [custom security review](https://github.com/Hiltronix/thunderbird-android/blob/thunderbird-10.1-custom/docs/HILTRONIX_SECURITY_REVIEW.md) and [release checklist](https://github.com/Hiltronix/thunderbird-android/blob/thunderbird-10.1-custom/docs/HILTRONIX_RELEASE_CHECKLIST.md).

---

## Original Thunderbird for Android README

# Thunderbird for Android

<a href="https://play.google.com/store/apps/details?id=net.thunderbird.android&referrer=utm_campaign%3Dandroid_metadata%26utm_medium%3Dweb%26utm_source%3Dgithub.com%26utm_content%3Dbadge" target="_blank"><img src="./docs/assets/get-it-on-play.png" alt="Get it on Google Play" height="28"></a>
<a href="https://f-droid.org/packages/net.thunderbird.android"><img src="./docs/assets/get-it-on-fdroid.png" alt="Get it on F-Droid" height="28"></a>
<a href="https://apps.obtainium.imranr.dev/"><img src="./docs/assets/get-it-on-obtainium.png" alt="Get it on Obtainium" height="28"></a>
[![Latest release](https://img.shields.io/github/release/thunderbird/thunderbird-android.svg?style=for-the-badge&filter=THUNDERBIRD_*&logo=thunderbird)](https://github.com/thunderbird/thunderbird-android/releases/latest)
[![Latest beta release](https://img.shields.io/github/release/thunderbird/thunderbird-android.svg?include_prereleases&style=for-the-badge&label=beta&filter=THUNDERBIRD_*b*&logo=thunderbird)](https://github.com/thunderbird/thunderbird-android/releases)

Thunderbird for Android is a powerful, privacy-focused email app. Effortlessly manage multiple email accounts from one app, with a Unified Inbox option for maximum productivity. Built on open-source technology and supported by a dedicated team of developers alongside a global community of volunteers, Thunderbird never treats your private data as a product.

Thunderbird for Android is based on K-9 Mail, which comes with a rich history of success and functionality in open source email.

## Download

Thunderbird for Android can be downloaded from a couple of sources:

- Thunderbird on [Google Play](https://play.google.com/store/apps/details?id=net.thunderbird.android&referrer=utm_campaign%3Dandroid_metadata%26utm_medium%3Dweb%26utm_source%3Dgithub.com%26utm_content%3Dlink) or [F-Droid](https://f-droid.org/packages/net.thunderbird.android)
- Thunderbird Beta on [Google Play](https://play.google.com/store/apps/details?id=net.thunderbird.android.beta&referrer=utm_campaign%3Dandroid_metadata%26utm_medium%3Dweb%26utm_source%3Dgithub.com%26utm_content%3Dlink) or [F-Droid](https://f-droid.org/packages/net.thunderbird.android.beta)
- [Github Releases](https://github.com/thunderbird/thunderbird-android/releases)
- [FFUpdater](https://f-droid.org/packages/de.marmaro.krt.ffupdater/) allows installing the latest versions from ftp.mozilla.org
- [Obtainium](https://obtainium.imranr.dev/): Use "Filter release title by regular expression" under "Additional options" with `Thunderbird`. More info in [Complex Obtainium Apps list](https://apps.obtainium.imranr.dev/).

By using Thunderbird for Android Beta, you have early access to current development and are able to try new features earlier.

Check out the [Release Notes](https://github.com/thunderbird/thunderbird-android/releases) to find out what changed in each version of Thunderbird for Android.

The SHA-256 fingerprints for our signing certificates are available in [SECURITY.md](./SECURITY.md#verifying-fingerprints).

## Need Help? Found a bug? Have an idea? Want to chat?

If the app is not behaving like it should, or you are not sure if you've encountered a bug:

- Check out our [knowledge base](https://support.mozilla.org/products/thunderbird-android) and [frequently asked questions](https://support.mozilla.org/kb/thunderbird-android-8-faq)
- Ask a question on our [support forum](https://support.mozilla.org/en-US/questions/new/thunderbird-android)

If you are certain you've identified a bug in Thunderbird for Android and would like to help fix it:

- File an issue on [our GitHub issue tracker](https://github.com/thunderbird/thunderbird-android/issues)

If you have an idea how to improve Thunderbird for Android:

- Tell us about and vote on your feature ideas on [connect.mozilla.org](https://connect.mozilla.org/t5/ideas/idb-p/ideas/label-name/thunderbird%20android).
- Join the discussion about the latest changes in the [Thunderbird Android Beta Topicbox](https://thunderbird.topicbox.com/groups/android-beta).

The Thunderbird Community uses Matrix to communicate:

- General chat about Thunderbird for Android and K-9 Mail: [#tb-android:mozilla.org](https://matrix.to/#/#tb-android:mozilla.org)
- Development and other ways to contribute: [#tb-android-dev:mozilla.org](https://matrix.to/#/#tb-android-dev:mozilla.org)
- Reach the broader Thunderbird Community in the [community space](https://matrix.to/#/#thunderbird-community:mozilla.org)

## Roadmap

To learn more about all the wonderful things planned for this year please see our
[roadmap](https://github.com/orgs/thunderbird/projects/19/views/1). The core team's day to day activities are additionally
tracked in our [sprint board](https://github.com/orgs/thunderbird/projects/20/views/1).

## Contributing

We welcome contributions from everyone.

- Development: Have you done a little bit of Kotlin? The [CONTRIBUTING](docs/CONTRIBUTING.md) guide will help you get started
- Translations: Do you speak a language aside from English? [Translating is easy](https://hosted.weblate.org/projects/thunderbird/thunderbird-android/) and just takes a few minutes for your first success.
- We have [a number of other contribution opportunities](https://blog.thunderbird.net/2024/09/contribute-to-thunderbird-for-android/) available.
- Thunderbird is supported solely by financial contributions from users like you. [Make a financial contribution today](https://www.thunderbird.net/donate/mobile/?form=tfa)!
- Make sure to check out the [Mozilla Community Participation Guidelines](https://www.mozilla.org/about/governance/policies/participation/).

### Engineering Process

We use a structured engineering process to propose, decide, and deliver technical changes. This includes:
- [Requests for Comments (RFCs)](docs/engineering/rfcs/README.md) for technical direction.
- [Technical Designs](docs/engineering/technical-designs/README.md) for implementation details.
- [Architecture Decision Records (ADRs)](docs/engineering/adr/README.md) for durable architectural decisions.

You can find more information in the [`docs/engineering`](docs/engineering) directory.

We encourage team members and contributors to read through our engineering documentation to understand the
processes and decisions that have shaped this project so far.

## K-9 Mail

In June 2022, [K-9 Mail joined the Thunderbird family](https://k9mail.app/2022/06/13/K-9-Mail-and-Thunderbird.html)
as the foundation for Thunderbird on Android. Since then, we’ve been updating both apps to give
users the same solid experience, so it’s normal to notice that K-9 Mail and Thunderbird look and
feel nearly identical. They’re built on the same code, and that’s intentional. You'll notice some
features are selectively enabled for Thunderbird as opposed to K-9 Mail, usually when they are
simply a better fit for Thunderbird (like the import from K-9 functionality).

If you prefer the robot dog and would like to keep K-9 Mail around, you can find it here:

- [K-9 Mail on Google Play](https://play.google.com/store/apps/details?id=com.fsck.k9&utm_source=thunderbird-android-github&utm_campaign=download-section)
- [K-9 Mail on F-Droid](https://f-droid.org/packages/com.fsck.k9/)
- [K-9 Mail on Obtainium](https://obtainium.imranr.dev/) (use `K-9 Mail` as filter, see [notes](#download) above)

## Forking

If you want to use a fork of this project please ensure that you replace the OAuth client setup in the `app-k9mail/src/{debug,release}/kotlin/app/k9mail/auth/K9OAuthConfigurationFactory.kt` and `app-thunderbird/src/{debug,daily,beta,release}/kotlin/net/thunderbird/android/auth/TbOAuthConfigurationFactory.kt` with your own OAuth client setup and ensure that the `redirectUri` is different to the one used in the main project. This is to prevent conflicts with the main app when both are installed on the same device.

## License

Thunderbird for Android is licensed under the [Apache License, Version 2.0](LICENSE).
