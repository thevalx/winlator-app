# Updating Winlator +trig on a phone

## First move from prerelease 1 to prerelease 2

Both versions use `com.winlator.trig`. The first GitHub build generated a signing
key in a folder the old workflow did not cache. That hosted runner and its private
key are gone. The public certificate in the APK cannot recreate the private key.
Android therefore requires a one-time uninstall/reinstall before v2 can be used.
Later releases will use the pinned v2 signing key and higher version codes.

**Do not uninstall v1 until you have verified your backups outside its app storage.**
Winlator currently has no full-container export/restore. Duplicating a container
inside the app does not protect it from uninstalling. Containers and their
settings must be recreated after this one-time reinstall.

1. Stop the game. Open **Input Controls**, select each custom profile and choose
   **Export Profile**. Verify the `.icp` files in Samsung My Files under
   **Download/Winlator/profiles**. These include external-controller mappings.
2. Open **Containers → ⋮ → File Manager**. Check the drive mappings: the default
   **D:** points to shared Downloads and survives uninstalling. The default **E:**
   is private app storage and is erased. Make a new backup folder on D:.
3. Copy important saves and game/mod files from C: or E: into that D: backup
   folder. NFS Underground 2 commonly saves under
   `C:\users\xuser\AppData\Local\NFS Underground 2`; check whether that folder
   exists, and preserve any other Documents/Saved Games locations you use.
   On the selected folder choose **⋮ → Copy**, use the File Manager's **Home icon**
   to return to the drives, open your D: backup folder, then tap **Paste**.
   Android Back clears this File Manager's clipboard, so use its Home icon while
   copying. Wait for completion and verify the copied files in Samsung My Files.
4. Record the container and game shortcut settings with screenshots. Include
   2340×1080, your Wine/Box64/renderer choices, the ThirteenAG Widescreen Fix,
   XtendedInput configuration, and the `dinput8` native/builtin override. The app
   has no supported export for all container settings or its registry.
5. After checking those backups, uninstall only **Winlator +trig** and install
   the v2 GitHub APK. Recreate the container, restore the saved files to their
   original locations, import profiles with **Import Profile → Open File**, and
   restore the recorded settings. Files already stored in shared Downloads
   remain there; verify your actual drive mappings before uninstalling.

The official Winlator app is a separate installation and does not need removal.

## Subsequent updates with Obtainium

Add this GitHub repository as the source:

https://github.com/thevalx/winlator-app

Enable **Include prereleases**. For the current `trig-test-<commit>` tags, sort by
**Release date** and enable **Use release date as version string**. If an APK
filter is needed, use `^Winlator-plus-trig\.apk$`. A GitHub token is not required
for this public source under normal rate limits.

Future APKs must keep `com.winlator.trig`, use the pinned v2 signing certificate,
and increase Android's version code (v2 is 34; the next update must be 35 or higher).
Obtainium cannot bypass Android's signing-key check or migrate private app data
through the initial uninstall.
