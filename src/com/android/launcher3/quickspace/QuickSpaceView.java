/*
 * Copyright (C) 2018 CypherOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.quickspace;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.quickspace.views.DateTextView;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;

import com.android.internal.util.aicp.OmniJawsClient;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherTab;
import com.android.launcher3.R;

public class QuickSpaceView extends FrameLayout implements ValueAnimator.AnimatorUpdateListener,
          OmniJawsClient.OmniJawsObserver, Runnable {

    private static final String TAG = "Launcher3:QuickSpaceView";
    private static final boolean DEBUG = false;

    protected ContentResolver mContentResolver;

    private BubbleTextView mBubbleTextView;
    private DateTextView mClockView;
    private ImageView mWeatherIcon;
    private TextView mWeatherTemp;
    private View mSeparator;
    private ViewGroup mQuickspaceContent;
    private ViewGroup mWeatherContent;

    private final Handler mHandler;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private OmniJawsClient.PackageInfo mPackageInfo;
    private WeatherSettingsObserver mWeatherSettingsObserver;
    private boolean mUpdatesEnabled;

    private QuickSpaceActionReceiver mActionReceiver;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        mHandler = new Handler();
        mWeatherSettingsObserver = new WeatherSettingsObserver(
              mHandler, context.getContentResolver());
        mWeatherSettingsObserver.register();
        mWeatherClient = new OmniJawsClient(context);
        if (mWeatherClient.isOmniJawsEnabled()) {
            mWeatherClient.addSettingsObserver();
            mWeatherClient.addObserver(this);
        }

        mActionReceiver = new QuickSpaceActionReceiver(context);
    }

    private void initListeners() {
        loadSingleLine();
    }

    private void loadSingleLine() {
        setBackgroundResource(0);
        boolean hasGoogleApp = LauncherAppState.getInstanceNoCreate().isSearchAppAvailable();
        boolean hasGoogleCalendar = LauncherAppState.getInstanceNoCreate().isCalendarAppAvailable();
        mClockView.setOnClickListener(hasGoogleCalendar ? mActionReceiver.getCalendarAction() : null);
        mClockView.reloadDateFormat();
        if (!mWeatherClient.isOmniJawsEnabled()) {
            mWeatherContent.setVisibility(View.GONE);
            mSeparator.setVisibility(View.GONE);
            Log.d("QuickSpaceView", "WeatherProvider is unavailable");
            return;
        }
        if (mWeatherInfo == null) {
            mWeatherContent.setVisibility(View.GONE);
            mSeparator.setVisibility(View.GONE);
            Log.d("QuickSpaceView", "WeatherInfo is null");
            return;
        }
        String temperatureText = mWeatherInfo.temp + " " + mWeatherInfo.tempUnits;
        Icon conditionIcon = Icon.createWithResource(mPackageInfo.packageName, mPackageInfo.resourceID);

        mSeparator.setVisibility(View.VISIBLE);
        mWeatherContent.setVisibility(View.VISIBLE);
        mWeatherTemp.setText(temperatureText);
        mWeatherTemp.setOnClickListener(hasGoogleApp ? mActionReceiver.getWeatherAction() : null);
        mWeatherIcon.setImageIcon(conditionIcon);
    }

    private void loadViews() {
        mClockView = findViewById(R.id.clock_view);
        mQuickspaceContent = findViewById(R.id.quickspace_content);
        mSeparator = findViewById(R.id.separator);
        mWeatherIcon = findViewById(R.id.weather_icon);
        mWeatherContent = findViewById(R.id.weather_content);
        mWeatherTemp = findViewById(R.id.weather_temp);

        setTypeface(mClockView, mWeatherTemp);
    }

    private void setTypeface(TextView... views) {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "fonts/GoogleSans-Regular.ttf");
        for (TextView view : views) {
            if (view != null) {
                view.setTypeface(tf);
            }
        }
    }

    public void getQuickSpaceView() {
        boolean visible = mQuickspaceContent.getVisibility() == View.VISIBLE;
        initListeners();
        if (!visible) {
            mQuickspaceContent.setVisibility(View.VISIBLE);
            mQuickspaceContent.setAlpha(0f);
            mQuickspaceContent.animate().setDuration(200L).alpha(1f);
        }
    }

    public void enableUpdates() {
        if (mUpdatesEnabled) {
            return;
        }
        if (DEBUG) Log.d(TAG, "enableUpdates");
        if (mWeatherClient.isOmniJawsEnabled()) {
            mWeatherClient.addSettingsObserver();
            mWeatherClient.addObserver(this);
            queryAndUpdateWeather();
            mUpdatesEnabled = true;
        }
    }

    public void disableUpdates() {
        if (!mUpdatesEnabled) {
            return;
        }
        if (DEBUG) Log.d(TAG, "disableUpdates");
        if (mWeatherClient != null) {
            mWeatherClient.removeObserver(this);
            mWeatherClient.cleanupObserver();
            mUpdatesEnabled = false;
        }
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
        getQuickSpaceView();
    }

    @Override
    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
        mSeparator.setVisibility(View.GONE);
        mWeatherContent.setVisibility(View.GONE);
        mWeatherInfo = null;
    }

    private void queryAndUpdateWeather() {
        try {
            if (DEBUG) Log.d(TAG, "queryAndUpdateWeather.isOmniJawsEnabled " + mWeatherClient.isOmniJawsEnabled());
            mWeatherClient.queryWeather();
            mWeatherInfo = mWeatherClient.getWeatherInfo();
            setPackageInfo();
            if (DEBUG) Log.w(TAG, "queryAndUpdateWeather mPackageName: " + mPackageInfo.packageName);
            if (DEBUG) Log.w(TAG, "queryAndUpdateWeather mDrawableResID: " + mPackageInfo.resourceID);
        } catch(Exception e) {
            // Do nothing
        }
    }

    private void setPackageInfo() {
        mPackageInfo = null;
        if (mWeatherInfo != null){
              Drawable conditionImage = mWeatherClient.getWeatherConditionImage(mWeatherInfo.conditionCode);
              mPackageInfo = mWeatherClient.getPackageInfo();
        }
    }

    public void onAnimationUpdate(final ValueAnimator valueAnimator) {
        invalidate();
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        loadViews();
        mContentResolver = getContext().getContentResolver();
        mBubbleTextView = findViewById(R.id.dummyBubbleTextView);
        mBubbleTextView.setTag(new ItemInfo() {
            @Override
            public ComponentName getTargetComponent() {
                return new ComponentName(getContext(), "");
            }
        });
        mBubbleTextView.setContentDescription("");
    }

    public void onResume() {
        getQuickSpaceView();
    }

    @Override
    public void run() {
        getQuickSpaceView();
    }

    @Override
    public void setPadding(final int n, final int n2, final int n3, final int n4) {
        super.setPadding(0, 0, 0, 0);
    }

    private class WeatherSettingsObserver extends ContentObserver {

         private Handler mHandler;
         private ContentResolver mResolver;

         WeatherSettingsObserver(Handler handler, ContentResolver resolver) {
             super(handler);
             mHandler = handler;
             mResolver = resolver;
         }

         public void register() {
             mResolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.OMNIJAWS_WEATHER_ICON_PACK),
                     false, this, UserHandle.USER_ALL);
         }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.System.getUriFor(Settings.System.OMNIJAWS_WEATHER_ICON_PACK))) {
                queryAndUpdateWeather();
                getQuickSpaceView();
            }
        }
    }
}
