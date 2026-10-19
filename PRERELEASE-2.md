# Winlator +trig prerelease 2

Version `11.2+trig.2` (version code 34). Java/JNI names remain `com.winlator`.
The first prerelease signing key was not retained by the old workflow, so this
version cannot update that installation directly. Keep the first installation
and its data until an installation or backup plan has been chosen.

Builds now use an explicit signing-key location and verify the APK certificate
against a committed public fingerprint. If the retained key is unavailable, the
workflow stops rather than publishing an APK with a replacement key. GitHub Actions
caches can expire; the cache is not a permanent key backup.

## In-game menu and HUD

Android Back or the existing four-finger gesture opens the new dark sidebar.
Its vertical rail provides Desktop, Settings, Input, HUD, Task Manager and Resume.
The existing keyboard, window, fullscreen, magnifier, effects, picture-in-picture,
logs, help and exit actions keep their existing handlers. The Task Manager page
shows Windows processes and provides their existing management actions.

In HUD, choose Enable HUD and Modern for the compact colored strip. FPS, renderer,
GPU usage/name, CPU usage/temperature, RAM, battery power and temperature can be
selected individually. Classic keeps the existing Winlator overlay. Until changed
explicitly, HUD enablement inherits the container's existing setting.

Telemetry reads run on a background worker at most once per second and stop when
the HUD is disabled, Classic is selected, or the activity is paused. Restricted
sensors show N/A. If Android blocks total CPU accounting, measured app CPU is
explicitly labeled App CPU. FPS uses the existing X11 update counter; sensor reads
never run per frame. The Task Manager polls only while its page is open.

## Bind the G8+ Mode button (optional)

1. Open Input Controls and select your existing controls profile.
2. Under External Controllers, open GameSir-G8+.
3. Press the controller's Mode button to find the BUTTON MODE row.
4. In that row, select **Winlator → Open in-game menu**.
5. Use that controls profile in the running game (or its shortcut settings).

The button opens and closes the same in-game menu as Android Back. No controller
button is reserved by default. Remove the action or select NONE to stop using it.
The mapping is saved with the existing controller profile. Held repeats and the
release do not reopen the menu, and menu navigation is isolated from game input.
Other trigger and controller mappings retain their existing behavior.

## Scope and validation

This version changes Android UI and local input handling. Wine, Box64, DXVK,
Turnip, runtime assets, renderer defaults/optimization flags and the Windows
packet format stay unchanged. No Ludashi runtime or renderer subsystem is included.

Checks used for this version:

- Existing trigger, mapping/editor and runtime-path regression suites.
- HUD metric parsing/calculation tests and profile menu binding/input-gate tests.
- Multi-slot/controller neutral-release regression checks.
- Android UI tests for menu routing, HUD controls/lifecycle, persisted profile
  mappings, opt-in behavior, held/repeated buttons and focus-loss recovery.
- ARM64 debug APK build, signature/application identity and official asset checks.

Phone validation is still required for the new UI, G8+ binding and FPS overhead.
Keep the same NFS Underground 2 settings as the first release: 2340×1080,
ThirteenAG Widescreen Fix, XtendedInput and dinput8 native/builtin override.
