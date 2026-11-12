# NovaCraft

<p align="center">
  <strong>An independent Android launcher project for Minecraft: Java Edition</strong>
</p>

<p align="center">
  <a href="https://github.com/kritikakumari0972-bit/MC-Android-Launcher/actions/workflows/android.yml?query=branch%3Acustom-launcher-ui">
    <img src="https://github.com/kritikakumari0972-bit/MC-Android-Launcher/actions/workflows/android.yml/badge.svg?branch=custom-launcher-ui" alt="NovaCraft Android CI">
  </a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/status-Early%20development-orange" alt="Early development">
  <img src="https://img.shields.io/badge/license-LGPL--3.0-blue" alt="LGPL 3.0">
</p>

NovaCraft is an independent Android launcher project based on the open-source **Amethyst-Android** codebase. The project aims to combine a customized Minecraft: Java Edition launching experience with a flexible interface, installation and content management tools, device guidance, and useful diagnostics.

NovaCraft is **not an official Minecraft, Mojang, or Microsoft product** and is not affiliated with or endorsed by them.

> **Development status:** NovaCraft is in early development. The current repository contains the inherited launcher foundation and initial NovaCraft rebranding. Many features described in the roadmap below are planned work, not completed functionality. Check the source and releases/build artifacts for what is currently available.

## Project goals

- Give the launcher its own name, visual identity, interface, themes, textures, and navigation.
- Preserve and improve the established Android Java Edition launching foundation.
- Make configuration easier to understand on both lower-end and higher-end Android devices.
- Add practical installation, world, map, mod, and resource-pack management.
- Provide clear compatibility information and useful crash diagnostics.
- Keep features modular so that news, catalogues, and other online services cannot prevent local game launching.

## Current foundation

NovaCraft starts from the Amethyst-Android project and its existing Android-specific launcher infrastructure. The inherited codebase includes functionality for launching Minecraft: Java Edition on Android, managing Java runtimes and installations, touch controls, renderer options, and supported mod-loader workflows.

Actual compatibility depends on the Minecraft version, Java runtime, Android device, graphics driver, renderer, and installed mods. A feature being present in the upstream project does not mean every game version or device supports it.

### NovaCraft changes currently recorded

- Android application display name changed to **NovaCraft**.
- Short application name changed to **NovaCraft**.
- Foreground-service notification and fatal-error wording updated to NovaCraft.
- A GitHub Actions workflow builds a **Debug APK** and uploads it as an artifact.
- Mod compatibility inspection for declared dependencies, conflicts, and Minecraft version metadata.
- Resource-pack and shader-pack import, validation, and visibility management.
- A Java runtime centre with ABI-aware runtime guidance and the existing runtime installer.
- Low-end performance presets that adjust launcher memory allocation and render scale.
- A local backup and restore centre for worlds, mods, packs, profile settings, and the NovaCraft skin library.
- A local PNG skin library with a touch-rotatable 3D-style avatar preview. Skin changes are not uploaded to online accounts.
- Full backup verification with ZIP entry validation, CRC checks, an extraction-size limit, and a SHA-256 fingerprint.

The full custom home screen, icon, themes, animated backgrounds, and additional modules are still part of the development plan unless implemented in the source.

## Feature roadmap

The following is the target feature set. Items are not represented as complete until they have been implemented and tested.

### Interface and customization
- Original NovaCraft home screen and navigation.
- Custom logo, application icon, textures, fonts, colours, and button designs.
- Repositionable and configurable interface elements where practical.
- Theme presets and a customization workshop.
- Animated, static, and lightweight background modes.
- Responsive layouts for different phone sizes and orientations.

### Accounts
- Microsoft sign-in through the appropriate secure authentication flow.
- Ely.by authentication using its supported integration.
- Local/offline profiles for gameplay modes that permit them.
- Clear account status, profile switching, sign-out, and authentication error handling.

Account types must remain clearly distinguished. NovaCraft must not collect Microsoft passwords in its own login form, bypass game-ownership checks, or present an offline profile as an authenticated Microsoft account.

### Minecraft installations and versions
- Manage separate Minecraft installations and profiles.
- Browse compatible release versions, snapshots, and older versions.
- Select suitable Java runtimes using version metadata.
- Support compatible mod-loader installation workflows.
- Show download progress, retry failures, and repair incomplete installations.
- Keep each installation's settings and content separate.

### Worlds and maps
- Discover supported Java world folders.
- Import worlds from folders and supported archives.
- Export, rename, organize, back up, and restore worlds.
- Browse a map catalogue with descriptions, screenshots, authors, and version information.
- Validate archives before extraction and protect existing worlds from accidental replacement.
- Use Android's supported file picker and storage-access mechanisms.

Java worlds and Bedrock worlds are different formats and are not automatically interchangeable.

### Mods and content
- Manage compatible mods and mod loaders.
- Import supported modpacks, including compatible `.mrpack` files where supported.
- Inspect declared dependencies and version requirements.
- Detect duplicate files and report known compatibility conflicts.
- Manage resource packs and compatible shader files.
- Respect the terms and licences of content providers and download sources.

### Settings and device guidance
- Organized settings for appearance, performance, Java, memory, rendering, controls, accounts, versions, downloads, and storage.
- Device information and compatibility reports based on data Android makes available.
- Conservative low-end, balanced, and performance recommendations.
- Device-specific help guides and troubleshooting articles.
- Clear distinction between detected facts, recommendations, community reports, and unknown information.

NovaCraft cannot guarantee a particular FPS or make unsupported hardware features available. Recommendations must be based on the device and the selected installation.

### Official Minecraft news
- A dedicated news page for official Minecraft articles and announcements.
- Article title, date, category, short summary, and link to the original source.
- Search, filters, saved articles, and read/unread indicators.
- Cached article metadata and a visible last-refresh time.
- Source verification and clear attribution.

The news page should link to official material and must not invent headlines, dates, or announcements.

Official source: [Minecraft News and Articles](https://www.minecraft.net/en-us/articles).

### Diagnostics and quality-of-life tools
- Crash-log viewer and guided troubleshooting.
- Checks for missing files, runtime mismatches, and mod-loader issues.
- Download queue, retry, and integrity checks when trusted hashes are available.
- World backup reminders and restore points.
- Session history based on information the launcher can actually measure.
- Optional, privacy-conscious compatibility reports.

## Building NovaCraft

### Build with GitHub Actions (recommended for phone-only development)

1. Open the [NovaCraft repository](https://github.com/kritikakumari0972-bit/MC-Android-Launcher).
2. Select the `custom-launcher-ui` branch.
3. Open the **Actions** tab and select **Android CI**.
4. Run the workflow if it did not start automatically after a commit.
5. Open the completed workflow run.
6. Under **Artifacts**, download `NovaCraft-Android-Debug`.
7. Extract the downloaded ZIP to obtain the debug APK.

The workflow runs NovaCraft backup unit tests and builds a **Debug APK**. A signed Release APK is also built when all four protected signing secrets described below are configured; otherwise release signing is explicitly skipped and the debug artifact remains clearly labelled.

### Optional signed Release APK

For a signed release, create a **new** private keystore outside the repository. Add these repository secrets under **Settings → Secrets and variables → Actions**:

- `NOVACRAFT_RELEASE_KEYSTORE_BASE64`: one-line Base64 encoding of the new keystore file.
- `NOVACRAFT_RELEASE_STORE_PASSWORD`: keystore password.
- `NOVACRAFT_RELEASE_KEY_PASSWORD`: private-key password.
- `NOVACRAFT_RELEASE_KEY_ALIAS`: alias stored in that keystore.

The workflow decodes the secret keystore only into the temporary GitHub runner. Never paste passwords into chat or commit a keystore/base64 file. Until all four secrets exist, CI builds only the debug APK. A release should not be labelled production-ready until its signing identity, update path, and on-device installation have been checked.

### Build locally

The project uses Gradle. From a suitable development environment with the required Android build tools and Java setup:

```bash
git clone --recursive https://github.com/kritikakumari0972-bit/MC-Android-Launcher.git
cd MC-Android-Launcher
./gradlew :app_pojavlauncher:assembleDebug
```

On Windows, use `gradlew.bat :app_pojavlauncher:assembleDebug`.

The APK is normally generated under:

```text
app_pojavlauncher/build/outputs/apk/debug/
```

The repository's GitHub Actions workflow contains the current cloud-build setup, including its Java and JRE preparation steps. Refer to that workflow when maintaining the build process.

## Development workflow

- Make changes on `custom-launcher-ui`.
- Keep `backup-before-redesign` as a recovery point.
- Inspect existing layouts, view IDs, settings, and Java/Kotlin code before changing them.
- Preserve required upstream notices and dependency licences.
- Make small, reviewable changes.
- Run Android CI after each meaningful change.
- Test the resulting APK on real devices before marking a feature complete.

A successful build confirms that the application compiled; it does not by itself confirm that every screen or feature works correctly.

## Contributing and bug reports

Contributions, bug reports, and practical feature suggestions are welcome.

When reporting a problem, include:
- Device model and Android version, if known.
- NovaCraft branch or commit.
- Minecraft version and selected Java runtime.
- Renderer and mod-loader information, when relevant.
- The relevant error message or a redacted crash log.

**Do not post passwords, Microsoft authentication tokens, private account data, or personal world files in issues.**

Open issues in the [NovaCraft issue tracker](https://github.com/kritikakumari0972-bit/MC-Android-Launcher/issues).

## Privacy and security

- Authentication secrets must be handled securely and must not be written to ordinary logs.
- Diagnostic uploads must be optional and explain what information is shared.
- Archive extraction must validate paths and reject unsafe entries.
- Downloads should be checked when reliable integrity information is available.
- Existing worlds and installations should be protected from accidental destructive changes.
- Production signing material must be supplied through protected GitHub Actions secrets and never committed to Git. Treat the legacy keystore already present in repository history as exposed; do not use it for a new public release. If an APK signed with that key has already been distributed, do not rotate signing identity blindly because Android will reject updates signed by a different key.
- Online catalogues and news must use permitted sources and respect their terms.

## License and upstream acknowledgements

NovaCraft is based on **Amethyst-Android**. The inherited project is distributed under the GNU Lesser General Public License v3.0. See the repository's [LICENSE](LICENSE) file and preserve all notices and obligations that apply to modified and redistributed components.

Upstream project: [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android).

NovaCraft is an independent fork and should not be confused with the upstream Amethyst project. Changes made in this repository must remain distinguishable from the original project.

### Credits and dependencies

The upstream project acknowledges the following projects and components. Their respective licences continue to apply:

- [Boardwalk](https://github.com/zhuowei/Boardwalk) (JVM Launcher): Unknown License/[Apache License 2.0](https://github.com/zhuowei/Boardwalk/blob/master/LICENSE) or GNU GPLv2.
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): [GLGPL](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/LICENSE).
- Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
- [GL4ES](https://github.com/AngelAuraMC/gl4es): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
- [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues): [LGPL-2.1 License](https://github.com/MobileGL-Dev/MobileGlues/blob/dev-es/LICENSE).
- [Krypton Wrapper](https://github.com/BZLZHH/NG-GL4ES): [MIT License](https://github.com/BZLZHH/NG-GL4ES/blob/main/LICENSE).
- [ANGLE](https://chromium.googlesource.com/angle/angle): [All Rights Reserved](app_pojavlauncher/src/main/assets/licenses/ANGLE_LICENSE).
- [OpenJDK](https://github.com/AngelAuraMC/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
- [LWJGL3](https://github.com/AngelAuraMC/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
- [LWJGLX](https://github.com/AngelAuraMC/lwjglx) (LWJGL2 API compatibility layer for LWJGL3): unknown license in the upstream README; verify before redistribution.
- [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
- [bhook](https://github.com/bytedance/bhook) (exit-code trapping): [MIT License](https://github.com/bytedance/bhook/blob/main/LICENSE).
- [libepoxy](https://github.com/anholt/libepoxy): [MIT License](https://github.com/anholt/libepoxy/blob/master/COPYING).
- [virglrenderer](https://github.com/AngelAuraMC/virglrenderer): [MIT License](https://gitlab.freedesktop.org/virgl/virglrenderer/-/blob/master/COPYING).
- [OpenAL-Soft](https://github.com/kcat/openal-soft): [GNU GPLv2](app_pojavlauncher/src/main/assets/licenses/OPENAL-SOFT_GPL2).
  - [oboe](https://github.com/google/oboe): [Apache License 2.0](app_pojavlauncher/src/main/assets/licenses/OBOE_APACHE2).
  - [pffft](https://bitbucket.org/jpommier/pffft/src/master/): [ARR](app_pojavlauncher/src/main/assets/licenses/PFFFT_LICENSE).
- [SDL3](https://github.com/libsdl-org/SDL): [zlib License](https://github.com/libsdl-org/SDL/blob/main/LICENSE.txt).
- [sdl2-compat](https://github.com/libsdl-org/sdl2-compat): [zlib License](https://github.com/libsdl-org/sdl2-compat/blob/main/LICENSE.txt).
- Thanks to [MCHeads](https://mc-heads.net) for providing Minecraft avatars.

Please review the actual licence files for all included components before redistributing a build. This README is an acknowledgement guide, not legal advice or a replacement for the required licence texts.

## Disclaimer

Minecraft is a trademark of Microsoft/Mojang. NovaCraft is an independent community project. It is not sponsored, approved, or endorsed by Microsoft, Mojang, or the Minecraft team. Users must obtain Minecraft through authorized channels and comply with the applicable Minecraft terms.
