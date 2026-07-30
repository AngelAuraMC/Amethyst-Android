<!-- TODO: Add the Chromite logo/banner here when the final artwork is ready. -->

<div align="center">

# Chromite

**Minecraft: Java Edition on Chromebooks — with a ChromeOS-first direction.**

[![Android CI](https://github.com/Lukiblokck/Chromite/actions/workflows/android.yml/badge.svg)](https://github.com/Lukiblokck/Chromite/actions)
[![License: LGPL v3](https://img.shields.io/badge/License-LGPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-ChromeOS%20%2F%20Android-brightgreen)](#compatibility)
[![Status](https://img.shields.io/badge/status-early%20Chromebook%20adaptation-orange)](#project-status)
[![Discord](https://img.shields.io/discord/1532397881238098100.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/6m9SSErvzU)

</div>

---

## What is Chromite?

Chromite is an open-source launcher for **Minecraft: Java Edition** focused on
**Chromebooks** and the ChromeOS Android environment.

The project starts from the proven Android launcher lineage of Amethyst,
PojavLauncher, and Boardwalk, but its long-term goal is different: Chromite aims
to become a launcher that feels natural on Chromebook hardware instead of a
phone-first experience stretched onto a laptop screen.

Chromite is not affiliated with Mojang, Microsoft, Google, or the Minecraft
brand.

## Why Chromite exists

Minecraft: Java Edition was designed around a desktop setup: a physical
keyboard, pointing device, large display, and windowed multitasking. Chromebooks
can provide much of that environment, but running Java Edition through the
ChromeOS Android subsystem introduces its own challenges.

Chromite exists to focus on those challenges directly:

| Chromebook need | Chromite direction |
| --- | --- |
| Physical keyboard-first play | Make keyboard input a primary experience, not an afterthought. |
| Trackpad and mouse-style navigation | Improve the desktop-like feel of menus and gameplay controls. |
| Larger screens and resizable windows | Move away from phone-oriented layouts over time. |
| ChromeOS Android containers | Account for ARC++/ARCVM behavior, storage, graphics, and memory constraints. |
| Practical performance | Prioritize sensible defaults and performance-oriented configuration for Chromebook hardware. |

## Core goals

Chromite is being shaped around the following principles:

- **ChromeOS-first design** — decisions should make sense on Chromebooks before
  generic Android phones.
- **Desktop-like interaction** — keyboard, trackpad, mouse, and larger screens
  should feel central to the launcher experience.
- **Transparent project status** — unfinished Chromebook-specific work should be
  labeled clearly instead of presented as complete.
- **Respect for upstream work** — historical and license credits are preserved
  without making Chromite's README a rebranded copy of another project.
- **Open development** — issues, testing reports, and pull requests are welcome.

## Features

The current codebase inherits a broad Minecraft launcher feature set from its
upstream projects. Chromebook-specific polish is still in progress.

| Area | Status | Notes |
| --- | --- | --- |
| Minecraft: Java Edition launching | Available | Provided by the existing launcher codebase. Compatibility can vary by version, device, runtime, and renderer. |
| Android / ChromeOS APK build | Available | The app builds as an Android application that can run under ChromeOS Android support. |
| OpenJDK runtime integration | Available | Runtime support is inherited from the upstream Android launcher stack. |
| Modded Minecraft workflows | Available / inherited | Existing Forge/Fabric-related launcher paths may be present; behavior should be tested on Chromebooks. |
| Physical keyboard focus | In Progress | Chromite is intended to prioritize Chromebook keyboard usage. |
| Trackpad and large-screen UX | In Progress | The project direction is desktop-like interaction on Chromebook displays. |
| Chromebook-specific performance profiles | Planned | Device/container-specific tuning is a roadmap item, not a finished feature. |
| ChromeOS-oriented onboarding | Planned | Installation and first-run flows should become clearer for Chromebook users. |

## Compatibility

Chromite targets devices that can run Android applications on ChromeOS.

| Requirement | Notes |
| --- | --- |
| Device | Chromebook with Android app support enabled. |
| Operating environment | ChromeOS Android subsystem, including ARC++ or ARCVM depending on the device and ChromeOS version. |
| Android API | The app currently declares Android minSdk 21 and targetSdk 34 in the Gradle configuration. |
| CPU architecture | Compatibility depends on available native components and runtime artifacts. Chromebook hardware varies between ARM and x86_64 devices. |
| Input | Physical keyboard and trackpad/mouse are project priorities; full Chromebook-native behavior is still being improved. |

> If you are testing Chromite, please include your Chromebook model, CPU
> architecture, ChromeOS version, Android container type if known, selected
> renderer, Minecraft version, and logs when opening an issue.

## Installation

Chromite is currently best treated as an early open-source project. Prefer test
builds from the repository rather than expecting a polished end-user release
channel.

### Option 1: Download a build

1. Open the repository's [releases]([https://github.com/Lukiblokck/Chromite/actions](https://github.com/Lukiblokck/Chromite/releases)).
3. Download the APK artifact if one is available.
4. Install it on a Chromebook with Android app support.

Depending on your ChromeOS configuration, sideloading APKs may require enabling
Linux/ADB debugging or developer-related settings. Follow ChromeOS guidance for
your device and be aware of the security tradeoffs before enabling sideloading.

### Option 2: Build it yourself

Use the source build steps below if you want a local debug APK or plan to
contribute.

## Build from source

### Prerequisites

- Git
- JDK compatible with the Android Gradle Plugin used by this repository
- Android SDK / Android Studio with the required SDK platform installed
- Network access for Gradle dependency resolution

### Clone

```bash
git clone --recursive https://github.com/Lukiblokck/Chromite.git
cd Chromite
```

If you already cloned without submodules, run:

```bash
git submodule update --init --recursive
```

### Generate language metadata

Some language metadata is generated before building:

```bash
chmod +x scripts/languagelist_updater.sh
bash scripts/languagelist_updater.sh
```

On Windows, use:

```bat
scripts\languagelist_updater.bat
```

### Build a debug APK

```bash
./gradlew :app_pojavlauncher:assembleDebug
```

The debug APK is generated under:

```text
app_pojavlauncher/build/outputs/apk/debug/
```

### Optional: build supporting modules

```bash
./gradlew :jre_lwjgl3glfw:build
```

## Project status

Chromite is in an **early Chromebook-focused adaptation stage**.

That means:

- the repository still contains inherited Android launcher architecture;
- some package names, module names, resources, and internal identifiers may
  still reference upstream project history;
- Chromebook-specific UX and performance improvements are ongoing;
- documentation, branding, and release processes are still being established.

The goal is not to hide that history, but to move the project toward a clear
ChromeOS identity with honest tracking of what is done and what remains.

## Roadmap

| Priority | Item | Status |
| --- | --- | --- |
| High | Replace remaining inherited branding with Chromite branding where appropriate | In Progress |
| High | Improve keyboard-first defaults and configuration | In Progress |
| High | Improve trackpad/mouse behavior for Chromebook workflows | In Progress |
| Medium | Review layouts for large Chromebook displays and resizable windows | Planned |
| Medium | Document known-good Chromebook models and configurations | Planned |
| Medium | Add ChromeOS-focused troubleshooting guides | Planned |
| Medium | Explore Chromebook/container-aware performance presets | Planned |
| Low | Prepare clearer release channels and installation documentation | Planned |

Roadmap items are intentionally conservative. Features listed as planned should
not be considered available until implemented and tested.

## Contributing

Contributions are welcome, especially from Chromebook users who can test real
hardware configurations.

Helpful contributions include:

- bug reports with logs and device details;
- Chromebook input testing for keyboard, trackpad, and mouse;
- performance reports across ARM and x86_64 devices;
- documentation improvements;
- UI/UX work for larger screens;
- code cleanup that separates Chromite identity from inherited assumptions;
- pull requests for fixes and focused improvements.

Before submitting a pull request:

1. Keep changes focused and explain the motivation.
2. Include steps to test the change.
3. Avoid presenting planned Chromebook features as completed.
4. Preserve license notices and required upstream attribution.

## Support

For help, use the repository's [Issues](https://github.com/Lukiblokck/Chromite/issues)
page.

When reporting a problem, include:

- Chromebook model;
- CPU architecture if known;
- ChromeOS version;
- Minecraft version;
- selected Java runtime and renderer if applicable;
- whether the issue happens with keyboard, trackpad, mouse, touch, or all input;
- logs, screenshots, or screen recordings when useful.

## License

Chromite is distributed under the **GNU Lesser General Public License v3.0**.
See [LICENSE](LICENSE) for the full license text.

## Credits

Chromite stands on significant open-source work. The references below are kept
for project history, licensing, and attribution:

- [Amethyst Launcher](https://github.com/AngelAuraMC/Amethyst-Android) — the
  direct codebase Chromite is based on.
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) — an
  upstream project in the launcher lineage.
- [Boardwalk](https://github.com/zhuowei/Boardwalk) — an earlier project in the
  Java-on-Android Minecraft launcher lineage.

Additional third-party components and licenses may be documented in repository
license files and bundled asset license notices.

---

<div align="center">

**Chromite is for making Minecraft: Java Edition feel at home on Chromebooks.**

</div>
