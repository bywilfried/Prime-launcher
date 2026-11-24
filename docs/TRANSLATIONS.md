# Prime Launcher translations

English in prime/res/values is the canonical source. Maintain French in parallel. Preserve all inherited language and regional configurations; do not replace valid existing translations with machine translations.

## Adding features
- Externalize user-facing text into Android resources, including accessibility labels, errors, plurals and arrays.
- Add English and French translations together.
- Preserve resource names, formatting arguments, markup and non-translatable identifiers.
- Translate new Prime strings for other locales in reviewed batches. Do not claim unreviewed translations are complete.
- Use Android fallback for missing translations; do not fill missing locales with duplicate English strings just to improve coverage.

## Audit
Run from repository root:
```sh
python3 scripts/audit_translations.py --json translation-audit.json
```
The GitHub Actions Audit translations workflow produces the same report. Direct coverage is not effective Android fallback coverage: regional variants may intentionally contain only overrides. Extra keys and formatting warnings require human review. The initial script does not validate plural/array placeholders, translation meaning, hardcoded Kotlin/Java text, or resources outside the selected res directory.

## Community translation
Evaluate Weblate or Crowdin after the English/French source audit. Connect only with maintainer approval; use reviewed pull requests and preserve upstream attribution. No external translation service is configured yet.

## Before release
Audit hardcoded strings, complete English/French, review inherited translations and new Prime translations, validate formatting and RTL on device, and build Dev and F-Droid variants. Fastlane metadata is maintained separately; final screenshots are supplied by the maintainer.
