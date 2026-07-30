<h1 align="center">Chromite</h1>

<img src="no image" align="left" width="130" height="130" alt="Chromite logo">

[![Android CI](https://github.com/Lukiblokck/Chromite/workflows/Android%20CI/badge.svg)](https://github.com/Lukiblokck/Chromite/actions)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/Lukiblokck/Chromite)](https://github.com/Lukiblokck/Chromite/actions)
[![Crowdin](https://badges.crowdin.net/pojavlauncher/localized.svg)](https://crowdin.com/project/pojavlauncher)
[![Discord](https://img.shields.io/discord/000000000000000000.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/YOUR_INVITE)

*Born as a fork of [Amethyst](https://github.com/AngelAuraMC/Amethyst-Android), which itself descends from [Boardwalk](https://github.com/zhuowei/Boardwalk) and [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher), here comes Chromite!*

Chromite is a launcher that lets you play Minecraft: Java Edition on your **Chromebook**, taking advantage of ChromeOS's Android subsystem to bring Java Edition to devices that weren't originally designed for it.

For more details, check out our [wiki](https://wiki.example.dev) (pending migration from the Amethyst wiki).

## Table of Contents

* [Introduction](#introduction)
* [Why a fork?](#why-a-fork)
* [Getting Chromite](#getting-chromite)
* [Building](#building)
    * [Quick Build (Recommended)](#quick-build-recommended)
    * [Detailed Build](#detailed-build)
* [Current Status](#current-status)
* [Known Issues](#known-issues)
* [FAQ](#faq)
* [Contributing](#contributing)
* [Support](#support)
* [License](#license)
* [Credits & Dependencies](#credits--dependencies)
* [Roadmap](#roadmap)

## Introduction

* Chromite is a Minecraft: Java Edition launcher built specifically for **Chromebooks**, based on the codebase of [Amethyst](https://github.com/AngelAuraMC/Amethyst-Android), which is in turn based on [Boardwalk](https://github.com/zhuowei/Boardwalk) and [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher).
* Since it runs on top of ChromeOS's Android subsystem, Chromite adapts the interface, controls, and performance for larger screens, keyboard, trackpad, and the particularities of Chromebook hardware (GPU, shared memory, ARC++/ARCVM containers).
* It can launch almost every available Minecraft version, from rd-132211 to 1.21 snapshots (including Combat Test versions).
* Modding via Forge and Fabric is also supported.
* This repository contains the source code for ChromeOS/Android. This project is **not** officially affiliated with Google, Mojang, or Microsoft.

## Why a fork?

Amethyst (and PojavLauncher before it) were designed with phones and Android tablets in mind. Chromite exists to adapt that codebase to the specific needs of Chromebooks:

* Dedicated support and detection for physical keyboard and trackpad instead of just touch controls.
* Performance and memory-management tweaks adapted to ChromeOS's ARC++/ARCVM containers.
* A resized interface for larger screens and windowed mode.
* Packaging and distribution designed for installation from the ChromeOS Play Store or via sideloading in developer mode.

## Getting Chromite

You can get Chromite in two ways:

1. **Releases:** Download the latest build from [nightly.link](https://nightly.link/Lukiblokck/Chromite/workflows/android/main/app-debug.zip) or pick an older version from our [automatic builds](https://github.com/Lukiblokck/Chromite/actions).
2. **Build from Source:** Follow the [building instructions](#building) below.

## Building

### Quick Build (Recommended)

The easiest way to build Chromite is to use the pre-built JREs provided by our CI.

1. Clone the repository: `git clone --recursive https://github.com/Lukiblokck/Chromite.git`
2. Build the launcher: `./gradlew :app_pojavlauncher:assembleDebug` (use `gradlew.bat` on Windows)

The built APK will be located in `app_pojavlauncher/build/outputs/apk/debug/`.

### Detailed Build

If you need more control over the build process, follow these steps:

1. **Java Runtime Environment (JRE):** Download the `jre8-pojav` artifact from our [CI auto builds](https://github.com/AngelAuraMC/openjdk-build-multiarch/actions). This package contains pre-built JREs for all supported architectures. If you need to build the JRE yourself, follow the instructions in the [android-openjdk-build-multiarch](https://github.com/AngelAuraMC/openjdk-build-multiarch) repository.

2. **LWJGL:** The build instructions for the custom LWJGL are available in the [LWJGL repository](https://github.com/AngelAuraMC/lwjgl3).

3. **Language List:** Because languages are auto-added by Crowdin, you need to run the language list generator before building. In the project directory, run:
   * Linux/macOS:
     ```bash
     chmod +x scripts/languagelist_updater.sh
     bash scripts/languagelist_updater.sh
     ```
   * Windows:
     ```batch
     scripts\languagelist_updater.bat
     ```

4. **Build the GLFW stub:** `./gradlew :jre_lwjgl3glfw:build`

5. **Build the launcher:** `./gradlew :app_pojavlauncher:assembleDebug` (replace `gradlew` with `gradlew.bat` on Windows).

## Current Status

* [x] OpenJDK 8 Mobile port: ARM32, ARM64, x86, x86_64
* [x] OpenJDK 17 Mobile port: ARM32, ARM64, x86, x86_64
* [x] OpenJDK 21 Mobile port: ARM32, ARM64, x86, x86_64
* [x] Headless mod installer
* [x] Mod installer with GUI
* [x] OpenGL in OpenJDK environment
* [x] OpenAL (works on most devices)
* [x] Support for Minecraft 1.12.2 and below
* [x] Support for Minecraft 1.13 and above
* [x] Support for Minecraft 1.17 (22w13a) and above
* [x] Game surface zooming
* [x] New input pipe rewritten to native code
* [x] Rewritten entire controls system
* [ ] Automatic Chromebook-specific keyboard/trackpad detection and mapping
* [ ] Performance profiles tuned per Chromebook model (ARC++ vs ARCVM)
* [ ] More to come!

## Known Issues

See our [issue tracker](https://github.com/Lukiblokck/Chromite/issues) for a list of known issues and their current status. Some issues inherited from Amethyst/PojavLauncher may behave differently on ChromeOS due to the particularities of ARC++/ARCVM containers.

## FAQ

See our [wiki](https://wiki.example.dev) for more information.

## Contributing

Contributions are welcome! We welcome any type of contribution, not only code. For example, you can help improve the wiki, contribute to [translations on Crowdin](https://crowdin.com/project/pojavlauncher), or submit bug reports and feature requests.

Any code change should be submitted as a pull request. The description should explain what the code does and give steps to execute it.

## Support

For support, please join our [Discord server](https://discord.gg/YOUR_INVITE).

## License

Chromite is licensed under [GNU LGPLv3](https://github.com/Lukiblokck/Chromite/blob/main/LICENSE), the same license as Amethyst, of which this project is a fork.

## Credits & Dependencies

Chromite is a fork of Amethyst and therefore inherits all of its original credits and dependencies:

* [Amethyst](https://github.com/AngelAuraMC/Amethyst-Android): the project Chromite is directly forked from, licensed under [GNU LGPLv3](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/LICENSE).
* [Boardwalk](https://github.com/zhuowei/Boardwalk) (JVM Launcher): Unknown License/[Apache License 2.0](https://github.com/zhuowei/Boardwalk/blob/master/LICENSE) or GNU GPLv2.
* [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): [GLGPL](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/LICENSE)
* Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
* [GL4ES](https://github.com/AngelAuraMC/gl4es): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
* [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues): [LGPL-2.1 License](https://github.com/MobileGL-Dev/MobileGlues/blob/dev-es/LICENSE).
* [Krypton Wrapper](https://github.com/BZLZHH/NG-GL4ES): [MIT License](https://github.com/BZLZHH/NG-GL4ES/blob/main/LICENSE)
* [ANGLE](https://chromium.googlesource.com/angle/angle): [All Rights Reserved](app_pojavlauncher/src/main/assets/licenses/ANGLE_LICENSE).
* [OpenJDK](https://github.com/AngelAuraMC/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
* [LWJGL3](https://github.com/AngelAuraMC/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
* [LWJGLX](https://github.com/AngelAuraMC/lwjglx) (LWJGL2 API compatibility layer for LWJGL3): unknown license.
* [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
* [bhook](https://github.com/bytedance/bhook) (used for exit code trapping): [MIT license](https://github.com/bytedance/bhook/blob/main/LICENSE).
* [libepoxy](https://github.com/anholt/libepoxy): [MIT License](https://github.com/anholt/libepoxy/blob/master/COPYING).
* [virglrenderer](https://github.com/AngelAuraMC/virglrenderer): [MIT License](https://gitlab.freedesktop.org/virgl/virglrenderer/-/blob/master/COPYING).
* [OpenAL-Soft](https://github.com/kcat/openal-soft): [GNU GPLv2](app_pojavlauncher/src/main/assets/licenses/OPENAL-SOFT_GPL2)
  * [oboe](https://github.com/google/oboe): [Apache License 2.0](app_pojavlauncher/src/main/assets/licenses/OBOE_APACHE2).
  * [pfffft](https://bitbucket.org/jpommier/pffft/src/master/): [ARR](app_pojavlauncher/src/main/assets/licenses/PFFFT_LICENSE)
* [SDL3](https://github.com/libsdl-org/SDL): [zlib License](https://github.com/libsdl-org/SDL/blob/main/LICENSE.txt)
* [sdl2-compat](https://github.com/libsdl-org/sdl2-compat): [zlib License](https://github.com/libsdl-org/sdl2-compat/blob/main/LICENSE.txt)
* Thanks to [MCHeads](https://mc-heads.net) for providing Minecraft avatars.

## Roadmap

We are currently focusing on:

* Optimizing performance and compatibility specifically for Chromebook hardware and containers (ARC++/ARCVM).
* Improving keyboard and trackpad mapping so it feels native on ChromeOS.

Future plans include:

* Exploring new rendering technologies.
* Improving overall stability and performance.
* Enhancing the mod installation experience.
* Simpler packaging for direct installation from the ChromeOS Play Store.

We welcome community feedback and suggestions for our roadmap. Please feel free to open a feature request in our [issue tracker](https://github.com/Lukiblokck/Chromite/issues).
