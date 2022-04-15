package com.daddiu.android.plugins.godotadmob;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;


interface GodotRewardedVideoListener {
   void onRewardedVideoLoaded();
   void onRewardedVideoFailedToLoad(LoadAdError error);
   void onRewardedVideoFailedToShow(AdError error);
   void onRewardedVideoOpened();
   void onRewardedVideoDismissed();
   void onRewardedVideoEarnedReward(String type, int amount);
}

public class RewardedVideo {

    private RewardedAd mRewardedAd;
    private Activity mActivity;
    private GodotRewardedVideoListener mListener;


    public RewardedVideo(Activity activity, GodotRewardedVideoListener listener) {
        mActivity = activity;
        mListener = listener;
    }


    public void load(final String id, AdRequest adRequest) {
        RewardedAd.load(mActivity, id,
                adRequest, new RewardedAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mRewardedAd = null;
                        mListener.onRewardedVideoFailedToLoad(loadAdError);
                    }
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        mRewardedAd = rewardedAd;
                        mListener.onRewardedVideoLoaded();
                    }
                });
    }

    public void show() {
        if (mRewardedAd != null) {
            mRewardedAd.setFullScreenContentCallback(
                    new FullScreenContentCallback() {
                        @Override
                        public void onAdShowedFullScreenContent() {
                            // Called when ad is shown.
                            Log.i("godot", "AdMob: onAdShowedFullScreenContent");
                            mListener.onRewardedVideoOpened();
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(AdError adError) {
                            // Called when ad fails to show.
                            Log.i("godot", "AdMob:" + String.format("onAdFailedToShowFullScreenContent, error: %d" ,adError.getCode()));
                            mRewardedAd = null;
                            mListener.onRewardedVideoFailedToShow(adError);
                        }

                        @Override
                        public void onAdDismissedFullScreenContent() {
                            // Called when ad is dismissed.
                            // Don't forget to set the ad reference to null so you
                            // don't show the ad a second time.
                            mRewardedAd = null;
                            Log.i("godot", "AdMob: onAdDismissedFullScreenContent");
                            //MainActivity.this.loadRewardedAd();
                            mListener.onRewardedVideoDismissed();
                        }
                    });

            mRewardedAd.show(mActivity, new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                    // Handle the reward.
                    Log.i("godot", "AdMob: onUserEarnedReward");
                    int rewardAmount = rewardItem.getAmount();
                    String rewardType = rewardItem.getType();
                    mListener.onRewardedVideoEarnedReward(rewardType,rewardAmount);
                }
            });
        } else {
            Log.i("godot", "AdMob: The rewarded ad wasn't ready yet.");
        }
    }

}
