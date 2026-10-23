# AndroForge

A fully customizable Android launcher with a dynamic, magnifying dock and a built-in auto-updater.

## Features

**Dynamic dock**
- Magnifies under your finger like macOS — slide along it and let go to launch
- Recently used apps slide in after your pinned apps (marked with an accent dot)
- Tints itself from your wallpaper, with an accent glow
- Styles: Floating, Glass, Bar, Minimal · adjustable size, opacity, corner radius, labels, bounce
- Holds folders too — drop an app onto a dock icon to make one

**Home screen**
- Multiple pages, adjustable grid (3–8 columns, 3–10 rows), icon size, labels
- Drag & drop everything; drop an app on another to make a folder; hold at the edge to flip pages
- Widgets: add, resize, move, remove
- Folders with grid or stack previews, custom names and colors
- Lock layout to prevent accidental changes

**Icons**
- Shapes: squircle, circle, rounded, square, teardrop, hexagon, or system
- ADW/Nova icon pack support, plus per-app icon and label overrides

**App drawer**
- Swipe up, instant search (press Go to launch the top hit), suggested apps, web search fallback
- Hide apps, adjust columns and background opacity
- App shortcuts in long-press menus (once AndroForge is your default launcher)

**Gestures** — swipe up, swipe down and double-tap can each open the drawer, search, notifications,
quick settings, launcher settings, or lock the screen.

**Theme** — accent from wallpaper, Material You, or a custom color.

**Backup** — export/import your whole layout as JSON.

## Updates

Every push to `main` builds a signed APK and publishes it as a GitHub Release. The launcher checks
the latest release (on open, and every few hours in the background), shows the changelog, and
installs the update in-app. On Android 12+, once the first update has gone through, later updates
install without a confirmation prompt.

### One-time setup (repo owner)

Add two repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `ANDROFORGE_KEYSTORE` | base64 of the release keystore |
| `ANDROFORGE_KEYSTORE_PASSWORD` | the keystore password (alias is `androforge`) |

Without them, CI still builds (to catch errors) but doesn't publish releases.
**Keep the keystore safe** — updates only install over a build signed with the same key.

### Installing

Download the newest `AndroForge-v*.apk` from [Releases](https://github.com/LibexiL/AndroForge/releases),
install it, then press Home and pick AndroForge (or use *Set default* in its settings).

## Building locally

Requires JDK 17 and the Android SDK (API 35).

```bash
gradle wrapper --gradle-version 8.14.3   # once, if you want ./gradlew
./gradlew :app:assembleDebug
```

## CI logs

The workflow pushes its latest build log to the `ci-logs` branch, which makes failed builds easy to inspect.
