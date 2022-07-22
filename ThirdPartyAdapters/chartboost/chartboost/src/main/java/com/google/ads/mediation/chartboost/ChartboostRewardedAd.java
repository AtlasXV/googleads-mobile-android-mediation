package com.google.ads.mediation.chartboost;

import static com.google.ads.mediation.chartboost.ChartboostMediationAdapter.ERROR_AD_NOT_READY;
import static com.google.ads.mediation.chartboost.ChartboostMediationAdapter.ERROR_DOMAIN;
import static com.google.ads.mediation.chartboost.ChartboostMediationAdapter.ERROR_INVALID_SERVER_PARAMETERS;
import static com.google.ads.mediation.chartboost.ChartboostMediationAdapter.TAG;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.chartboost.sdk.ads.Rewarded;
import com.chartboost.sdk.callbacks.RewardedCallback;
import com.chartboost.sdk.events.CacheError;
import com.chartboost.sdk.events.CacheEvent;
import com.chartboost.sdk.events.ClickError;
import com.chartboost.sdk.events.ClickEvent;
import com.chartboost.sdk.events.DismissEvent;
import com.chartboost.sdk.events.ImpressionEvent;
import com.chartboost.sdk.events.RewardEvent;
import com.chartboost.sdk.events.ShowError;
import com.chartboost.sdk.events.ShowEvent;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAd;
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration;

public class ChartboostRewardedAd implements MediationRewardedAd {

  private Rewarded mChartboostRewardedAd;

  private final MediationRewardedAdConfiguration mRewardedAdConfiguration;
  private final MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>
      mMediationAdLoadCallback;
  private MediationRewardedAdCallback mRewardedAdCallback;

  public ChartboostRewardedAd(
      @NonNull MediationRewardedAdConfiguration mediationRewardedAdConfiguration,
      @NonNull MediationAdLoadCallback<MediationRewardedAd,
          MediationRewardedAdCallback> mediationAdLoadCallback) {
    mRewardedAdConfiguration = mediationRewardedAdConfiguration;
    mMediationAdLoadCallback = mediationAdLoadCallback;
  }

  public void load() {
    final Context context = mRewardedAdConfiguration.getContext();
    Bundle serverParameters = mRewardedAdConfiguration.getServerParameters();

    ChartboostParams mChartboostParams =
        ChartboostAdapterUtils.createChartboostParams(serverParameters, null);
    if (!ChartboostAdapterUtils.isValidChartboostParams(mChartboostParams)) {
      // Invalid server parameters, send ad failed to load event.
      AdError error = new AdError(ERROR_INVALID_SERVER_PARAMETERS, "Invalid server parameters.",
          ERROR_DOMAIN);
      Log.e(TAG, error.toString());
      if (mMediationAdLoadCallback != null) {
        mMediationAdLoadCallback.onFailure(error);
      }
      return;
    }

    final String location = mChartboostParams.getLocation();
    ChartboostInitializer.getInstance()
        .updateCoppaStatus(context, mRewardedAdConfiguration.taggedForChildDirectedTreatment());
    ChartboostInitializer.getInstance()
        .init(context, mChartboostParams, new ChartboostInitializer.Listener() {
          @Override
          public void onInitializationSucceeded() {
            createAndLoadRewardAd(location, mMediationAdLoadCallback);
          }

          @Override
          public void onInitializationFailed(@NonNull AdError error) {
            Log.w(TAG, error.getMessage());
            if (mMediationAdLoadCallback != null) {
              mMediationAdLoadCallback.onFailure(error);
            }
          }
        });
  }

  @Override
  public void showAd(@NonNull Context context) {
    if (mChartboostRewardedAd == null || !mChartboostRewardedAd.isCached()) {
      AdError error = new AdError(ERROR_AD_NOT_READY,
          "Chartboost rewarded ad is not yet ready to be shown.", ERROR_DOMAIN);
      Log.w(TAG, error.getMessage());
      return;
    }
    mChartboostRewardedAd.show();
  }

  private void createAndLoadRewardAd(@Nullable String location,
      @Nullable final MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>
          mMediationAdLoadCallback) {
    if (TextUtils.isEmpty(location)) {
      AdError error = new AdError(ERROR_INVALID_SERVER_PARAMETERS,
          "Missing or Invalid location.", ERROR_DOMAIN);
      Log.w(TAG, error.getMessage());
      if (mMediationAdLoadCallback != null) {
        mMediationAdLoadCallback.onFailure(error);
      }
      return;
    }

    mChartboostRewardedAd = new Rewarded(location,
        new RewardedCallback() {
          @Override
          public void onRewardEarned(@NonNull RewardEvent rewardEvent) {
            Log.d(TAG, "Chartboost rewarded ad user earned a reward.");
            int rewardValue = rewardEvent.getReward();
            if (mRewardedAdCallback != null) {
              mRewardedAdCallback.onVideoComplete();
              mRewardedAdCallback.onUserEarnedReward(new ChartboostReward(rewardValue));
            }
          }

          @Override
          public void onAdDismiss(@NonNull DismissEvent dismissEvent) {
            Log.d(TAG, "Chartboost rewarded ad has been dismissed.");
            if (mRewardedAdCallback != null) {
              mRewardedAdCallback.onAdClosed();
            }
          }

          @Override
          public void onImpressionRecorded(@NonNull ImpressionEvent impressionEvent) {
            Log.d(TAG, "Chartboost rewarded ad impression recorded.");
            if (mRewardedAdCallback != null) {
              mRewardedAdCallback.reportAdImpression();
            }
          }

          @Override
          public void onAdShown(@NonNull ShowEvent showEvent, @Nullable ShowError showError) {
            if (showError == null) {
              Log.d(TAG, "Chartboost rewarded has been shown.");
              if (mRewardedAdCallback != null) {
                mRewardedAdCallback.onAdOpened();
                mRewardedAdCallback.onVideoStart();
              }
            } else {
              AdError error = ChartboostAdapterUtils.createSDKError(showError);
              Log.w(TAG, error.getMessage());
              if (mRewardedAdCallback != null) {
                mRewardedAdCallback.onAdFailedToShow(error);
              }
            }
          }

          @Override
          public void onAdRequestedToShow(@NonNull ShowEvent showEvent) {
            Log.d(TAG, "Chartboost rewarded ad will be shown.");
          }

          @Override
          public void onAdLoaded(@NonNull CacheEvent cacheEvent, @Nullable CacheError cacheError) {
            if (cacheError == null) {
              Log.d(TAG, "Chartboost rewarded ad has been loaded.");
              if (mMediationAdLoadCallback != null) {
                mRewardedAdCallback =
                    mMediationAdLoadCallback.onSuccess(ChartboostRewardedAd.this);
              }
            } else {
              AdError error = ChartboostAdapterUtils.createSDKError(cacheError);
              Log.w(TAG, error.getMessage());
              if (mMediationAdLoadCallback != null) {
                mMediationAdLoadCallback.onFailure(error);
              }
            }
          }

          @Override
          public void onAdClicked(@NonNull ClickEvent clickEvent, @Nullable ClickError clickError) {
            if (clickError == null) {
              Log.d(TAG, "Chartboost rewarded ad has been clicked.");
              if (mRewardedAdCallback != null) {
                mRewardedAdCallback.reportAdClicked();
              }
            } else {
              AdError error = ChartboostAdapterUtils.createSDKError(clickError);
              Log.w(TAG, error.getMessage());
            }
          }
        }, ChartboostAdapterUtils.getChartboostMediation());
    mChartboostRewardedAd.cache();
  }
}
