<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:050505,50:ef233c,100:ff6b35&height=240&section=header&text=CATSMOKER&fontSize=64&fontColor=ffffff&fontAlignY=35&desc=Android%20Gaming%20Toolkit&descAlignY=58&animation=twinkling&stroke=ff6b35&strokeWidth=2" alt="CatSmoker 标语" width="100%" />
</p>

<div align="center">

**面向 Android 的游戏性能调优、遥测监测与精准操控工具。**

[![Stars](https://img.shields.io/github/stars/catsmoker/catsmoker-app?style=flat-square&logo=github)](https://github.com/catsmoker/catsmoker-app/stargazers)
[![Forks](https://img.shields.io/github/forks/catsmoker/catsmoker-app?style=flat-square&logo=github)](https://github.com/catsmoker/catsmoker-app/network/members)
[![Issues](https://img.shields.io/github/issues/catsmoker/catsmoker-app?style=flat-square&logo=github)](https://github.com/catsmoker/catsmoker-app/issues)
[![Last Commit](https://img.shields.io/github/last-commit/catsmoker/catsmoker-app?style=flat-square&logo=git)](https://github.com/catsmoker/catsmoker-app/commits/main)
[![Release](https://img.shields.io/github/v/release/catsmoker/catsmoker-app?style=flat-square&label=版本)](https://github.com/catsmoker/catsmoker-app/releases)
[![License](https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-0ea5e9?style=flat-square&logo=creativecommons)](LICENSE)

[![Android](https://img.shields.io/badge/Android-8.1%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose)](https://developer.android.com/compose)
[![Gradle](https://img.shields.io/badge/构建-Gradle-02303A?style=flat-square&logo=gradle)](https://gradle.org/)
[![Shizuku](https://img.shields.io/badge/访问-Shizuku-2E7D32?style=flat-square)](https://shizuku.rikka.app/)
[![LSPosed](https://img.shields.io/badge/模块-LSPosed-8E44AD?style=flat-square)](https://github.com/LSPosed/LSPosed)

[![Discord](https://img.shields.io/badge/Discord-社区-5865F2?style=flat-square&logo=discord)](https://discord.com/invite/HQC5BwcXtS)
[![Telegram](https://img.shields.io/badge/Telegram-群组-26A5E4?style=flat-square&logo=telegram)](https://t.me/CATSM0KER)
[![PayPal](https://img.shields.io/badge/支持-PayPal-00457C?style=flat-square&logo=paypal)](https://www.paypal.me/catsmoker)

[![Typing SVG](https://readme-typing-svg.demolab.com?font=Fira+Code&size=20&duration=2800&pause=900&color=0EA5E9&center=true&vCenter=true&width=620&lines=优化你的游戏。掌控你的设备。;为认真玩游戏的你而生的 Android 工具。)](https://github.com/catsmoker/catsmoker-app)

<a href="https://github.com/catsmoker/catsmoker-app/releases">
  <img src="https://img.shields.io/badge/⬇%20下载完整版-ef233c?style=for-the-badge&logo=android&logoColor=white" alt="下载完整版"/>
</a>
<a href="https://play.google.com/apps/testing/com.catsmoker.app">
  <img src="https://img.shields.io/badge/GOOGLE%20PLAY-4285F4?style=for-the-badge&logo=googleplay&logoColor=white" alt="Google Play"/>
</a>

[报告问题](https://github.com/catsmoker/catsmoker-app/issues) · [官网](https://catsmoker.vercel.app)

[English](README.md) · [العربية](README.ar.md) · [Español](README.es.md) · **中文** · [日本語](README.ja.md) · [Português](README.pt-BR.md)

</div>

---

## 简介

**CatSmoker** 是一款面向已支持游戏的 Android 优化工具箱，旨在优化兼容游戏、解锁额外配置选项并监测设备性能。

## 功能特性

| 类别 | 功能 |
|---|---|
| **核心优化** | 设备伪装、FPS 配置、分辨率与 DPI 控制 |
| **游戏工具** | 实时 FPS 悬浮窗、CPU/内存监测、温度与功耗遥测 |
| **个性化** | 准星悬浮窗、游戏专用 DNS、游戏配置编辑器 |
| **系统级** | Root/Shizuku 集成、诊断工具、系统日志 |
| **游戏库** | 发现、启动并管理已安装游戏 |

## 下载与安装

请根据需求 **二选一** 版本。两个版本源自同一项目，但提供不同的功能与安装要求。

<details>
<summary><strong>GitHub 完整版 — 功能全版本</strong></summary>

**适合想要所有可用功能的用户。**

<a href="https://github.com/catsmoker/catsmoker-app/releases">
  <img src="https://img.shields.io/badge/⬇%20下载完整版-ef233c?style=for-the-badge&logo=github&logoColor=white" alt="下载完整版"/>
</a>

**包含内容:**
- 所有可用的游戏与优化功能。
- 设备伪装与 LSPosed/Vector 集成。
- Magisk 模块支持。
- Shizuku 与手动文件访问工作流。
- GitHub 版本自动更新器。

### 安装方法

**方案 A — 已 Root 设备 (LSPosed / Vector)**

前置条件: Magisk、APatch 或 KernelSU。

1. 仅安装并启用 **一种** Zygisk 实现:
   - [ZygiskNext](https://github.com/LSPosed/ZygiskNext/releases)
   - [NeoZygisk](https://github.com/JingMatrix/NeoZygisk/releases)
   - [ReZygisk](https://github.com/PerformanC/ReZygisk)
2. 仅安装 **一种** 框架模块:
   - **推荐:** [Vector](https://github.com/JingMatrix/Vector/releases)
   - 备选: [LSPosed](https://github.com/LSPosed/LSPosed)
3. 重启设备。
4. 安装 CatSmoker 并授予 Root 权限。
5. 打开 Vector 或 LSPosed 管理器，启用 CatSmoker 模块，勾选目标游戏。
6. 强制停止选中的游戏并重新启动。

**重要:** 仅使用一种 Zygisk 实现与一种框架模块。

**方案 B — 免 Root 设备 (Shizuku)**

1. 安装 [Shizuku Next](https://github.com/ShizukuNext/ShizukuNext) 或 [官方 Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)。
2. 通过无线调试或 ADB 启动 Shizuku。
3. 安装 CatSmoker 并授予 Shizuku 权限。
4. 选择游戏，使用可通过 Shizuku 运行的工具。

部分文件操作可能需要通过 SAF 或 ZArchiver 等文件管理器获取额外访问权限。

</details>

<details>
<summary><strong>Google Play 版 — 免 Root 版本</strong></summary>

**适合偏好从 Google Play 安装且无需 Root 的用户。**

<a href="https://play.google.com/apps/testing/com.catsmoker.app">
  <img src="https://img.shields.io/badge/在%20Google%20Play%20获取-4285F4?style=for-the-badge&logo=googleplay&logoColor=white" alt="Google Play 测试版"/>
</a>

**差异:**
- 无需 Root 或 LSPosed。
- 不包含设备伪装、Magisk 模块生成、GitHub 自动更新器。
- 使用 AdMob 而非 Start.io 广告。
- 部分高级功能保留各自的权限或兼容性要求。

### 加入封闭测试

Google Play 版目前通过封闭测试分发。

1. 加入 Google Group: **catsmoker-apps@googlegroups.com**
2. 等待最多 5 分钟以更新访问权限。
3. 打开 [Google Play 测试页面](https://play.google.com/apps/testing/com.catsmoker.app)。
4. 注册测试并从 Google Play 安装 CatSmoker。

</details>

> **不知道选哪个版本?** 想要高级 Root 功能选 GitHub 完整版；偏好标准免 Root 发行版选 Google Play 版。

## 支持的游戏

CatSmoker 支持 50+ 款游戏，包括:

- PUBG Mobile (所有受支持变体)
- 原神 (Genshin Impact)
- 崩坏：星穹铁道
- 鸣潮
- GRID Autosport
- 以及更多……

请通过 [GitHub Issues](https://github.com/catsmoker/catsmoker-app/issues)、[Discord](https://discord.com/invite/HQC5BwcXtS) 或 [Telegram](https://t.me/CATSM0KER) 请求支持新游戏。

## 兼容性

- **最低 Android 版本:** 8.1 (API 27)。
- **Root 环境:** Magisk、APatch 或 KernelSU，需受支持的 Zygisk/框架配置。
- **免 Root 环境:** Shizuku 或受支持的手动访问方式。

已测试设备:

- POCO X3 NFC
- Nothing Phone (2a)
- 小米 Redmi Note 14
- Lenovo Legion Tab
- Realme 9 Pro Plus

## 源码构建

环境要求: Android Studio、JDK 17、Android SDK 及项目配置的 NDK。

```bash
bash ./gradlew :app:assembleFullDebug :app:assemblePlaystoreDebug
```

`main` 分支维护两个 Gradle 变体:

| 变体 | 构建任务 |
|---|---|
| GitHub 完整版 | `:app:assembleFullRelease` |
| Google Play 版 | `:app:assemblePlaystoreRelease` |

详见 [变体工作流](docs/FLAVOR_WORKFLOW.md)。

## 隐私

- **本地优先:** 设置、配置文件、备份与日志均保留在设备上。
- 无账号体系，无项目自营后端。
- 诊断日志仅在用户主动分享时离开设备；不录制音频、视频或屏幕。
- 更新检查使用公开的 GitHub Releases API。
- 完整版: Start.io 广告。Google Play 版: AdMob 广告。

完整隐私政策与条款可在应用内 **关于 → 法律** 中查看。

## 免责声明

修改游戏文件或伪装设备身份可能违反特定游戏的服务条款。请负责任地使用 CatSmoker。开发者不对账号封禁、硬件故障或启动循环承担责任。

## 许可证

基于 [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License](https://creativecommons.org/licenses/by-nc-sa/4.0/) 发布。

[![CC BY-NC-SA](https://mirrors.creativecommons.org/presskit/buttons/88x31/svg/by-nc-sa.svg)](LICENSE)

## 支持项目

- [通过 PayPal 捐赠](https://www.paypal.me/catsmoker)
- [GitHub Issues](https://github.com/catsmoker/catsmoker-app/issues)
- [Discord 社区](https://discord.com/invite/HQC5BwcXtS)
- [Telegram 群组](https://t.me/CATSM0KER)

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