# Prime Launcher — draft request for packaging (not submitted)

Application: Prime Launcher
Package: com.bywilfried.prime.fdroid
Proposed version: 0.10.0 (11000000)
Source: https://github.com/bywilfried/Prime-launcher
Issues: https://github.com/bywilfried/Prime-launcher/issues
Description: Customizable Android launcher based on AOSP Launcher3 and Lawnchair, with local search, calculator, optional web search and backup.
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

Prior CI: https://github.com/bywilfried/Prime-launcher/actions/runs/45578003085 (three builds successful; source scanner errors remain).
Do not submit as a release-ready request until maintainer approves scope and remaining legal issues are addressed. No final license declaration, stable commit or tag approved.
