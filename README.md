# Simple Photos

A small, fast photo gallery for Android. It shows the pictures already on the
phone — nothing else. No account, no ads, no cloud, no tracking.

It exists because Google Photos started showing ads to people who only wanted
to look at their own pictures.

## What it does

- **Every photo, newest first**, grouped under big headings: Today, Yesterday,
  then the date.
- **Albums** — the folders your camera and other apps already use.
- **Full screen viewer** — swipe between photos, pinch to zoom, tap to hide the
  buttons, share with the share icon.
- **Big buttons and plain words**, sized for people who don't want a settings
  screen to fight with.
- **Light and dark theme** follows the phone.

## What it does not do

- It has **no `INTERNET` permission at all**, so it cannot upload, download or
  show an advert even in principle.
- It never edits, moves or deletes a photo. Read-only.
- It has no account and no sign-in.

## Install

Grab the APK from the latest green run under **Actions → Build APK →
simple-photos-debug-apk**, copy it to the phone and open it. Android will ask
you to allow installing apps from this source; that prompt is normal for any
app that doesn't come from the Play Store.

The APK is signed with the standard Android debug key. That is fine for
installing it yourself; it is not a Play Store release build.

On first launch the app asks for permission to see photos. On Android 14+ you
can choose "Select photos" and it will only see the ones you pick.

## Build it yourself

```bash
./gradlew :app:assembleDebug
# APK ends up in app/build/outputs/apk/debug/
```

Needs JDK 17 and the Android SDK (compileSdk 34). Android Studio will set both
up for you; from the command line, set `ANDROID_HOME` or add `local.properties`
with `sdk.dir=/path/to/android-sdk`.

## Code layout

| File | What it holds |
| --- | --- |
| `PhotoStore.kt` | All MediaStore queries, thumbnails, full-size decoding |
| `MainActivity.kt` | The grid of photos, permission screen |
| `PhotoAdapter.kt` | Day headings + photo tiles |
| `AlbumsActivity.kt` | The folder list |
| `ViewerActivity.kt` | Full-screen pager, share |
| `ZoomImageView.kt` | Pinch-zoom and pan |
| `Dates.kt` | "Today"/"Yesterday" labels, permission checks |

Kotlin only, no Compose, no image-loading library: the whole app is a few
hundred lines and builds in well under a minute.

## Licence

MIT
