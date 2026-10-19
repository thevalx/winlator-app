# Winlator +trig

Based on official `brunodev85/winlator-app` commit
`8077f374ef2d54127b088de9e8cd7b4af14d9c8b` (Winlator 11.2 source).

The app installs as `com.winlator.trig`, alongside `com.winlator`. Java packages,
the Android namespace and native JNI symbol names remain `com.winlator`.
Containers and preferences belong to the separate installation.

## Trigger settings

Input Controls → Trigger Mode sets the global `trigger_type` preference:

| Value | Mode | Behavior |
| --- | --- | --- |
| 0 | As Button | L2/R2 produce 0 or 1 and trigger button bits. |
| 1 | As Axis (default) | Preserve continuous trigger axes; duplicate L2/R2 key events cannot overwrite them. |
| 2 | Both | Preserve continuous axes and trigger button bits. |

Shortcut Settings → Advanced → Input Controls places Trigger Mode between Profile
and DirectInput Mapper Type. “Use global setting” removes the `triggerType`
override. Explicit selections persist `0`, `1` or `2` even if the global setting
later changes. Missing or invalid overrides use the global setting.

The existing LTRIGGER/BRAKE and RTRIGGER/GAS axis selection, clamping and epsilon
are retained. In Button/Both modes, an Android trigger key or analog travel above
50% sets the corresponding button bit. Separate key and raw-axis state handles
either key/motion release order. Axis mode requires an axis-producing controller;
Button mode also works with key-only controllers. Virtual on-screen controls keep
their existing digital `GamepadState.setPressed()` behavior.

The Windows gamepad packet layout, XInput/DirectInput transport, Wine, Box64,
DXVK, Turnip, runtime assets, optimization flags and renderer defaults are unchanged.
The existing Standard DirectInput mapper still exposes triggers as buttons; select
the existing XInput mapper for its continuous trigger-axis transport.

## Side-by-side identity audit

The application ID and localized launcher labels change. FileProvider uses the
application ID, including its URI creation call. `AppUtils.INTERNAL_STORAGE` uses
`BuildConfig.APPLICATION_ID`. Gradle passes the application ID to CMake so native
cache and socket path macros use the fork's data directory:

- `APP_CACHE_DIR` in `winlator/include/winlator.h`
- `X11_SERVER_PATH` in `gladiorenderer/include/gladio.h`
- `VORTEK_SERVER_PATH` in `vortekrenderer/include/vortek.h`

The bundled guest runtime also contains absolute official rootfs paths. At archive
extraction only, `RootFSPathRelocator` replaces the 36-byte prefix
`/data/data/com.winlator/files/rootfs` with the equal-length
`/data/data/com.winlator.trig/files/r`. A private `files/r -> rootfs` symlink resolves
the relocated loader, Wine, graphics-client, font and configuration paths to this
installation. Absolute archive symlink targets are relocated too. File sizes, ELF
offsets, executable sections and all other bytes are preserved. The bundled archive
files themselves stay identical to official. This is identity relocation, not a
change to Wine, Box64, DXVK, Turnip or renderer algorithms/defaults; no hot-path
translation is added. Like official Winlator, these paths assume Android's owner-user
`/data/data` alias.

These are identity path substitutions, with no renderer logic changes. Existing
JNI `Java_com_winlator_*` symbols and manifest activity/service class names remain
unchanged. The foreground service's existing private intent action is unchanged;
the service is not exported.

## Build and checks

The `Winlator +trig test APK` workflow builds the `feat/trigger-modes` branch,
verifies APK signing and application identity, and uploads an artifact containing
`Winlator-plus-trig.apk`, `SHA256SUMS.txt` and `apk-badging.txt`.
It uses the official SDK/NDK/CMake versions and all official runtime assets.
The debug signing key is cached between CI builds; retain the original APK/key
when distributing subsequent test builds that should install as updates. The local
cloud APK and GitHub Actions APK use separate debug keys, so use one build source
consistently for updates; Android rejects an in-place update signed by another key.

With the official Android build prerequisites installed:

```sh
python3 tests/trigger_modes/run.py
python3 tests/runtime_paths/run.py  # JDK and zstd required
bash gradlew --no-daemon --max-workers=2 assembleDebug
```

The dependency-free regression harness compiles the actual `ExternalController`,
`GamepadState`, `TriggerMode` and `Mathf` source against small Android event fixtures.
It checks both trigger axis pairs, duplicate keys, both release orders, independent
left/right travel, key-only Button mode, clamping, mode changes, ordinary buttons,
virtual digital triggers and global/shortcut setting resolution. The mapping/editor
checks exercise the production event-consumer methods so axis-only triggers keep
working in custom profiles and the binding editor independently of native slot mode.
Runtime path checks cover stream boundaries, short reads, repeated relocation,
symlinks and all 25 bundled runtime archives. They verify that only matching path
bytes change and that ELF layouts and executable sections remain identical. These
checks do not replace an Android APK build or physical-controller testing.

## Phone testing

Install alongside official Winlator. Recreate the working NFS Underground 2 setup:
2340×1080, ThirteenAG Widescreen Fix, XtendedInput, and the dinput8 native/builtin
override. Keep the same graphics and runtime settings as the official baseline.

Check partial and full throttle/brake, simultaneous triggers, full release, and
all three modes. Check a shortcut override, then “Use global setting,” after
changing the global selection. Compare FPS against official Winlator on the same
scene and settings. Performance and device compatibility require phone testing;
no on-device result is implied by the source or host regression checks.
