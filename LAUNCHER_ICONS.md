# Launcher Icons

## Status
Launcher icons need to be generated using Android Studio's Image Asset Studio.

## Instructions

1. Open Android Studio
2. Right-click on `app/src/main/res` → New → Image Asset
3. Configure the icon:
   - Icon Type: Launcher Icons (Adaptive and Legacy)
   - Foreground Layer: Choose or create an icon
   - Background Layer: Choose a color or image
4. Click Next and Finish

This will automatically generate icons for all density folders:
- `mipmap-mdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-hdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-xhdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-xxhdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-xxxhdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` (adaptive icons)

## Note
The AndroidManifest.xml already references these icons:
- `android:icon="@mipmap/ic_launcher"`
- `android:roundIcon="@mipmap/ic_launcher_round"`

Once generated, these will work automatically.

