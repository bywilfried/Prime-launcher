# F-Droid binary and license audit

Static review of root and secondary Gradle build files.

## Required local JARs

| JAR | Usage |
|---|---|
| framework-10.jar through framework-15.jar | Compile-only Android compatibility modules VQ, VR, VS, VT, VU, VV respectively |
| framework-16.jar | Compile-only root and SystemUI/compatibility modules |
| SystemUI-core-16.jar | Compile-only |
| SystemUI-statsd-16.jar | Root implementation and compile-only modules |
| WindowManager-Shell-16.jar | Root implementation and compile-only modules |

The existing prebuilts/libs/README.md documents AOSP module commands and Android 16 source tag android-16.0.0_r3. Exact source revisions for framework-10.jar and framework-13.jar are missing. The two implementation JARs require particular scrutiny because their classes may be packaged in the APK.

Other historical JARs (including libGoogleFeed.jar) have no explicit references in the reviewed Gradle build files. This does not establish absence of indirect usage. Do not delete until all build scripts and source references have been checked.

## Licenses

- Preserve LICENSE.txt (Apache-2.0) and upstream notices.
- Calculator source includes a separate MIT license, copyright 2018 Keelar.
- GoogleFontsListing.kt has a GPL-3.0-or-later header. Audit all source headers and upstream licensing before declaring the combined application license; include applicable full license texts.
- Verify font and image asset licenses and dependency license reports.
- Verify exact AOSP binary provenance and source rebuild procedure; do not claim reproducibility without comparing outputs.

## Validation status

Check Prime F-Droid build #1 compiled successfully according to the maintainer. The runtime dependency report showed no explicit GMS or Firebase modules in the prior review. Local JAR contents, final APK contents, network behavior, device behavior without GMS, and fdroidserver validation remain unverified. No binaries removed or license terms changed by this audit.

## Privacy changes pending build validation

- Web suggestions default to disabled in both Prime Search and drawer, including the legacy preference.
- Failed requests no longer fall back to a different provider in either surface.
- Prime Search web shortcut icons use installed application icons or local monograms instead of Google favicon requests.
- Google downloadable fonts use the system default when the expected provider is absent.
- Feed provider preference defaults to empty.
- Existing explicit user settings remain unchanged.

## Test before merging

Run Build Prime Dev APK on feature/fdroid-support, then Check Prime F-Droid build. Test clean installation and restored settings, both search surfaces with suggestions disabled/enabled, unavailable selected provider, web shortcuts, fonts without GMS, and WebApps. Inspect network traffic. The source edits have not been compiled.

## Outstanding release blockers

Full source and asset license inventory, exact binary provenance and F-Droid build recipe validation, native library source verification, full English/French and inherited locale audit, and actual fdroidserver scanner/build remain outstanding. Do not treat a successful Dev build as F-Droid approval.
