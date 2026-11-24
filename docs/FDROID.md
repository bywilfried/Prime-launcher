# Prime Launcher — F-Droid preparation

## Stable releases

Set VERSION_NAME and VERSION_CODE in version.properties before merging to main.
The proposed initial code 11000000 exceeds the maximum of the existing 0.9 RC formula (10999998). Verify against all previously distributed APKs and increment for every stable release. Existing RC APKs share the public application ID and may have a lower code than stable; Android will reject a downgrade. Dev APKs have a separate debug ID. Consider a separate RC application ID in a future change.

Run Check Prime F-Droid build on the feature branch first. It does not publish.
After validation and merge to main, manually run Publish Prime Stable. It creates a GitHub release and vX.Y.Z tag only after compilation and APK version checks.

F-Droid build command:
```sh
git submodule update --init --recursive
./gradlew -PprimeStable=true assembleLawnWithQuickstepGithubRelease
```
Java 21; Android SDK 37 minor API 2 and build tools 37.0.0 as required by current Gradle configuration. Confirm availability in F-Droid build infrastructure.

Suggested fdroiddata settings (not submitted or validated):
```yaml
RepoType: git
Repo: https://github.com/bywilfried/Prime-launcher.git
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
AutoUpdateMode: Version
```
The build recipe must enable submodules, select lawnWithQuickstepGithub and pass -PprimeStable=true. Update detection must read version.properties (or use a validated custom update-check configuration). Do not enable automatic updates until checkupdates and build succeed.

## Remaining blockers
- Audit GPL-3.0-or-later source headers versus LICENSE.txt; retain copyright notices and provide full applicable license texts. Do not relicense upstream code.
- Verify all prebuilt JARs, including AOSP SystemUI-statsd and WindowManager-Shell packaged dependencies, against source and F-Droid policy. See prebuilts/libs/README.md. Do not remove required JARs.
- Inspect resolved runtime dependency report, including JitPack artifacts, and scan the final APK for non-free libraries.
- Test Google Fonts, optional feed and web suggestions on a device without Google Play Services; inspect defaults and network requests.
- Replace inherited Fastlane graphics and other localized Lawnchair descriptions.
- Run fdroidserver lint, checkupdates and build with actual fdroiddata metadata.
- Verify signing and reproducibility separately. GitHub and F-Droid APKs normally have different signing certificates.
- No F-Droid submission or stable release has been made by this preparation.

Official policy: https://f-droid.org/docs/Inclusion_Policy/
Metadata: https://f-droid.org/docs/Build_Metadata_Reference/
