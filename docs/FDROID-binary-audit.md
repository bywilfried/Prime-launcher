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

Check Prime F-Droid build #1 compiled successfully according to the maintainer. The runtime dependency report showed no explicit GMS or Firebase modules in the prior review. A preliminary APK inventory found five native library families (androidx.graphics.path, datastore_shared_counter, graphics-core, hoko_blur and nrb) across four ABIs. No explicit proprietary SDK was identified in that preliminary inspection. Exact native source provenance, local JAR contents, network behavior, device behavior without GMS, and fdroidserver validation remain unverified. No binaries removed or license terms changed by this audit.

## Privacy changes pending build validation

- Web suggestions default to disabled in both Prime Search and drawer, including the legacy preference.
- Failed requests no longer fall back to a different provider in either surface.
- Web shortcuts use installed icons, bundled resources or local monograms. Optional first-party favicon retrieval is disabled by default per surface; persistent shared storage is excluded from backup. When enabled, cached web icons take priority and refresh after 30 days on use. No query text is transmitted. Direct HTTPS /favicon.ico requests do not follow redirects; unsupported formats or missing endpoints retain the fallback. Failed attempts are throttled for one hour.
- Google downloadable fonts use the system default when the expected provider is absent.
- Feed provider preference defaults to empty.
- Existing explicit user settings remain unchanged.

## Test before merging

Run Build Prime Dev APK on feature/fdroid-support, then Check Prime F-Droid build. Test clean installation and restored settings, both search surfaces with suggestions disabled/enabled, unavailable selected provider, web shortcuts, fonts without GMS, and WebApps. Inspect network traffic. The maintainer subsequently confirmed a successful Dev build and initial functional tests of the privacy changes. This predates the Gradle variant rename; the renamed Dev and F-Droid variants still require build validation.

## Outstanding release blockers

Full source and asset license inventory, exact binary provenance and F-Droid build recipe validation, native library source verification, full English/French and inherited locale audit, and actual fdroidserver scanner/build remain outstanding. Do not treat a successful Dev build as F-Droid approval.

## Web icon validation checklist

- Verify both settings appear above the source list and are independently off on clean install.
- Verify no favicon requests with both settings off, and no search text in favicon requests when enabled.
- Verify cached icons survive restart, offline operation and app update; verify stale cache survives refresh failure.
- Exercise all 28 engines; direct favicon endpoints and Android image decoder support have not been verified.
- Confirm drawer rendering actually uses the shared overlay path; check compact and expanded drawer results separately.
- Audit bundled logo rights separately; downloading does not establish trademark permission.

## Maintainer decision: historical binaries
Retain all existing JARs, including historical/reference files, pending fdroidserver scanner results. Resolve any scanner findings individually; do not add blanket scanner exclusions. The presence of a JAR in the repository does not establish that it is packaged in the APK.

## Bundled font
The repository contains prime/res/font/googlesansflex_variable.ttf. Verify the exact upstream font revision, embedded copyright and license metadata, and redistribution terms; include the applicable license and notices before publication. Do not infer the license from the filename.

### Upstream license findings
Google Sans Flex upstream switched to OFL-1.1 in release 3.007 (2025-11-07). Official sources: https://github.com/googlefonts/googlesans-flex and https://github.com/google/fonts/blob/main/ofl/googlesansflex/OFL.txt . The bundled binary's revision and license are not yet established. Retain pending verification; if it predates the open release, use a verified OFL release after visual regression testing. Preserve license and copyright notices in the distributed app.

### Native dependency candidates
The version catalog declares androidx.graphics:graphics-core:1.0.4, androidx.graphics:graphics-path:1.1.0, io.github.hokofly:hoko-blur:1.5.5 and libsu core/service 6.0.0. These are investigation leads, not proof of the origin of every APK native library. Trace the remaining datastore_shared_counter and nrb binaries to resolved artifacts and verify exact source revisions and licenses.

## Source license verification
The calculator's original MIT license (Copyright 2018 Keelar) has been copied verbatim to prime/assets/licenses/calculator-MIT.txt. Verify inclusion in the next APK and accessibility from third-party notices. GoogleFontsListing.kt explicitly states GPL-3.0-or-later; retain the header and investigate the upstream licensing history and combined distribution obligations before declaring an application-wide license. The README already credits Launcher3 and Lawnchair. No relicensing has been performed.

## Verified bundled font and latest Dev validation
The earlier F-Droid APK contains res/P0.ttf, whose Git blob SHA-1 (83f272d2271c17ade8b4cda6363d7dfc777cd3eb) matches the current repository font exactly. OpenType name records identify Google Sans Flex version 3.007;[58cd9cb9b], copyright 2015 Google LLC, and SIL Open Font License 1.1. The upstream OFL notice is now included in prime/assets/licenses/google-sans-flex-OFL.txt. Verify inclusion in the next APK and user access to notices. No font replacement needed on license grounds. Maintainer reports successful Dev compilation after variant rename and FR/EN externalization; functional regression and current F-Droid build remain pending.

## Native source follow-up
The current version catalog also declares androidx.datastore:datastore-preferences:1.2.1. AndroidX (https://github.com/androidx/androidx), HokoBlur (https://github.com/HokoFly/HokoBlur) and libsu (https://github.com/topjohnwu/libsu) upstream repository metadata identify Apache-2.0. HokoBlur master hoko-blur/src/main/jni/CMakeLists.txt explicitly builds the shared target hoko_blur from local C++ sources and links Android log and jnigraphics. This establishes a source lead for libhoko_blur.so, not byte-level provenance for release 1.5.5. Exact release tags and artifact correspondence remain unverified. libdatastore_shared_counter.so is a candidate from AndroidX DataStore; libnrb.so remains unattributed. Do not infer its origin from libsu's presence. Obtain resolved AARs and compare their JNI entries against the current F-Droid APK before concluding the native audit. No scanner exceptions added.

## Resolved native provenance (current F-Droid check)
The supplied prime-fdroid-audit.zip contains native-dependencies.json, native-provenance.json, runtime-dependencies.txt and the unsigned production APK. All 20 APK .so entries (five libraries across arm64-v8a, armeabi-v7a, x86 and x86_64) have exact SHA-256 matches to resolved AAR entries; APK hashes independently recomputed. graphics-path 1.1.0, graphics-core 1.0.4, datastore-core-android 1.2.1, hoko-blur 1.5.5, and com.github.ChickenHook:RestrictionBypass:c59bd6a680 (libnrb.so). RestrictionBypass upstream commit c59bd6a680ead0a6c35e8bd6e5146a1fc9abb561 exists and contains LICENSE, restrictionbypass/CMakeLists.txt and restrictionbypass/src/main/cpp/RestrictionBypass.cpp. Repository license metadata reports Apache-2.0; exact revision license and build correspondence still require review. https://github.com/ChickenHook/RestrictionBypass/tree/c59bd6a680ead0a6c35e8bd6e5146a1fc9abb561 . Both calculator-MIT.txt and google-sans-flex-OFL.txt are present in APK assets/licenses. This validates artifact-to-APK provenance, not source reproducibility or F-Droid acceptance. Signed test APK status not established by this archive.

## Source license follow-up
RestrictionBypass exact revision LICENSE is Apache 2.0 and restrictionbypass/CMakeLists.txt builds shared target nrb from RestrictionBypass.cpp. Source-to-binary reproducibility remains unverified.
GoogleFontsListing.kt explicitly states GPL-3.0-or-later and is integrated with launcher preferences and Dagger. Apache-2.0-only distribution of a combined GPL-covered work is not justified by compatibility alone. Preserve upstream notices; investigate original history and permission, or independently replace the functionality, or assess GPL-compatible distribution with maintainer approval. Do not silently relicense. References: https://www.apache.org/licenses/GPL-compatibility and https://www.gnu.org/licenses/gpl-faq.html . Release gate pending resolution. Asset redistribution rights remain pending.

## Differential audit against Lawnchair 16-dev (2026-11-25)
Maintainer direction: retain Apache-2.0 pending resolution of inherited licensing; no relicensing. Compared current upstream gradle/libs.versions.toml: 17 changed Prime entries, all version differences, no new Prime keys in the comparison. Root build.gradle and settings.gradle dependency/repository declaration lines have no Prime-only matches under the textual filter. This is not a full transitive or historical fork comparison. Upstream lawnchair/src/app/lawnchair/font/googlefonts/GoogleFontsListing.kt contains the same GPL-3.0-or-later header; inherited issue remains unresolved. Compared drawable XML: ic_bing, ic_brave, ic_duckduckgo, ic_github, ic_wikipedia identical to current upstream; ic_youtube differs; ic_prime_fdroid has CC-BY-SA-3.0 attribution and no same-path upstream counterpart. Inheritance does not establish artwork permission; YouTube and other logos require further provenance. Full source-license-audit.json pending workflow execution. Retain JARs pending individual scanner review. Official policy https://f-droid.org/docs/Inclusion_Policy/ requires valid asset rights and free dependencies; no release clearance claimed.


### Upstream GPL header comparison (2026-11-25)

The source inventory identifies 18 files with GPL notices: two Flowerpot Python scripts, seven Flowerpot Kotlin files, two font Kotlin files, three icon shape Kotlin files and four XML resources. Their corresponding upstream paths on Lawnchair 16-dev retain GPL-3.0-or-later notices (prime/ maps to lawnchair/). The upstream root LICENSE.txt declares Apache-2.0. This is an upstream licensing ambiguity, not evidence of a new Prime dependency or of permission to relicense. Preserve notices and request upstream clarification of relicensing scope and contributor authorization before declaring Apache-only distribution compliant. The two Python scripts are separate tooling candidates; runtime packaging of the remaining files requires verification. No license change or scanner exemption is authorized.

Inventory skipped paths: modules/widgetpicker/tests/multivalentTestsForDevice, platform_frameworks_libs_systemui, quickstep/tests/multivalentTestsForDevice, tests/multivalentTestsForDevice. Verify their Git types and relevant contents separately.


### Dependency declaration comparison (2026-11-25)

Compared Prime feature/fdroid-support against Lawnchair 16-dev: gradle/libs.versions.toml, root build.gradle and settings.gradle. No additional library aliases, root implementation dependencies or included Gradle modules were identified in Prime. Version differences exist (including Kotlin, AGP, DataStore, Compose, OkHttp). This is a declaration comparison, not a complete resolved transitive dependency or submodule audit. Both roots declare SystemUI-statsd-16.jar and WindowManager-Shell-16.jar as implementation dependencies; framework-16.jar is compileOnly, and SystemUI-core-16.jar is referenced in compileOnlyCommonJars. Do not classify all historical JARs as compile-only. Verify exact source revision and build recipe for each bundled JAR before proposing individual scanner handling. Preserve existing files pending verification. F-Droid policy: https://f-droid.org/en/docs/Inclusion_Policy/ .


### Android 16 prebuilt JAR provenance (2026-11-25)

Prime and upstream Lawnchair 16-dev have identical prebuilts/libs/README.md documentation and identical Git blob IDs for all four Android 16 JARs:

| JAR | Git blob SHA-1 | AOSP build target |
|---|---|---|
| framework-16.jar | 4b73c3a034a5d6f1c5a85adae211792582129c4f | m framework |
| SystemUI-core-16.jar | 141c1b24923990e961b9868bbbed7aac44945d49 | m SystemUI-core |
| SystemUI-statsd-16.jar | 9cd41821a7646959c2b7319ccadbcc9d820f3001 | m SystemUI-statsd |
| WindowManager-Shell-16.jar | 6e90a87b280e8b2532ecae3e6c08101b70eaabb7 | m WindowManager-Shell |

Documented source tag: android-16.0.0_r3; lunch target: aosp_cf_x86_64_only_phone-aosp_current-userdebug. Source: https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r3/ . Exact equality with upstream Git objects is established; independent AOSP source-to-binary rebuild and F-Droid scanner acceptance are not established. Historical JARs outside these four remain outside this comparison. No deletions or scanner exemptions.


### Historical framework JAR comparison

All seven additional documented framework JARs match upstream Lawnchair 16-dev Git blob IDs: framework-15.jar 265de0f75c3cd7082e1dd7e58a2a361e67b0aed9; framework-14.jar 51975822b108b29285c2b9b192d5b9e210b369ae; framework-13.jar 0e9085a68705bb508ca62507ced7ac66769c9c35; framework-12l.jar 24d28c71f7b943b9aec2a9b22b0df18b7a15a7a2; framework-12.jar 2cb001a9224372de76c5345aeba53548b2397b3f; framework-11.jar f2881766a9a7b1196c626b049c77b1c4e39ff233; framework-10.jar 592572c90dfa1660afe7996790c6d4deefcd1fb4. Together with the four Android 16 JARs, 11 documented JARs match upstream. README lacks source revisions for Android 10 and 13. Independent rebuild, other undocumented JARs, and scanner acceptance remain unverified. Preserve all historical files.


### Complete tracked JAR comparison and special paths

Recursive Git tree comparison (not truncated) established that all 26 tracked JARs, including 25 prebuilts and the Gradle wrapper, match Lawnchair 16-dev blob IDs. Three skipped test paths are identical symbolic links upstream; platform_frameworks_libs_systemui is a gitlink to 7d9e92bda50359dc66a530248e15f9748343524e (upstream 233c33f3934cb364d997f42d989785d51d09530a). Its recursive tree has 439 entries and no named LICENSE/NOTICE or JAR/AAR/SO files; source header audit remains open.

### Web icon distribution policy

Maintainer confirms optional runtime favicon caching for icons without verified redistribution permission. Preserve offline initial fallback, opt-in per surface, direct HTTPS fetch without search terms, private noBackupFilesDir cache, and online icon priority when enabled. Do not bundle unverified artwork; runtime fetching does not override third-party terms. PrimeWebIcons.kt currently maps eleven local resources; each requires a license/permission check before inclusion. The cache is not proof of permission for those assets. See https://f-droid.org/docs/Inclusion_Policy/ . No functional modification or license change in this commit.


## Remaining prebuilt review request

After commit 24d40c0, CI run https://github.com/bywilfried/Prime-launcher/actions/runs/45578003085 validated Dev, Stable and F-Droid compilation; strict source scan reports ten remaining JARs. Workflow is manual-only again. Request F-Droid reviewer guidance before proposing scanignore: seven framework JARs (10–16) and SystemUI-core-16 are compilation inputs; SystemUI-statsd-16 and WindowManager-Shell-16 are implementation inputs and need separate scrutiny. All ten match upstream Lawnchair blobs recorded above. Android 10 and 13 source revisions remain unknown; other historical branch references must be pinned before claiming reproducibility. Android 16 recipe is documented above. Prefer source build or reviewer-approved trusted distribution; no blanket scanignore, no deletion of required inputs. See https://f-droid.org/docs/Building_Applications/ and https://f-droid.org/docs/Build_Metadata_Reference/. This is evidence for a review request, not approval or a validated F-Droid build recipe. License and artwork gates remain open.


### Dependency and Fastlane follow-up (2026-11-25)

Audit artifact: run 45578003085, commit f322603b4bbcc5ac9f151e4d77c5c5d8af4d0210. APK contains assets/licenses.json, license.html, GPL-3.0, calculator MIT, Google Sans Flex OFL, META-INF/NOTICE.md and AndroidX license files. No separate GPL-2.0 text identified in APK filenames; complete redistribution notices not established. FuzzyWuzzy 1.4.0 POM declares GPL 2; GPL-2.0-only versus or-later remains unresolved, no Apache-only clearance. Libsu 6.0.0 upstream exact tag LICENSE is Apache-2.0; RestrictionBypass exact revision previously reviewed. Successful GitHub resolution is not proof of F-Droid builder availability, source reproducibility or scanner acceptance. Keep existing dependencies and root license pending resolution. Fastlane descriptions and version-code changelogs prepared; screenshots and final release approval pending.

## Notice inventory — 2026-11-25

Run [45629838150](https://github.com/bywilfried/Prime-launcher/actions/runs/45629838150), audit artifact 15031720887: 173 artifact records, 119 notice occurrences, 14 distinct SHA-256 values (including APK-origin records). Thirteen hashes match license/notice entries in the unsigned APK. The remaining hash `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` is an Apache-2.0 license text with appendix, collected from Guava 33.7.2-jre, FailureAccess 1.0.3 and Jakarta Inject API 2.0.1. Apache-2.0 texts are already bundled; a non-identical hash alone does not establish a missing legal notice. The Jakarta Inject notice itself matches an APK entry. This inventory does not prove complete upstream attribution or license compatibility.

All three variants compiled. Source scanner exit file is 0 but its JSON reports the ten known JAR errors; the explicit JSON gate fails. APK scanner exit is 0. No scanner exemptions added.
