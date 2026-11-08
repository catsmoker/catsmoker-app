 <p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:050505,50:ef233c,100:ff6b35&height=240&section=header&text=CATSMOKER&fontSize=64&fontColor=ffffff&fontAlignY=35&desc=Android%20Gaming%20Toolkit&descAlignY=58&animation=twinkling&stroke=ff6b35&strokeWidth=2" alt="CatSmoker banner" width="100%" />
</p>

<div align="center">

**Android gaming tools for performance tuning, telemetry, and precision.**

[![Stars](https://img.shields.io/github/stars/catsmoker/catsmoker-app?style=flat-square&logo=github)](https://github.com/catsmoker/catsmoker-app/stargazers)
[![Forks](https://img.shields.io/github/forks/catsmoker/catsmoker-app?style=flat-square&logo=github)](https://github.com/catsmoker/catsmoker-app/network/members)
[![Issues](https://img.shields.io/github/issues/catsmoker/catsmoker-app?style=flat-square&logo=github)](https://github.com/catsmoker/catsmoker-app/issues)
[![Last Commit](https://img.shields.io/github/last-commit/catsmoker/catsmoker-app?style=flat-square&logo=git)](https://github.com/catsmoker/catsmoker-app/commits/main)
[![Release](https://img.shields.io/github/v/release/catsmoker/catsmoker-app?style=flat-square&label=release)](https://github.com/catsmoker/catsmoker-app/releases)
[![License](https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-0ea5e9?style=flat-square&logo=creativecommons)](LICENSE)

[![Android](https://img.shields.io/badge/Android-8.1%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose)](https://developer.android.com/compose)
[![Gradle](https://img.shields.io/badge/Build-Gradle-02303A?style=flat-square&logo=gradle)](https://gradle.org/)
[![Shizuku](https://img.shields.io/badge/Access-Shizuku-2E7D32?style=flat-square)](https://shizuku.rikka.app/)
[![LSPosed](https://img.shields.io/badge/Module-LSPosed-8E44AD?style=flat-square)](https://github.com/LSPosed/LSPosed)

[![Discord](https://img.shields.io/badge/Discord-Community-5865F2?style=flat-square&logo=discord)](https://discord.com/invite/HQC5BwcXtS)
[![Telegram](https://img.shields.io/badge/Telegram-Chat-26A5E4?style=flat-square&logo=telegram)](https://t.me/CATSM0KER)
[![PayPal](https://img.shields.io/badge/Support-PayPal-00457C?style=flat-square&logo=paypal)](https://www.paypal.me/catsmoker)

[![Typing SVG](https://readme-typing-svg.demolab.com?font=Fira+Code&size=20&duration=2800&pause=900&color=0EA5E9&center=true&vCenter=true&width=620&lines=Tune+your+game.+Measure+your+device.;Android+gaming+tools+for+serious+sessions.)](https://github.com/catsmoker/catsmoker-app)

<a href="https://github.com/catsmoker/catsmoker-app/releases">
  <img src="https://img.shields.io/badge/⬇%20DOWNLOAD%20FULL%20VERSION-ef233c?style=for-the-badge&logo=android&logoColor=white" alt="Download Full Version"/>
</a>
<a href="https://play.google.com/apps/testing/com.catsmoker.app">
  <img src="https://img.shields.io/badge/GOOGLE%20PLAY-4285F4?style=for-the-badge&logo=googleplay&logoColor=white" alt="Google Play"/>
</a>

[Report an issue](https://github.com/catsmoker/catsmoker-app/issues) · [Website](https://catsmoker.vercel.app)

[English](README.md) · [العربية](README.ar.md) · [Español](README.es.md) · [中文](README.zh-CN.md) · [日本語](README.ja.md) · [Português](README.pt-BR.md)

</div>

---

## About

**CatSmoker** is an Android gaming toolkit designed to optimize supported games, unlock additional configuration options, and monitor device performance.

## Features

| Category | Features |
|---|---|
| **Optimization** | Device spoofing, FPS configurations, resolution and DPI controls |
| **Game Tools** | Real-time FPS overlay, CPU/RAM monitoring, temperature and power telemetry |
| **Customization** | Crosshair overlay, gaming DNS, game configuration editor |
| **System** | Root/Shizuku integration, diagnostics, system logs |
| **Game Library** | Discover, launch, and manage installed games |

## Download & Installation

Choose **one version** depending on your needs. Both versions share the same project but offer different capabilities and installation requirements.

<details>
<summary><strong>GitHub Full Version — Complete Edition</strong></summary>

**Recommended for users who want all available features.**

<a href="https://github.com/catsmoker/catsmoker-app/releases">
  <img src="https://img.shields.io/badge/⬇%20DOWNLOAD%20FULL%20VERSION-ef233c?style=for-the-badge&logo=github&logoColor=white" alt="Download Full Version"/>
</a>

**Includes:**
- All available gaming and optimization features.
- Device spoofing and LSPosed/Vector integration.
- Magisk module support.
- Shizuku and manual file-access workflows.
- GitHub release updater.

### Installation

**Option A — Rooted devices (LSPosed / Vector)**

Requirements: Magisk, APatch, or KernelSU.

1. Install and enable one Zygisk implementation:
   - [ZygiskNext](https://github.com/LSPosed/ZygiskNext/releases)
   - [NeoZygisk](https://github.com/JingMatrix/NeoZygisk/releases)
   - [ReZygisk](https://github.com/PerformanC/ReZygisk)
2. Install one framework module:
   - **Recommended:** [Vector](https://github.com/JingMatrix/Vector/releases)
   - Alternative: [LSPosed](https://github.com/LSPosed/LSPosed)
3. Reboot your device.
4. Install CatSmoker and grant root access.
5. Open Vector/LSPosed Manager, enable the CatSmoker module, and select your target games.
6. Force-stop the selected games and relaunch them.

**Important:** Use only one Zygisk implementation and one framework module.

**Option B — Non-root devices (Shizuku)**

1. Install [Shizuku Next](https://github.com/ShizukuNext/ShizukuNext) or [official Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api).
2. Start Shizuku using Wireless Debugging or ADB.
3. Install CatSmoker and grant Shizuku permission.
4. Select your game and use the available Shizuku-powered tools.

Some file operations may require additional access through SAF or a file manager such as ZArchiver.

</details>

<details>
<summary><strong>Google Play Version — Non-Root Edition</strong></summary>

**Designed for users who prefer installing through Google Play without root.**

<a href="https://play.google.com/apps/testing/com.catsmoker.app">
  <img src="https://img.shields.io/badge/GET%20IT%20ON%20GOOGLE%20PLAY-4285F4?style=for-the-badge&logo=googleplay&logoColor=white" alt="Google Play Testing"/>
</a>

**Differences:**
- No root or LSPosed requirement.
- Excludes device spoofing, Magisk module generation, and GitHub self-updater.
- Uses AdMob instead of Start.io.
- Some advanced features retain individual permissions or compatibility requirements.

### Join the Closed Test

The Google Play version is currently available through closed testing.

1. Join the Google Group: **catsmoker-apps@googlegroups.com**
2. Wait up to 5 minutes for access to update.
3. Open the [Google Play Testing Page](https://play.google.com/apps/testing/com.catsmoker.app).
4. Enrol and install CatSmoker from Google Play.

</details>

> **Not sure which version to choose?** Choose GitHub Full if you want advanced root features. Choose Google Play if you prefer the standard non-root distribution.

## Supported Games

CatSmoker supports 50+ games, including:

- PUBG Mobile (all supported variants)
- Genshin Impact
- Honkai: Star Rail
- Wuthering Waves
- GRID Autosport
- And many more.

Request a game through [GitHub Issues](https://github.com/catsmoker/catsmoker-app/issues), [Discord](https://discord.com/invite/HQC5BwcXtS), or [Telegram](https://t.me/CATSM0KER).

## Compatibility

- **Minimum Android:** 8.1 (API 27).
- **Root:** Magisk, APatch, or KernelSU with a supported Zygisk/framework setup.
- **Non-root:** Shizuku or supported manual access methods.

Tested devices include:

- POCO X3 NFC
- Nothing Phone (2a)
- Xiaomi Redmi Note 14
- Lenovo Legion Tab
- Realme 9 Pro Plus

## Build From Source

Requirements: Android Studio, JDK 17, Android SDK, and the project's configured NDK.

```bash
bash ./gradlew :app:assembleFullDebug :app:assemblePlaystoreDebug
```

Two Gradle flavors are maintained on the `main` branch:

| Variant | Build task |
|---|---|
| GitHub Full | `:app:assembleFullRelease` |
| Google Play | `:app:assemblePlaystoreRelease` |

See [Flavor Workflow](docs/FLAVOR_WORKFLOW.md).

## Privacy

- Local-first: settings, profiles, backups, and logs stay on your device.
- No account system or project-operated backend.
- Logs and recordings leave your device only when explicitly shared.
- Update checks use the public GitHub Releases API.
- Full version: Start.io ads. Google Play version: AdMob ads.

Read the full Privacy Policy and Terms inside the app under **About → Legal**.

## Disclaimer

Modifying game files or spoofing device identity may violate individual games' Terms of Service. Use CatSmoker responsibly. The developers are not responsible for account bans, hardware issues, or boot loops.

## License

Licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License](https://creativecommons.org/licenses/by-nc-sa/4.0/).

[![CC BY-NC-SA](https://mirrors.creativecommons.org/presskit/buttons/88x31/svg/by-nc-sa.svg)](LICENSE)

## Support the Project

- [Donate via PayPal](https://www.paypal.me/catsmoker)
- [GitHub Issues](https://github.com/catsmoker/catsmoker-app/issues)
- [Discord Community](https://discord.com/invite/HQC5BwcXtS)
- [Telegram Community](https://t.me/CATSM0KER)

---

## Star History

<p align="center">
  <a href="https://www.star-history.com/#catsmoker/catsmoker-app&type=date">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=catsmoker/catsmoker-app&type=date&theme=dark"/>
      <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=catsmoker/catsmoker-app&type=date"/>
      <img alt="CatSmoker Star History" src="https://api.star-history.com/svg?repos=catsmoker/catsmoker-app&type=date" width="100%"/>
    </picture>
  </a>
</p>
