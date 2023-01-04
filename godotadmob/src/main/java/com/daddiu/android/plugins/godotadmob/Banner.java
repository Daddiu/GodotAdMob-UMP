package com.daddiu.android.plugins.godotadmob;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.FrameLayout;

import android.content.res.Configuration;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;


interface BannerListener {
        void onBannerLoaded();
        void onBannerFailedToLoad(LoadAdError error);
    }

    public class Banner {
        private AdView adView = null; // Banner view
        private FrameLayout layout = null;
        private FrameLayout.LayoutParams adParams = null;
        private AdRequest adRequest = null;
        private Activity activity = null;
        private BannerListener defaultBannerListener;
        private String bannerSize;


        public Banner(final String id, final AdRequest adRequest, final Activity activity, final BannerListener defaultBannerListener, final boolean isOnTop, final FrameLayout layout, final String bannerSize) {
            this.activity = activity;
            this.layout = layout;
            this.adRequest = adRequest;
            this.defaultBannerListener = defaultBannerListener;
            this.bannerSize = bannerSize;

            AddBanner(id, (isOnTop ? Gravity.TOP : Gravity.BOTTOM), getAdSize(bannerSize), new AdListener() {
                @Override
                public void onAdLoaded() {
                    Log.w("godot", "AdMob: onAdLoaded");
                    defaultBannerListener.onBannerLoaded();
                }

                @Override
                public void onAdFailedToLoad(LoadAdError adError) {
                    Log.w("godot", "AdMob: onAdFailedToLoad. errorCode: " + adError.getCode());
                    defaultBannerListener.onBannerFailedToLoad(adError);
                }
            });
        }

        public void show() {
            if (adView == null) {
                Log.w("w", "AdMob: showBanner - banner not loaded");
                return;
            }

            if (adView.getVisibility() == View.VISIBLE) {
                return;
            }

            adView.setVisibility(View.VISIBLE);
            adView.resume();
            Log.i("godot", "AdMob: Show Banner");
        }

        public void move(final boolean isOnTop)
        {
            if (layout == null || adView == null || adParams == null) {
                return;
            }

            layout.removeView(adView); // Remove the old view

            AdListener adListener = adView.getAdListener();
            String id = adView.getAdUnitId();
            AddBanner(id, (isOnTop ? Gravity.TOP : Gravity.BOTTOM), adView.getAdSize(), adListener);

            Log.i("godot", "AdMob: Banner Moved");
        }

        public void resize() {
            if (layout == null || adView == null || adParams == null) {
                return;
            }

            layout.removeView(adView); // Remove the old view

            AdListener adListener = adView.getAdListener();
            String id = adView.getAdUnitId();
            AddBanner(id, adParams.gravity, getAdSize(bannerSize), adListener);

            Log.i("godot", "AdMob: Banner Resized");
        }

        private void AddBanner(final String id, final int gravity, final AdSize size, final AdListener listener) {
            adParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            adParams.gravity = gravity;

            // Create new view & set old params
            adView = new AdView(activity);
            adView.setAdUnitId(id);
            adView.setBackgroundColor(Color.TRANSPARENT);
            adView.setAdSize(size);
            adView.setAdListener(listener);

            // Add to layout and load ad
            layout.addView(adView, adParams);

            // Request
            adView.loadAd(adRequest);
        }

        public void remove() {
            if (adView != null) {
                layout.removeView(adView); // Remove the old view
            }
        }

        public void hide() {
            if (adView.getVisibility() == View.GONE) return;
            adView.setVisibility(View.GONE);
            adView.pause();
            Log.i("godot", "AdMob: Hide Banner");
        }

        private AdSize getAdaptiveAdSize() {
            // Determine the screen width (less decorations) to use for the ad width.
            Display display = null;
            float widthPixels = 0;
            float density = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display = activity.getDisplay();
                WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
                // Gets all excluding insets
                final WindowInsets windowInsets = metrics.getWindowInsets();
                Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()
                                        | WindowInsets.Type.displayCutout());

                int insetsWidth = insets.right + insets.left;
                int insetsHeight = insets.top + insets.bottom;
                // Legacy size that Display#getSize reports
                final Rect bounds = metrics.getBounds();
                widthPixels = bounds.width();
                density =  activity.getResources().getConfiguration().densityDpi;

            } else {
                display = activity.getWindowManager().getDefaultDisplay();
                DisplayMetrics outMetrics = new DisplayMetrics();
                display.getMetrics(outMetrics);
                widthPixels = outMetrics.widthPixels;
                density = outMetrics.density;
            }

            int adWidth = (int) (widthPixels / density);

            // Get adaptive ad size and return for setting on the ad view.
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
        }

        private AdSize getAdSize(final String bannerSize) {
            switch (bannerSize) {
                case "SMART_BANNER":
                    return AdSize.SMART_BANNER;
                case "BANNER":
                    return AdSize.BANNER;
                case "LARGE_BANNER":
                    return AdSize.LARGE_BANNER;
                case "MEDIUM_RECTANGLE":
                    return AdSize.MEDIUM_RECTANGLE;
                case "FULL_BANNER":
                    return AdSize.FULL_BANNER;
                case "LEADERBOARD":
                    return AdSize.LEADERBOARD;
                default:
                    return getAdaptiveAdSize();
            }
        }

        public int getWidth() {
            return getAdSize(bannerSize).getWidthInPixels(activity);
        }

        public int getHeight() {
            return getAdSize(bannerSize).getHeightInPixels(activity);
        }


}
