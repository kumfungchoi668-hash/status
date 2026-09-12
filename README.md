# Status Halo

Status Halo is a lightweight Android status indicator inspired by compact signal widgets. It draws a configurable halo overlay near the status bar and combines cellular signal, Wi‑Fi, battery state and optional network speed in one compact indicator.

## Highlights

- Cellular signal shown as outer arc segments
- Wi‑Fi strength shown in the center
- Battery represented with four dots
- Optional network speed and mobile network type
- Light / dark / follow-system appearance
- Adjustable size, line width, opacity and position
- Quick Settings tile
- Screen-off aware rendering to reduce unnecessary work
- No analytics, accounts or data upload

## Build

Open the project in Android Studio and build the `app` module, or run the included GitHub Actions workflow.

### GitHub Actions

Go to **Actions → Build APK → Run workflow**. After the build finishes, download the generated debug APK artifact.

## Samsung / One UI tip

For the cleanest result, use Good Lock → QuickStar to hide the stock status icons that overlap the halo.

## Permissions

Status Halo only requests permissions needed to read device connectivity / telephony state and render its overlay. It does not request Internet access.

## Notes

This is an independent implementation and is not affiliated with Samsung or O.status.
