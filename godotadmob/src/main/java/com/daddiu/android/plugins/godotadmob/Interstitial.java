package com.daddiu.android.plugins.godotadmob;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

interface GodotInterstitialListener {
    void onInterstitialLoaded();
    void onInterstitialFailedToLoad(LoadAdError error);
    void onInterstitialFailedToShow(AdError error);
    void onInterstitialOpened();
    void onInterstitialDismissed();
}

public class Interstitial {
    private InterstitialAd mInterstitialAd;
    private Activity mActivity;
    private GodotInterstitialListener mListener;

    public Interstitial(Activity activity, GodotInterstitialListener listener) {
        mActivity = activity;
        mListener = listener;
    }

    public void load(final String id, AdRequest adRequest) {
        InterstitialAd.load(mActivity, id, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                        mListener.onInterstitialLoaded();
                        Log.d("godot", "AdMob: onAdLoaded");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mInterstitialAd = null;
                        mListener.onInterstitialFailedToLoad(loadAdError);
                    }
                });
    }

    public void show() {
        if (mInterstitialAd != null) {
            mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    // Called when fullscreen content is dismissed.
                    mListener.onInterstitialDismissed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    // Called when fullscreen content failed to show.
                    mListener.onInterstitialFailedToShow(adError);
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    mInterstitialAd = null;
                    mListener.onInterstitialOpened();
                }
            });
            mInterstitialAd.show(mActivity);
        }
    }

}
