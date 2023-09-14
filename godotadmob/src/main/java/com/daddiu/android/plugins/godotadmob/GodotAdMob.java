package com.daddiu.android.plugins.godotadmob;

//based on https://github.com/Shin-NiL/Godot-Android-Admob-Plugin/

/*MIT License

        Copyright (c) 2023 Daddiu

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
        SOFTWARE.
*/

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

import static com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE;


interface ConsentInformationUpdateListener
{
    void onUpdateFailed(int errorCode);
    void onUpdateSuccessful();
}

interface FormCallBackListener
{
    void onFormFailedToLoad(int errorCode);
    void onFormSuccessfullyDismissed();
}


public class GodotAdMob extends GodotPlugin {

    private Activity activity = null; // The main activity of the game

    private ConsentInformation consentInformation = null;

    private boolean isReal = false; // Store if is real or not
    private boolean isForChildDirectedTreatment = false; // Store if is children directed treatment desired
    private String maxAdContentRating = ""; // Store maxAdContentRating ("G", "PG", "T" or "MA")

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



        return signals;
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

    private void updateConsentInformation(ConsentInformationUpdateListener listener)
    {
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

    private void initWithContentRating() {
        activity.runOnUiThread(() -> {
            MobileAds.initialize(activity);

            this.setRequestConfigurations();

            Log.i("godot", String.format("AdMob: init with content rating: %s (is real: %s, is child directed : %s) ", maxAdContentRating, isReal, isForChildDirectedTreatment));
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
     * @return AdRequest object
     */
    private AdRequest getAdRequest() {
        AdRequest.Builder adBuilder = new AdRequest.Builder();
        AdRequest adRequest = adBuilder.build();
        return adRequest;
    }

    /**
     * Used on godot side to show only the UMP Form, without the ad after it
     */
    @UsedByGodot
    public void showForm() {
        Log.i("godot","Showing form..");
        initForm(null,true);
    }

    private void initForm(Runnable onSuccessfullyDismissedFun,boolean forceForm) {
        //a bit of weird recursive shenanigans, to see if can be done better
        //if consentInformation isn't updated, it tries to update and if successful then recalls this function with same args
        if (consentInformation == null) {
            updateConsentInformation(new ConsentInformationUpdateListener() {
                @Override
                public void onUpdateFailed(int errorCode) {
                    //fires form_failed_to_load instead of hypothetical "consent_info_update_failed" because godot really shouldn't care which part of it failed
                    emitSignal("on_form_failed_to_load", errorCode);
                }
                @Override
                public void onUpdateSuccessful() {
                    initForm(onSuccessfullyDismissedFun,forceForm);
                }
            });
            return;
        }


        FormCallBackListener callBackListener = new FormCallBackListener() {
            @Override
            public void onFormFailedToLoad(int errorCode) {
                emitSignal("on_form_failed_to_load", errorCode);
            }

            @Override
            public void onFormSuccessfullyDismissed() {
                //at this point, consentInformation should be updated
                if(!consentInformation.canRequestAds()) {
                    Log.w("godot", "AdMob: Can't request ads on this device (consent denied)");
                    return;
                }

                if (!initialized) {
                    initWithContentRating();
                }
                if (onSuccessfullyDismissedFun != null) {
                    onSuccessfullyDismissedFun.run();
                }
                }
            };

        if (!isFormRequired() && !forceForm) {
            callBackListener.onFormSuccessfullyDismissed();
            return;
        }

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
                                                if (formError != null) {
                                                    Log.i("godot", String.format("AdMob: Form dismissed, error code: %d", formError.getErrorCode()));
                                                    callBackListener.onFormFailedToLoad(formError.getErrorCode());
                                                } else {
                                                    Log.i("godot", "AdMob: Form dismissed");
                                                    emitSignal("on_form_dismissed");
                                                    callBackListener.onFormSuccessfullyDismissed();
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

    //To use only if consentinformation is updated properly
    private boolean isFormRequired() {
        return consentInformation.getConsentStatus() == ConsentInformation.ConsentStatus.REQUIRED;
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
        initForm(
                () -> loadRewardedVideoNoForm(id), false
        );
    }


    private void loadRewardedVideoNoForm(final String id) {
        activity.runOnUiThread(() -> {
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
        initForm(
                () -> loadBannerNoForm(id,isOnTop,bannerSize) , false
        );

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
     * @param id AdMob Interstitial ID
     */
    @UsedByGodot
    public void loadInterstitial(final String id) {
        initForm(
                () -> loadInterstitialNoForm(id) , false
        );

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
