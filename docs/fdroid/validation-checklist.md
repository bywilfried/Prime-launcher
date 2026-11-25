# Grouped validation — first F-Droid release

## Before manual CI
- [ ] Review branch HEAD and all changes since prior successful run.
- [ ] Resolve distribution rights and license notices; record outstanding reviewer questions.
- [ ] Confirm version.properties, SDK availability and metadata.
- [ ] Approve a single manual Check Prime F-Droid build run.

## CI (one grouped run)
- [ ] Dev: assemblePrimeWithQuickstepGithubDebug.
- [ ] Stable: assemblePrimeWithQuickstepGithubRelease -PprimeStable=true.
- [ ] F-Droid: assemblePrimeWithQuickstepFdroidRelease -PprimeStable=true.
- [ ] Inspect strict source scanner JSON; record each remaining JAR error, do not suppress.
- [ ] Inspect APK scanner, runtime dependencies, native provenance, notices and packaged artwork.
- [ ] Verify unsigned production APK and signed test APK, application IDs and version codes.

## Device tests (record device, Android version, variant, result)
- [ ] Default launcher selection, home screen, drawer, categories and shortcuts.
- [ ] Prime Search: local results, calculator, permissions, optional web suggestions and selected provider.
- [ ] Widgets, gestures, backup/restore, offline behavior.
- [ ] Clean install with both favicon settings off: no favicon requests; monogram/local fallback.
- [ ] Enable each surface separately: no query text in favicon requests; cache persistence, offline reuse, refresh and failure fallback.
- [ ] No-GMS device: fonts, optional feed, web suggestions.
- [ ] FR/EN UI, fallback, truncation; Android compatibility on available devices.
- [ ] Recover prior functional vigilance note from maintainer if unavailable.

## Final gate
- [ ] Validate privacy notice and AntiFeatures, icon and real screenshots.
- [ ] Validate actual fdroiddata metadata with lint, checkupdates and build.
- [ ] Confirm legal clearance, signing and reproducibility status.
- [ ] Maintainer approves final version, merge, stable commit and tag.
- [ ] Submit RFP only with explicit approval; no automatic publishing.
