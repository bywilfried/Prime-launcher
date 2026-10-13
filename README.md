<div align="center">

# Prime Launcher

<img src="docs/prime/prime-logo.svg" alt="Prime Launcher logo" width="144" height="144" />

### Your home screen. Your style.

**An open-source Android launcher built for personalisation, control, and a home screen that feels like yours.**

[Explore the source](https://github.com/bywilfried/Prime-launcher/tree/Prime-identity) · [Releases](https://github.com/bywilfried/Prime-launcher/releases) · [Report an issue](https://github.com/bywilfried/Prime-launcher/issues)

</div>

## Meet Prime Launcher

Prime Launcher is a customisable Android home screen experience, built on **Launcher3 (AOSP)** and the development branch of **Lawnchair 16**.

Prime takes that foundation in its own direction: a distinctive visual identity, detailed appearance controls, category-based app organisation, and more freedom to fine-tune everyday interactions. From the colour of a category tab to the spacing around a widget, the aim is to make your launcher feel deliberately yours.

## Features

### 🎨 Make every colour yours

Choose the colours of your home screen, dock, app drawer, folders, and interface elements. Prime supports theme-level customisation as well as **individual app drawer category colours**.

Automatic text colour options help maintain readability:
- **Automatic black/white** chooses whichever of black or white contrasts best with the background.
- **Adaptive contrast** derives a readable text colour from the background's hue, targeting a 7:1 contrast ratio where achievable.

### 🗂️ An app drawer that fits your routine

Organise apps into **categories** and move between them using customisable tabs. Give categories their own colours and distinguish active and inactive tab states.

### 🧩 Fine-tune widgets

Adjust widget margins globally or per widget, with separate controls for each side. Make room for dense layouts or give your favourite widgets extra breathing space.

### 📱 Personalise your layout

Refine the appearance of icons, labels, folders, the home screen, dock, and drawer. Prime offers granular sizing and colour options rather than a single fixed look.

### 🛠️ Built with iteration in mind

Prime Launcher is actively developed. Debug builds and diagnostic log export help investigate issues as the project evolves.

## Visual identity

The official Prime Launcher logo is preserved as an editable vector at [`docs/prime/prime-logo.svg`](docs/prime/prime-logo.svg). It features the original roof-and-P motif, including its four square windows, on a dark blue One UI-style background.

### Screenshots

Screenshots of the home screen, category drawer, and customisation settings will be added here as the presentation assets are prepared.

## Get Prime Launcher

Browse [GitHub Releases](https://github.com/bywilfried/Prime-launcher/releases) for published builds. The [Prime-identity branch](https://github.com/bywilfried/Prime-launcher/tree/Prime-identity) contains ongoing identity and feature development.

**Current release series: 0.8.x.** Development builds may contain experimental changes.

## Verify an APK

To verify that an APK is signed with the expected certificate, compare its **SHA-256 signing certificate fingerprint** with the fingerprint published by the project.

**SHA-256 signing certificate fingerprint (provided release APK):**

```text
14:B0:D1:4C:A4:D8:85:DB:55:35:6D:E0:BD:D9:E1:72:91:58:CB:4C:48:B8:F3:7C:BA:2B:FA:D5:D0:E6:A8:0B
```

This fingerprint was extracted from the signing certificate embedded in a supplied Prime Launcher release APK. Compare it with your downloaded APK before installation. The certificate fingerprint alone does not verify the APK's full contents.

You can inspect an APK locally using Android SDK Build Tools:

```bash
apksigner verify --print-certs Prime-Launcher-release.apk
```

## Open-source foundations and credits

Prime Launcher is an independent fork built on the work of [Launcher3 (AOSP)](https://source.android.com/) and [Lawnchair](https://github.com/LawnchairLauncher/lawnchair). Thanks to the maintainers and contributors of those projects.

Third-party licences and acknowledgements remain available in the source tree and in **Settings → About → Third-party licences**.

Prime Launcher is not an official Lawnchair or Google product.

## Contributing and feedback

Found a bug or have an idea? [Open an issue](https://github.com/bywilfried/Prime-launcher/issues) to share feedback or discuss an improvement.
