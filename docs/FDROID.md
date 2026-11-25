# Prime Launcher — F-Droid preparation

## Stable releases

Set VERSION_NAME and VERSION_CODE in version.properties before merging to main.
The proposed initial stable code is 11000000; increment for every stable release. Public stable GitHub builds use com.bywilfried.prime.stable; F-Droid builds use com.bywilfried.prime.fdroid. Existing personal RC builds retain com.bywilfried.prime and Dev builds retain their debug ID. Existing GitHub users moving to the public stable app will install a separate application.

Run Check Prime F-Droid build on the feature branch first. It does not publish.
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
The tag filter and UpdateCheckData use the documented F-Droid Tags syntax. Validate with fdroidserver before changing AutoUpdateMode from None to Version. No scanner exceptions are proposed until binary provenance is established.

## Remaining blockers
- Prepare GPL-3.0-or-later distribution of the combined launcher, subject to completing the source and asset license inventory. Retain Apache-2.0 and other upstream notices, and confirm rights to Prime-specific contributions. GPLv3 text is included in prime/assets/licenses/GPL-3.0.txt. Update the root license declaration, README, in-app legal notices and draft F-Droid License field together after review.
- Verify all prebuilt JARs, including AOSP SystemUI-statsd and WindowManager-Shell packaged dependencies, against source and F-Droid policy. See prebuilts/libs/README.md. Do not remove required JARs.
- Inspect resolved runtime dependency report, including JitPack artifacts, and scan the final APK for non-free libraries.
- Test Google Fonts, optional feed and web suggestions on a device without Google Play Services; inspect defaults and network requests.
- Fastlane currently contains English (en-US) and French (fr-FR) Prime Launcher descriptions only. The placeholder device-frame.jpg was removed. The maintainer will supply final screenshots at the end of preparation. Verify images/icon.png against current branding. App resource translations are a separate audit.
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
