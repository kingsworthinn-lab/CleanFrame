# CleanFrame for Android TV (Mi Box)

This folder turns CleanFrame into an Android TV app, for a Samsung The Frame (or any TV) that gets its apps from an Android TV box such as a Xiaomi Mi Box. The interface is the same one in `../app`. Only the small device layer is different: [`app/src/main/web/device.js`](app/src/main/web/device.js) replaces the Samsung `device.js` and talks to [`DeviceBridge.java`](app/src/main/java/com/cleanframe/androidtv/DeviceBridge.java).

What works the same as on Samsung: layouts, wallpapers, fonts, glass focus, saved settings, All apps and arranging Home. What changes:

- **Apps** launch by Android package name (Netflix is `com.netflix.ninja`, for example). All apps lists everything in the box's app row, with each app's own icon.
- **Home starts with the built-in apps you actually have installed.** Add others, such as Showmax or Kodi, from All apps.
- **Art Mode is gone** from the controls. Art Mode belongs to the Frame itself, not the box. Use the Frame remote as usual.
- **Settings › Android TV** opens the box's usual Home screen or its settings. Back on CleanFrame's Home offers the same.
- The app never closes on Back, and loads nothing from the internet.

Not yet tried on a real box. The interface and bridge were tested in Chromium with a simulated box. Please check the first install with your remote, and report what you see.

## 1. Get the APK

**Option A: let GitHub build it (no software to install).** Fork this repository on GitHub. Open your fork's **Actions** tab and enable workflows if asked. Choose **Android TV build** and press **Run workflow**. After about five minutes, open the finished run and download **CleanFrame-AndroidTV** under Artifacts. The zip contains `CleanFrame-AndroidTV.apk`.

**Option B: build it yourself.** Install [Android Studio](https://developer.android.com/studio), choose **Open**, pick this `android` folder, and wait for it to finish syncing. Then choose **Build › Build App Bundle(s) / APK(s) › Build APK(s)**. The file appears in `android/app/build/outputs/apk/debug/`.

### Keep one signing key (recommended)

Android only installs a new version over the old one when both are signed with the same key. Otherwise you must uninstall first, which clears your CleanFrame settings. Android Studio reuses your computer's key automatically. GitHub uses a new throwaway key each time unless you give it yours:

```sh
mkdir -p .local/android
keytool -genkeypair -keystore .local/android/cleanframe.jks -alias cleanframe \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass YOUR-PASSWORD -keypass YOUR-PASSWORD \
  -dname "CN=CleanFrame"
base64 < .local/android/cleanframe.jks   # copy this output
```

In your fork, go to **Settings › Secrets and variables › Actions** and add two secrets: `CLEANFRAME_KEYSTORE_BASE64` (the output above) and `CLEANFRAME_KEYSTORE_PASSWORD`. `.local/` is ignored by Git. Never commit the key.

## 2. Install it on the Mi Box

Pick whichever route is easiest:

- **Straight onto the box, with no computer.** When you run the build, tick **Publish the APK as a GitHub release**. On the Mi Box, install *Downloader* (by AFTVnews) from Google Play and open it. Type `https://github.com/YOUR-NAME/CleanFrame/releases/latest/download/CleanFrame-AndroidTV.apk`, using your GitHub name, and choose **Install**. Downloader asks once for permission to install apps. That address is public, so anyone with it can download your build.
- **From your phone.** Install *Send files to TV* on both the phone and the Mi Box, send the APK, then open it on the box.
- **From a USB stick.** Copy the APK onto it and open it with a file manager on the box, such as *X-plore File Manager*.
- **From a computer with adb.** On the box, open **Settings › Device Preferences › About** and select **Build** seven times. Then turn on **Developer options › USB debugging** (some boxes call it network debugging). Run `adb connect <box-ip>` followed by `adb install -r CleanFrame-AndroidTV.apk`.

The first two routes need permission for that file app: **Settings › Device Preferences › Security & restrictions › Unknown sources**. CleanFrame then appears in the box's app row. Open it and try the remote: the arrows, OK and Back.

If movement feels slow on an older Mi Box, try **Settings › Focus › Type** and a **Dark** background. Both use fewer layers.

## 3. Optional: open CleanFrame with the Home button

This is the riskiest step, as it is on Samsung. Skip it until the app works well when opened from the app row.

**Safer: remap the button.** Install *Button Mapper* from Google Play and set the Home button to open CleanFrame. This changes no system settings, and you can undo it by uninstalling Button Mapper.

**Stronger: disable the stock Home screen with adb.** Some Mi Box firmware then uses CleanFrame as Home. First write down the exact package name for your stock Home screen. Run `adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME` while CleanFrame is not yet the default. It is usually `com.google.android.tvlauncher` on a Mi Box, or `com.google.android.apps.tv.launcherx` on the Google TV model. Then:

```sh
adb shell pm disable-user --user 0 com.google.android.tvlauncher   # use your package name
```

To undo it:

```sh
adb shell pm enable com.google.android.tvlauncher
```

Keep debugging switched on so you can always run the undo command. Disabling the stock Home screen also removes its recommendation rows and its built-in Settings shortcut. CleanFrame's **Settings › Android TV › Box settings** still opens the box's settings.

## Developing

`../app` is copied into the APK at build time (`prepareWeb` in `app/build.gradle`), with the same exclusions as the public Samsung export, so private fonts are never packed. Interface changes belong in `../app`, and both builds pick them up. Check them in the browser preview as usual (`npm run dev:public`). To preview with the Android device layer, serve a copy of `app/` whose `device.js` is replaced by `android/app/src/main/web/device.js`, and define a stand-in `window.CleanFrameAndroid` that has the methods in `DeviceBridge.java`.

Android-specific launch names live in `IDS`, `EXTRA_BRANDS` and `STREAMING` at the top of `app/src/main/web/device.js`.
