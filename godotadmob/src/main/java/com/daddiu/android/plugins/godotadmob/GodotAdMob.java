package com.daddiu.android.plugins.godotadmob;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

import static com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE;


interface FormCallBackListener
{
    void onFormFailedToLoad(int errorCode);
    void onFormDismissed();
    void onFormNotRequired();
}

interface ConsentInformationUpdateListener
{
    void onUpdateFailed(int errorCode);
    void onUpdateSuccessful();
}


public class GodotAdMob extends GodotPlugin {

    enum PersonalizedValues {
        NOT_SET(-1),
        NOT_PERSONALIZED(0),
        PERSONALIZED(1);

        private int value = -1;
        PersonalizedValues(int value)
        {
            this.value = value;
        }
        public int getValue() {
            return value;
        }
    }


    final private String SAVED_ID = "PersonalizedAds";
    private Activity activity = null; // The main activity of the game

    private ConsentInformation consentInformation;
    private ConsentForm consentForm;


    private boolean isReal = false; // Store if is real or not
    private boolean isForChildDirectedTreatment = false; // Store if is children directed treatment desired
    private String maxAdContentRating = ""; // Store maxAdContentRating ("G", "PG", "T" or "MA")
    private Bundle extras = null;

    private FrameLayout layout = null; // Store the layout

    private RewardedVideo rewardedVideo = null; // Rewarded Video object
    private Interstitial interstitial = null; // Interstitial object
    private Banner banner = null; // Banner object

    private boolean initialized = false;

    public GodotAdMob(Godot godot) {
        super(godot);
        this.activity = getActivity();
    }

    // create and add a new layout to Godot
    @Override
    public View onMainCreate(Activity activity) {
        layout = new FrameLayout(activity);
        return layout;

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotAdMob";
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        signals.add(new SignalInfo("on_admob_banner_loaded"));
        signals.add(new SignalInfo("on_admob_banner_failed_to_load", Integer.class));

        signals.add(new SignalInfo("on_interstitial_loaded"));
        signals.add(new SignalInfo("on_interstitial_failed_to_load", Integer.class));
        signals.add(new SignalInfo("on_interstitial_failed_to_show", Integer.class));
        signals.add(new SignalInfo("on_interstitial_opened"));
        signals.add(new SignalInfo("on_interstitial_dismissed"));


        signals.add(new SignalInfo("on_rewarded_video_loaded"));
        signals.add(new SignalInfo("on_rewarded_video_dismissed"));
        signals.add(new SignalInfo("on_rewarded_video_failed_to_load", Integer.class));
        signals.add(new SignalInfo("on_rewarded_video_failed_to_show",Integer.class));
        signals.add(new SignalInfo("on_rewarded_video_opened"));
        signals.add(new SignalInfo("on_rewarded_video_earned_reward", String.class, Integer.class));

        signals.add(new SignalInfo("on_form_loaded"));
        signals.add(new SignalInfo("on_form_dismissed"));
        signals.add(new SignalInfo("on_form_failed_to_load",Integer.class));

        signals.add(new SignalInfo("manage_consent_aborted",Integer.class));

        return signals;
    }

    private void setPersonalizedPreference(int value) {
        SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(SAVED_ID, value);
        editor.apply();
    }

    private boolean isPersonalizedPreferenceSet() {
        SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        int value = sharedPref.getInt(SAVED_ID,PersonalizedValues.NOT_SET.getValue());
        return (value != PersonalizedValues.NOT_SET.getValue());
    }


    private int getPersonalizedPreferenceSaved() {
        SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        int value = sharedPref.getInt(SAVED_ID,PersonalizedValues.NOT_SET.getValue());
        if (value == PersonalizedValues.NOT_SET.getValue()) {
            Log.i("godot","AdMob: Preference not saved yet, returning NOT_SET value");
        }
        return value;
    }

//https://itnext.io/android-admob-consent-with-ump-personalized-or-non-personalized-ads-in-eea-3592e192ec90
    private boolean canShowPersonalizedAds(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
        String purposeConsent = prefs.getString("IABTCF_PurposeConsents", "");
        String vendorConsent = prefs.getString("IABTCF_VendorConsents","");
        String vendorLI = prefs.getString("IABTCF_VendorLegitimateInterests","");
        String purposeLI = prefs.getString("IABTCF_PurposeLegitimateInterests","");

        int googleId = 755;
        boolean hasGoogleVendorConsent = hasAttribute(vendorConsent, googleId);
        boolean hasGoogleVendorLI = hasAttribute(vendorLI, googleId);

        List<Integer> indexes = new ArrayList<>();
        indexes.add(1);
        indexes.add(3);
        indexes.add(4);

        List<Integer> indexesLI = new ArrayList<>();
        indexesLI.add(2);
        indexesLI.add(7);
        indexesLI.add(9);
        indexesLI.add(10);

        return hasConsentFor(indexes, purposeConsent, hasGoogleVendorConsent)
                && hasConsentOrLegitimateInterestFor(indexesLI, purposeConsent, purposeLI, hasGoogleVendorConsent, hasGoogleVendorLI);

    }


    private boolean hasAttribute(String input, int index) {
        if (input == null) return false;
        return input.length() >= index && input.charAt(index-1) == '1';
    }

    private boolean hasConsentFor(List<Integer> indexes, String purposeConsent, boolean hasVendorConsent) {
        for (Integer p: indexes) {
            if (!hasAttribute(purposeConsent, p)) {
                return false;
            }
        }
        return hasVendorConsent;
    }

    private boolean hasConsentOrLegitimateInterestFor(List<Integer> indexes, String purposeConsent, String purposeLI, boolean hasVendorConsent, boolean hasVendorLI){
        for (Integer p: indexes) {
            boolean purposeAndVendorLI = hasAttribute(purposeLI, p) && hasVendorLI;
            boolean purposeConsentAndVendorConsent = hasAttribute(purposeConsent, p) && hasVendorConsent;
            boolean isOk = purposeAndVendorLI || purposeConsentAndVendorConsent;
            if (!isOk){
                return false;
            }
        }
        return true;
    }

    /**
     * Init with content rating additional options
     *
     * @param isReal                      Tell if the environment is for real or test
     * @param maxAdContentRating          must be "G", "PG", "T" or "MA"
     * @param isForChildDirectedTreatment if True, show only ads suitable for children
     */
    @UsedByGodot
    public void init(boolean isReal, String maxAdContentRating, boolean isForChildDirectedTreatment)
    {
        this.isReal = isReal;
        this.maxAdContentRating = maxAdContentRating;
        this.isForChildDirectedTreatment = isForChildDirectedTreatment;
    }

    @UsedByGodot
    public void manageConsents() {
        FormCallBackListener listener = new FormCallBackListener() {
            @Override
            public void onFormFailedToLoad(int errorCode) {
                emitSignal("manage_consent_aborted",errorCode);
            }

            @Override
            public void onFormDismissed() {
                setPersonalizedPreferenceByFunction();
                initWithContentRating();
            }

            @Override
            public void onFormNotRequired() { }
        };

        if (consentInformation == null) {
            updateConsentInformation(new ConsentInformationUpdateListener() {
                @Override
                public void onUpdateFailed(int errorCode) {
                    emitSignal("manage_consent_aborted",errorCode);
                }

                @Override
                public void onUpdateSuccessful() {
                    activity.runOnUiThread(
                            () -> initForm(listener));
                }
            });
        } else {
            activity.runOnUiThread(
                    () -> initForm(listener));
        }

    }


    private void setPersonalizedPreferenceByFunction() {
        int value = canShowPersonalizedAds() ? PersonalizedValues.PERSONALIZED.getValue() : PersonalizedValues.NOT_PERSONALIZED.getValue();
        setPersonalizedPreference(value);
    }

    private void updateConsentInformation(ConsentInformationUpdateListener listener)
    {
/*        ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings
                        .DebugGeography
                        .DEBUG_GEOGRAPHY_NOT_EEA)
                        .addTestDeviceHashedId(getAdMobDeviceId())
                                .build();*/

        ConsentRequestParameters params = new ConsentRequestParameters
                .Builder()
                .setTagForUnderAgeOfConsent(isForChildDirectedTreatment)
                .build();

        Log.i("godot","Updating consent information..");
        ConsentInformation ci = UserMessagingPlatform.getConsentInformation(activity);

        activity.runOnUiThread( () -> ci.requestConsentInfoUpdate(
                activity,
                params,
                new ConsentInformation.OnConsentInfoUpdateSuccessListener() {
                    @Override
                    public void onConsentInfoUpdateSuccess() {
                        Log.i("godot", "AdMob: Consent info Update successfully");
                        consentInformation = ci;
                        listener.onUpdateSuccessful();
                    }
                },
                new ConsentInformation.OnConsentInfoUpdateFailureListener() {
                    @Override
                    public void onConsentInfoUpdateFailure(FormError formError) {
                        Log.i("godot", String.format("AdMob: Consent info update failed (error code: %d)", formError.getErrorCode()));
                        consentInformation = null;
                        listener.onUpdateFailed(formError.getErrorCode());
                    }
                }
        ));
    }

    public void initForm(FormCallBackListener callBackListener) {
        if (consentInformation != null && consentInformation.isConsentFormAvailable()) {
            Log.i("godot", "AdMob: Loading form..");
            activity.runOnUiThread( () ->
                UserMessagingPlatform.loadConsentForm(
                        activity,
                        new UserMessagingPlatform.OnConsentFormLoadSuccessListener() {
                            @Override
                            public void onConsentFormLoadSuccess(ConsentForm consentForm) {
                                Log.i("godot", "AdMob: Consent loaded successfully");
                                emitSignal("on_form_loaded");
                                consentForm.show(
                                        activity,
                                        new ConsentForm.OnConsentFormDismissedListener() {
                                            @Override
                                            public void onConsentFormDismissed(@Nullable FormError formError) {
                                                // Handle dismissal by reloading form.
                                                if (formError != null) {
                                                    Log.i("godot", String.format("AdMob: Form dismissed, error code: %d", formError.getErrorCode()));
                                                    callBackListener.onFormFailedToLoad(formError.getErrorCode());
                                                } else {
                                                    Log.i("godot", "AdMob: Form dismissed");
                                                    emitSignal("on_form_dismissed");
                                                    callBackListener.onFormDismissed();
                                                }
                                            }
                                        });
                            }
                        },
                        new UserMessagingPlatform.OnConsentFormLoadFailureListener() {
                            @Override
                            public void onConsentFormLoadFailure(FormError formError) {
                                Log.i("godot", String.format("AdMob: Form load error: %d", formError.getErrorCode()));
                                emitSignal("on_form_failed_to_load", formError.getErrorCode());
                                callBackListener.onFormFailedToLoad(formError.getErrorCode());
                            }
                        }
            ));
        }
    }

    //To use only if consentinformation is updated properly
    private boolean isFormRequired() {
        return consentInformation.getConsentStatus() == ConsentInformation.ConsentStatus.REQUIRED;
    }

    private void initWithContentRating() {
        activity.runOnUiThread(() -> {
            MobileAds.initialize(activity);

            this.setRequestConfigurations();

            if (!isPersonalizedPreferenceSet()) {
                //NON-EEA USERS ONLY will arrive here without having a preference set and a form not required, so it's safe to assume you can show them personalized ads
                setPersonalizedPreference(PersonalizedValues.PERSONALIZED.getValue());
            }

            int value = getPersonalizedPreferenceSaved();
            boolean bool = (value == PersonalizedValues.PERSONALIZED.getValue());
            if (!bool) {
                if (extras == null) {
                    extras = new Bundle();
                }
                extras.putString("npa", "1");
            }

            Log.i("godot", String.format("AdMob: init with content rating: %s (is real: %s, is child directed : %s, personalized ads: %s) ", maxAdContentRating, isReal, isForChildDirectedTreatment, bool));
            initialized = true;
        });
    }


    private void setRequestConfigurations() {
        RequestConfiguration requestConfiguration = null;
        if (!this.isReal) {
            List<String> testDeviceIds = Arrays.asList(AdRequest.DEVICE_ID_EMULATOR, getAdMobDeviceId());
            requestConfiguration = MobileAds.getRequestConfiguration()
                    .toBuilder()
                    .setTestDeviceIds(testDeviceIds)
                    .build();
        }

        if (this.isForChildDirectedTreatment) {
            requestConfiguration = MobileAds.getRequestConfiguration()
                    .toBuilder()
                    .setTagForChildDirectedTreatment(TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                    .build();
            MobileAds.setRequestConfiguration(requestConfiguration);
        }

        if (this.maxAdContentRating != null && !this.maxAdContentRating.equals("")) {
            requestConfiguration = MobileAds.getRequestConfiguration()
                    .toBuilder()
                    .setMaxAdContentRating(this.maxAdContentRating)
                    .build();
        }
        if (requestConfiguration == null) {
            Log.e("godot","AdMob: RequestConfiguration is empty!");
        }
        MobileAds.setRequestConfiguration(requestConfiguration);

    }


    /**
     * Returns AdRequest object constructed considering the extras.
     *
     * @return AdRequest object
     */
    private AdRequest getAdRequest() {
        AdRequest.Builder adBuilder = new AdRequest.Builder();
        AdRequest adRequest;
        if (!this.isForChildDirectedTreatment && extras != null) {
            adBuilder.addNetworkExtrasBundle(AdMobAdapter.class, extras);
        }

        adRequest = adBuilder.build();
        return adRequest;
    }

    private void tryLoadForm(FormCallBackListener listener) {
        if (isFormRequired())
        {
            activity.runOnUiThread(() -> initForm(listener));
        } else {
            listener.onFormNotRequired();
        }
    }


    /* Rewarded Video
     * ********************************************************************** */

    /**
     * Load a Rewarded Video
     *
     * @param id AdMod Rewarded video ID
     */
    @UsedByGodot
    public void loadRewardedVideo(final String id) {

        FormCallBackListener listener = new FormCallBackListener() {
            @Override
            public void onFormFailedToLoad(int errorCode) {
                emitSignal("on_rewarded_video_failed_to_load", errorCode); //Same signals as actual rewarded to streamline error handling on godot-side
            }

            @Override
            public void onFormDismissed() {
                int value = canShowPersonalizedAds() ? PersonalizedValues.PERSONALIZED.getValue() : PersonalizedValues.NOT_PERSONALIZED.getValue();
                setPersonalizedPreference(value);
                onFormNotRequired();
            }

            @Override
            public void onFormNotRequired() {
                if (!initialized) {
                    initWithContentRating();
                }
                loadRewardedVideoNoForm(id);
            }
        };

        if (consentInformation == null) {
            updateConsentInformation(new ConsentInformationUpdateListener()
            {
                @Override
                public void onUpdateFailed(int errorCode) {
                    emitSignal("on_rewarded_video_failed_to_load",errorCode); //Same signals as actual rewarded to streamline error handling on godot-side
                }

                @Override
                public void onUpdateSuccessful() {
                    tryLoadForm(listener);
                }
            });
        } else {
            tryLoadForm(listener);
        }
    }


    private void loadRewardedVideoNoForm(final String id) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rewardedVideo = new RewardedVideo(activity, new GodotRewardedVideoListener() {
                    @Override
                    public void onRewardedVideoLoaded() {
                        emitSignal("on_rewarded_video_loaded");
                        Log.i("godot", "AdMob: onRewardedVideoLoaded");
                    }

                    @Override
                    public void onRewardedVideoFailedToLoad(LoadAdError errorCode) {
                        emitSignal("on_rewarded_video_failed_to_load", errorCode.getCode());
                        Log.i("godot", String.format("AdMob: onRewardedVideoFailedToLoad (reason: %s)(error code: %d)",errorCode.getMessage(),errorCode.getCode()));
                    }

                    @Override
                    public void onRewardedVideoFailedToShow(AdError errorCode) {
                        emitSignal("on_rewarded_video_failed_to_show", errorCode.getCode());
                        Log.i("godot", String.format("AdMob: onRewardedVideoFailedToShow (reason: %s)(error code: %d)",errorCode.getMessage(),errorCode.getCode()));
                    }

                    @Override
                    public void onRewardedVideoOpened() {
                        emitSignal("on_rewarded_video_opened");
                        Log.i("godot", "AdMob: onRewardedVideoOpened");
                    }

                    @Override
                    public void onRewardedVideoDismissed() {
                        emitSignal("on_rewarded_video_dismissed");
                        Log.i("godot", "AdMob: onRewardedVideoDismissed");
                    }

                    @Override
                    public void onRewardedVideoEarnedReward(String type, int amount) {
                        emitSignal("on_rewarded_video_earned_reward", type, amount);
                        Log.i("godot", String.format("AdMob: onRewardedVideoEarnedReward(%s : %d)",type,amount));
                    }
                });

                rewardedVideo.load(id, getAdRequest());
            }
        });
    }

    /**
     * Show a Rewarded Video
     */
    @UsedByGodot
    public void showRewardedVideo() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (rewardedVideo == null) {
                    return;
                }
                rewardedVideo.show();
            }
        });
    }


    /* Banner
     * ********************************************************************** */

    /**
     * Load a banner
     *
     * @param id      AdMod Banner ID
     * @param isOnTop To made the banner top or bottom
     */
    @UsedByGodot
    public void loadBanner(final String id, final boolean isOnTop, final String bannerSize)
    {
        FormCallBackListener listener = new FormCallBackListener() {
            @Override
            public void onFormFailedToLoad(int errorCode) {
                emitSignal("on_admob_banner_failed_to_load", errorCode); //Same signals as actual banner to streamline error handling on godot-side
            }

            @Override
            public void onFormDismissed() {
                setPersonalizedPreferenceByFunction();
                onFormNotRequired();
            }

            @Override
            public void onFormNotRequired() {
                if (!initialized) {
                    initWithContentRating();
                }
                loadBannerNoForm(id,isOnTop,bannerSize);
            }
        };

        if (consentInformation == null) {
            updateConsentInformation(new ConsentInformationUpdateListener()
            {
                @Override
                public void onUpdateFailed(int errorCode) {
                    emitSignal("on_admob_banner_failed_to_load",errorCode); //Same signals as actual banner to streamline error handling on godot-side
                }

                @Override
                public void onUpdateSuccessful() {
                    tryLoadForm(listener);
                }
            });
        } else {
            tryLoadForm(listener);
        }

    }

    private void loadBannerNoForm(final String id, final boolean isOnTop, final String bannerSize) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (banner != null) banner.remove();
                banner = new Banner(id, getAdRequest(), activity, new BannerListener() {
                    @Override
                    public void onBannerLoaded() {
                        emitSignal("on_admob_banner_loaded");
                        Log.i("godot", "AdMob: OnBannerLoaded");
                    }

                    @Override
                    public void onBannerFailedToLoad(LoadAdError error) {
                        emitSignal("on_admob_banner_failed_to_load", error.getCode());
                        Log.i("godot", String.format("AdMob: onBannerFailedToLoad (reason: %s)(error code: %d)",error.getMessage(),error.getCode()));
                    }
                }, isOnTop, layout, bannerSize);
            }
        });
    }

    /**
     * Show the banner
     */
    @UsedByGodot
    public void showBanner() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (banner != null) {
                    banner.show();
                }
            }
        });
    }

    /**
     * Resize the banner
     * @param isOnTop To made the banner top or bottom
     */
    @UsedByGodot
    public void move(final boolean isOnTop) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (banner != null) {
                    banner.move(isOnTop);
                }
            }
        });
    }

    /**
     * Resize the banner
     */
    @UsedByGodot
    public void resize() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (banner != null) {
                    banner.resize();
                }
            }
        });
    }


    /**
     * Hide the banner
     */
    @UsedByGodot
    public void hideBanner() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (banner != null) {
                    banner.hide();
                }
            }
        });
    }

    /**
     * Get the banner width
     *
     * @return int Banner width
     */
    @UsedByGodot
    public int getBannerWidth() {
        if (banner != null) {
            return banner.getWidth();
        }
        return 0;
    }

    /**
     * Get the banner height
     *
     * @return int Banner height
     */
    @UsedByGodot
    public int getBannerHeight() {
        if (banner != null) {
            return banner.getHeight();
        }
        return 0;
    }

    /* Interstitial
     * ********************************************************************** */

    /**
     * Load a interstitial
     *
     * @param id AdMod Interstitial ID
     */
    @UsedByGodot
    public void loadInterstitial(final String id) {

        FormCallBackListener listener = new FormCallBackListener() {
            @Override
            public void onFormFailedToLoad(int errorCode) {
                emitSignal("on_interstitial_failed_to_load", errorCode); //Same signals as actual interstitial to streamline error handling on godot-side
            }

            @Override
            public void onFormDismissed() {
                int value = canShowPersonalizedAds() ? PersonalizedValues.PERSONALIZED.getValue() : PersonalizedValues.NOT_PERSONALIZED.getValue();
                setPersonalizedPreference(value);
                onFormNotRequired();
            }

            @Override
            public void onFormNotRequired() {
                if (!initialized) {
                    initWithContentRating();
                }
                loadInterstitialNoForm(id);
            }
        };

        if (consentInformation == null) {
            updateConsentInformation(new ConsentInformationUpdateListener()
            {
                @Override
                public void onUpdateFailed(int errorCode) {
                    emitSignal("on_interstitial_failed_to_load",errorCode); //Same signals as actual interstitial to streamline error handling on godot-side
                }

                @Override
                public void onUpdateSuccessful() {
                    tryLoadForm(listener);
                }
            });
        } else {
            tryLoadForm(listener);
        }
    }

    private void loadInterstitialNoForm(final String id) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                interstitial = new Interstitial(activity, new GodotInterstitialListener() {
                    public void onInterstitialLoaded() {
                        emitSignal("on_interstitial_loaded");
                        Log.i("godot", "AdMob: onInterstitialLoaded");
                    }
                    public void onInterstitialFailedToLoad(LoadAdError error) {
                        emitSignal("on_interstitial_failed_to_load",error.getCode());
                        Log.i("godot", String.format("AdMob: onInterstitialFailedToLoad (reason: %s)(error code: %d)",error.getMessage(),error.getCode()));
                    }
                    public void onInterstitialFailedToShow(AdError error) {
                        emitSignal("on_interstitial_failed_to_show", error.getCode());
                        Log.i("godot", String.format("AdMob: onInterstitialFailedToShow (reason: %s)(error code: %d)",error.getMessage(),error.getCode()));
                    }
                    public void onInterstitialOpened(){
                        emitSignal("on_interstitial_opened");
                        Log.i("godot", "onInterstitialOpened");

                    }
                    public void onInterstitialDismissed() {
                        emitSignal("on_interstitial_dismissed");
                        Log.i("godot", "onInterstitialDismissed");
                    }
                });
                interstitial.load(id,getAdRequest());
                }
            });
    }

    /**
     * Show the interstitial
     */
    @UsedByGodot
    public void showInterstitial() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (interstitial != null) {
                    interstitial.show();
                }
            }
        });
    }

    /* Utils
     * ********************************************************************** */

    /**
     * Generate MD5 for the deviceID
     *
     * @param s The string to generate de MD5
     * @return String The MD5 generated
     */
    private String md5(final String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++) {
                String h = Integer.toHexString(0xFF & messageDigest[i]);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            //Logger.logStackTrace(TAG,e);
        }
        return "";
    }

    /**
     * Get the Device ID for AdMob
     *
     * @return String Device ID
     */
    private String getAdMobDeviceId() {
        String android_id = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceId = md5(android_id).toUpperCase(Locale.US);
        return deviceId;
    }

}
