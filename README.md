# GodotAdMob-UMP

Based on commit [#676b6ab](https://github.com/Shin-NiL/Godot-Android-Admob-Plugin/commit/676b6ab42d2eb212fffc47fce85cb09de47ab9b1) by [Shin-NiL](https://github.com/Shin-NiL/Godot-Android-Admob-Plugin).

Godot plugin to use AdMob and UMP into your game. UMP is used to get consent from people living within EEA countries (https://developers.google.com/admob/android/privacy?hl=en).

Created originally to be used only in [Wacky Volleyball](https://play.google.com/store/apps/details?id=wacky.volleyball) i decided to make it public in case someone needs it for their game.

This plugin supports:
- Banner
- Interstitial
- Rewarded Video

<h3>HOW TO USE</h3>

- Go to the [release](https://github.com/Daddiu/GodotAdMob-UMP/edit/Daddiu-readmework/README.md) tab, choose the version and download both the GodotAdMob-UMP and admob-godotlib zips.
- Install the Android Build Template from "Project" -> "Install Android Build Template" if you haven't already (more about it [here](https://docs.godotengine.org/en/stable/tutorials/export/android_custom_build.html))
- Unzip the contents of the GodotAdMob-UMP package into your project's ``` res://android/plugins ``` folder
- Unzip the contents of the admob-godotlib package into your project's root folder
- Go to on the Project -> Export... -> Android -> Options
  - Under "Custom Build" check that "Use Custom Build" is checked.
  - under "Plugins" check that "GodotAdMob" is checked.
  - Under "Permissions" check that "Access Network State" and "Internet" are both checked.
- Edit ```res://android/build/AndroidManifest.xml ``` to add your appID.
> [!NOTE]
  > In Godot 3 .xml files aren't visible from the editor, you should open it from your OS file explorer

  > [!NOTE]
  > If you want to test ads without using your appID, you should use this appID which only fires test ads (more about it [here](https://developers.google.com/admob/android/quick-start#update_your_androidmanifestxml))

  ```
  <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713"/>
  ```
