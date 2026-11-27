# Prime Launcher — draft request for packaging (not submitted)

Application: Prime Launcher
Package: com.bywilfried.prime.fdroid
Proposed version: 0.10.0 (11000000)
Source: https://github.com/bywilfried/Prime-launcher
Issues: https://github.com/bywilfried/Prime-launcher/issues
Description: Customizable Android launcher based on AOSP Launcher3 and Lawnchair, with home screen and widget customization, gestures, icons, themes, drawer tabs and folders, custom WebApps in the drawer, local search, calculator, optional web search and backup.
Build: Java 21, Android SDK 37 minor API 2, build tools 37.0.0; recursive submodules; ./gradlew -PprimeStable=true assemblePrimeWithQuickstepFdroidRelease.
Metadata draft: com.bywilfried.prime.fdroid.yml.

## Open questions for reviewers
- Ten inherited required AOSP JARs remain: framework-10 through framework-16, SystemUI-core-16 (compile-only), SystemUI-statsd-16 and WindowManager-Shell-16 (implementation). Fifteen unused JARs removed. Strict source scanner reports ten errors; no blanket exceptions requested. Ask for acceptable individual provenance/build handling.
- Native AAR-to-APK SHA-256 correspondence documented; source-to-binary reproducibility not established.
- JitPack dependencies RestrictionBypass and libsu have Apache-2.0 source licenses at identified revisions; F-Droid build availability not tested.
- Root Apache-2.0, 18 inherited GPL-3.0-or-later headers and ambiguous FuzzyWuzzy 1.4.0 GPL-2.0 metadata remain unresolved. Legal clearance is required before distribution; reviewer advice is not a substitute.
- Bundled logo redistribution rights and complete license notices remain open. Optional favicon cache is not proof of artwork permission.
- Optional web services, fonts and feed: confirm network behavior and applicable AntiFeatures.
- SDK availability, signing, reproducibility and fdroidserver validation pending.

Latest reviewed CI: https://github.com/bywilfried/Prime-launcher/actions/runs/45629838150 (three builds successful; APK scanner exit 0; ten source JSON errors remain; strict gate fails). Notice inventory: 173 artifact records, 119 occurrences, 14 distinct texts including APK-origin records; see docs/FDROID-binary-audit.md. This is a request for preliminary reviewer guidance, not a declaration of release readiness.
Maintainer authorizes proceeding with a preliminary RFP and explicitly disclosing unresolved questions rather than delaying reviewer contact. This is not authorization to distribute before legal and inclusion requirements are resolved. No final license declaration, stable commit or tag approved.

## Maintainer decision — FuzzyWuzzy
Retain me.xdrop:fuzzywuzzy:1.4.0 and the existing search algorithm pending F-Droid reviewer feedback. GPL version 2 text is bundled; GPL-2.0-only versus GPL-2.0-or-later remains unconfirmed for the exact release. Request guidance explicitly. No change to root Apache-2.0 or claim of legal compatibility; resolve distribution rights before release. Replacement is deferred, not ruled out.

## Requested next step
Please advise which source-build and licensing evidence is required for inclusion, and which AntiFeatures apply. We will address concrete requirements before distribution. We are not requesting blanket scanner exemptions or assuming acceptance. The proposed build metadata is a draft; the immutable release commit, final license declaration and fdroidserver validation remain pending.
