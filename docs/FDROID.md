# Prime Launcher — F-Droid preparation

## Stable releases

Set VERSION_NAME and VERSION_CODE in version.properties before merging to main.
The proposed initial stable code is 11000000; increment for every stable release. Public stable GitHub builds use com.bywilfried.prime.stable; F-Droid builds use com.bywilfried.prime.fdroid. Existing personal RC builds retain com.bywilfried.prime and Dev builds retain their debug ID. Existing GitHub users moving to the public stable app will install a separate application.

Run Check Prime F-Droid build manually on feature/fdroid-support; it validates Dev, Stable and F-Droid without publishing. Review all job results before merging.
After validation and merge to main, manually run Publish Prime Stable. It creates a GitHub release and vX.Y.Z tag only after compilation and APK version checks.

F-Droid build command:
```sh
git submodule update --init --recursive
./gradlew -PprimeStable=true assemblePrimeWithQuickstepFdroidRelease
```
Java 21; Android SDK 37 minor API 2 and build tools 37.0.0 as required by current Gradle configuration. Confirm availability in F-Droid build infrastructure.

Suggested fdroiddata settings for metadata/com.bywilfried.prime.fdroid.yml (not submitted or validated):
```yaml
RepoType: git
Repo: https://github.com/bywilfried/Prime-launcher.git
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
UpdateCheckData: 'version.properties|^VERSION_CODE=([0-9]+)|.|^VERSION_NAME=([0-9.]+)'
AutoUpdateMode: None
```
Draft build block (replace the placeholder with the full approved stable commit hash and confirm version values before submission):
```yaml
Builds:
  - versionName: '0.10.0'
    versionCode: 11000000
    commit: REPLACE_WITH_APPROVED_STABLE_COMMIT_SHA
    submodules: true
    gradle:
      - primeWithQuickstepFdroid
    gradleprops:
      - primeStable=true
```
The tag filter and UpdateCheckData use the documented F-Droid Tags syntax. Validate with fdroidserver before changing AutoUpdateMode from None to Version. No blanket scanner exceptions are proposed. Ten required AOSP prebuilts remain after removal of fifteen unused legacy JARs; individual source-build recipes or reviewer-approved handling remain necessary.

## Remaining blockers
- Retain Apache-2.0 pending resolution of the 18 inherited GPL-3.0-or-later source headers. Maintainer declined upstream contact; resolve or document for reviewer discussion, with legal clearance required before distribution; do not change the project license without maintainer approval.
- All 26 original tracked JARs matched upstream Git blobs. Fifteen legacy prebuilts were removed in 24d40c0; ten required prebuilts remain. Framework 10–16 are used by compatibility modules; SystemUI-core-16, SystemUI-statsd-16 and WindowManager-Shell-16 are used by current modules. Independent source reconstruction and reviewer acceptance remain open. Actions run 45578003085 confirmed all three builds and exactly ten source scanner errors. Automatic push triggers were removed in b90eead.
- Previous official APK scanner reported zero problems; rerun after cleanup. Inspect transitive dependency licenses and JitPack source provenance.
- Test Google Fonts, optional feed and web suggestions on a device without Google Play Services; inspect defaults and network requests.
- Fastlane contains English (en-US) and French (fr-FR) titles, descriptions and version-code changelogs. The placeholder device-frame.jpg was removed. The maintainer will supply final screenshots at the end of preparation. Verify images/icon.png against current branding. App resource translations are a separate audit.
- Run fdroidserver lint, checkupdates and build with actual fdroiddata metadata.
- Verify signing and reproducibility separately. GitHub and F-Droid APKs use distinct application IDs and signing certificates.
- No F-Droid submission or stable release has been made by this preparation.

Official policy: https://f-droid.org/docs/Inclusion_Policy/
Metadata: https://f-droid.org/docs/Build_Metadata_Reference/

## Mandatory localization audit before first public release

- Audit all user-facing hardcoded strings in Java, Kotlin, Compose and XML, especially French-only additions.
- Ensure complete default English resources and matching French translations for all Prime features.
- Compare resource keys, placeholders, plurals and formatting across inherited locales; identify missing and obsolete translations.
- Verify fallback to English, language selection, truncation and RTL layouts on device.
- Review Prime Search, WebApps, gestures, settings, backup, menus, accessibility labels and error messages.
- Review localized Fastlane metadata separately; remove or correct misleading inherited Lawnchair descriptions.
- Complete and validate this audit before the first F-Droid publication; do not treat this checklist as evidence that translations have already been checked.

## Privacy and AntiFeatures review

See [privacy draft](PRIVACY.md) and [draft metadata](fdroid/com.bywilfried.prime.fdroid.yml). NonFreeNet is a candidate for optional third-party web suggestions, favicon retrieval and online integrations; assess against current F-Droid definitions and actual network behavior. No final AntiFeature or license declaration is approved. No blanket scanner exceptions. Validate on device and obtain maintainer approval before submission.
