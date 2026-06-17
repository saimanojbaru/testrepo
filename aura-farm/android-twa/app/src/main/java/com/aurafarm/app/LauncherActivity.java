package com.aurafarm.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;

import java.util.List;

/**
 * LauncherActivity attempts to launch the PWA as a Trusted Web Activity.
 * If Chrome or a TWA-capable browser is not available, it falls back to
 * a simple WebView wrapper.
 */
public class LauncherActivity extends Activity {

    private static final String TAG = "AuraFarmTWA";
    private static final String LAUNCH_URL = "https://aura-farm-pwa.netlify.app";
    private static final int THEME_COLOR = 0xFF0A0A0A;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isTwaSupported()) {
            launchTwa();
        } else {
            launchFallback();
        }
    }

    private boolean isTwaSupported() {
        try {
            // Check if there's a browser that supports Custom Tabs
            Intent serviceIntent = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
            List<ResolveInfo> resolveInfos = getPackageManager().queryIntentServices(
                    serviceIntent, PackageManager.MATCH_DEFAULT_ONLY);
            return resolveInfos != null && !resolveInfos.isEmpty();
        } catch (Exception e) {
            Log.w(TAG, "Error checking TWA support", e);
            return false;
        }
    }

    private void launchTwa() {
        try {
            Uri launchUri = Uri.parse(LAUNCH_URL);

            CustomTabColorSchemeParams colorSchemeParams = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(THEME_COLOR)
                    .setNavigationBarColor(THEME_COLOR)
                    .build();

            TrustedWebActivityIntentBuilder builder = new TrustedWebActivityIntentBuilder(launchUri)
                    .setDefaultColorSchemeParams(colorSchemeParams);

            // Build and launch the TWA intent
            Intent twaIntent = builder.buildCustomTabsIntent().intent;
            twaIntent.setData(launchUri);
            twaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(twaIntent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch TWA, falling back to WebView", e);
            launchFallback();
        }
    }

    private void launchFallback() {
        Intent intent = new Intent(this, FallbackWebViewActivity.class);
        intent.putExtra(FallbackWebViewActivity.EXTRA_URL, LAUNCH_URL);
        startActivity(intent);
        finish();
    }
}
