# GodotAdMob-UMP

Based on commit [#676b6ab](https://github.com/Shin-NiL/Godot-Android-Admob-Plugin/commit/676b6ab42d2eb212fffc47fce85cb09de47ab9b1) by [Shin-NiL](https://github.com/Shin-NiL/Godot-Android-Admob-Plugin).

Godot plugin to use AdMob and UMP into your game. UMP is used to get consent from people living within EEA countries (https://developers.google.com/admob/android/privacy?hl=en).

Created originally to be used only in [Wacky Volleyball](https://play.google.com/store/apps/details?id=wacky.volleyball) i decided to make it public in case someone needs it for their game.

This plugin supports:
- Banner
- Interstitial
- Rewarded Video

<h3>HOW TO INSTALL</h3>

- Go to the [release](https://github.com/Daddiu/GodotAdMob-UMP/edit/Daddiu-readmework/README.md) tab, choose the version and download both the GodotAdMob-UMP and admob-godotlib zips
- Install the Android Build Template from "Project" -> "Install Android Build Template" if you haven't already (more about it [here](https://docs.godotengine.org/en/stable/tutorials/export/android_custom_build.html))
- Unzip the contents of the GodotAdMob-UMP package into your project's ``` res://android/plugins ``` folder
- Unzip the contents of the admob-godotlib package into your project's root folder
- Go to on the Project -> Export... -> Android -> Options
  - Under "Custom Build" check that "Use Custom Build" is checked
  - under "Plugins" check that "GodotAdMob" is checked
  - Under "Permissions" check that "Access Network State" and "Internet" are both checked
- Edit ```res://android/build/AndroidManifest.xml``` to add your appID
  > [!NOTE]
  > In Godot 3 .xml files aren't visible from the editor, you should open it from your OS file explorer

  > [!NOTE]
  > If you want to test ads without using your appID, you should use this appID which only fires test ads (more about it [here](https://developers.google.com/admob/android/quick-start#update_your_androidmanifestxml))

  ```
  <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713"/>
  ```
  >[!NOTE]
  >One good place to add this metadata is just below these lines, inside of the application tag:
  ```
  <application>
  ...
  <!-- Custom application XML added by add-ons. -->
  <!--CHUNK_APPLICATION_BEGIN-->
  <!--CHUNK_APPLICATION_END-->
  
  Here
  </application>
  ```
<h3>HOW TO USE</h3>

- Setup the AdMob Node in your game by adding it into your scene
  >[!NOTE]
  > I recommend to make it a singleton by going into Project -> Project Settings -> Autoload and adding the script there, but it's not stricly required

- Modify the banner_id,interstitial_id and rewarded_id to use your own id, found in your AdMob page (the default ones will only fires test ads)
  >[!NOTE]
  > Make sure when shipping your game to set "isReal" property to true, otherwise it will treat any device as a developer device and will not generate you revenue
  
- Load the type of ads you need with either ```load_interstitial()```, ```load_rewarded_video()``` or ```load_banner()```
  >[!WARNING]
  > You should always load ads way before you actually show them, preferably during loadings or such

- Anytime an ad will be loaded, the linked signal will be emitted(so ```on_interstitial_loaded``` will be emitted if you load an interstitial)
  >[!NOTE]
  >If an ad could not be loaded for some reason, the linked error signal will be emitted instead(so ```on_interstitial_failed_to_load``` will be emitted if you load an interstitial)
  
- Once the ad is loaded, you can show it with either ```show_interstitial()```, ```show_rewarded_video()``` or ```show_banner()```, depending on the type of ad you loaded
  >[!NOTE]
  >Both interstitial and rewarded ads will emit signals when they are being shown and when the they are being dismissed, you should use those signals to pause and resume your game

<h3>COMPILING</h3>
If you want to compile it yourself (not required):

- Clone this repository
- Open the command line
- Go the root directory of the cloned repository
- Run ```gradlew build```
  
  If everything goes fine, you should find your AARs files in the ```godotadmob\build\outputs\aar``` folder


