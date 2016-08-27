/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package xyz.pwrtelegram.messenger.browser;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import xyz.pwrtelegram.messenger.ApplicationLoader;
import xyz.pwrtelegram.messenger.FileLog;
import xyz.pwrtelegram.messenger.LocaleController;
import xyz.pwrtelegram.messenger.MediaController;
import xyz.pwrtelegram.messenger.R;
import xyz.pwrtelegram.messenger.ShareBroadcastReceiver;
import xyz.pwrtelegram.messenger.support.customtabs.CustomTabsCallback;
import xyz.pwrtelegram.messenger.support.customtabs.CustomTabsClient;
import xyz.pwrtelegram.messenger.support.customtabs.CustomTabsIntent;
import xyz.pwrtelegram.messenger.support.customtabs.CustomTabsServiceConnection;
import xyz.pwrtelegram.messenger.support.customtabs.CustomTabsSession;
import xyz.pwrtelegram.messenger.support.customtabsclient.shared.CustomTabsHelper;
import xyz.pwrtelegram.messenger.support.customtabsclient.shared.ServiceConnection;
import xyz.pwrtelegram.messenger.support.customtabsclient.shared.ServiceConnectionCallback;
import xyz.pwrtelegram.ui.ActionBar.Theme;
import xyz.pwrtelegram.ui.LaunchActivity;

import java.lang.ref.WeakReference;

public class Browser {

    private static WeakReference<CustomTabsSession> customTabsCurrentSession;
    private static CustomTabsSession customTabsSession;
    private static CustomTabsClient customTabsClient;
    private static CustomTabsServiceConnection customTabsServiceConnection;
    private static String customTabsPackageToBind;
    private static WeakReference<Activity> currentCustomTabsActivity;

    private static CustomTabsSession getCurrentSession() {
        return customTabsCurrentSession == null ? null : customTabsCurrentSession.get();
    }

    private static void setCurrentSession(CustomTabsSession session) {
        customTabsCurrentSession = new WeakReference<>(session);
    }

    private static CustomTabsSession getSession() {
        if (customTabsClient == null) {
            customTabsSession = null;
        } else if (customTabsSession == null) {
            customTabsSession = customTabsClient.newSession(new NavigationCallback());
            setCurrentSession(customTabsSession);
        }
        return customTabsSession;
    }

    public static void bindCustomTabsService(Activity activity) {
        if (Build.VERSION.SDK_INT < 15) {
            return;
        }
        Activity currentActivity = currentCustomTabsActivity == null ? null : currentCustomTabsActivity.get();
        if (currentActivity != null && currentActivity != activity) {
            unbindCustomTabsService(currentActivity);
        }
        if (customTabsClient != null) {
            return;
        }
        currentCustomTabsActivity = new WeakReference<>(activity);
        try {
            if (TextUtils.isEmpty(customTabsPackageToBind)) {
                customTabsPackageToBind = CustomTabsHelper.getPackageNameToUse(activity);
                if (customTabsPackageToBind == null) {
                    return;
                }
            }
            customTabsServiceConnection = new ServiceConnection(new ServiceConnectionCallback() {
                @Override
                public void onServiceConnected(CustomTabsClient client) {
                    customTabsClient = client;
                    if (MediaController.getInstance().canCustomTabs()) {
                        if (customTabsClient != null) {
                            try {
                                customTabsClient.warmup(0);
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    }
                }

                @Override
                public void onServiceDisconnected() {
                    customTabsClient = null;
                }
            });
            if (!CustomTabsClient.bindCustomTabsService(activity, customTabsPackageToBind, customTabsServiceConnection)) {
                customTabsServiceConnection = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static void unbindCustomTabsService(Activity activity) {
        if (Build.VERSION.SDK_INT < 15 || customTabsServiceConnection == null) {
            return;
        }
        Activity currentActivity = currentCustomTabsActivity == null ? null : currentCustomTabsActivity.get();
        if (currentActivity == activity) {
            currentCustomTabsActivity.clear();
        }
        try {
            activity.unbindService(customTabsServiceConnection);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        customTabsClient = null;
        customTabsSession = null;
    }

    private static class NavigationCallback extends CustomTabsCallback {
        @Override
        public void onNavigationEvent(int navigationEvent, Bundle extras) {
            FileLog.e("tmessages", "code = " + navigationEvent + " extras " + extras);
        }
    }

    public static void openUrl(Context context, String url) {
        openUrl(context, Uri.parse(url), true);
    }

    public static void openUrl(Context context, Uri uri) {
        openUrl(context, uri, true);
    }

    public static void openUrl(Context context, String url, boolean allowCustom) {
        if (context == null || url == null) {
            return;
        }
        openUrl(context, Uri.parse(url), allowCustom);
    }

    public static void openUrl(Context context, Uri uri, boolean allowCustom) {
        if (context == null || uri == null) {
            return;
        }

        try {
            boolean internalUri = isInternalUri(uri);
            if (Build.VERSION.SDK_INT >= 15 && allowCustom && MediaController.getInstance().canCustomTabs() && !internalUri) {
                Intent share = new Intent(ApplicationLoader.applicationContext, ShareBroadcastReceiver.class);
                share.setAction(Intent.ACTION_SEND);

                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(getSession());
                builder.setToolbarColor(Theme.ACTION_BAR_COLOR);
                builder.setShowTitle(true);
                builder.setActionButton(BitmapFactory.decodeResource(context.getResources(), R.drawable.abc_ic_menu_share_mtrl_alpha), LocaleController.getString("ShareFile", R.string.ShareFile), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 0, share, 0), false);
                CustomTabsIntent intent = builder.build();
                intent.launchUrl((Activity) context, uri);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                if (internalUri) {
                    ComponentName componentName = new ComponentName(context.getPackageName(), LaunchActivity.class.getName());
                    intent.setComponent(componentName);
                }
                intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, context.getPackageName());
                context.startActivity(intent);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static boolean isInternalUrl(String url) {
        return isInternalUri(Uri.parse(url));
    }

    public static boolean isInternalUri(Uri uri) {
        String host = uri.getHost();
        host = host != null ? host.toLowerCase() : "";
        return "tg".equals(uri.getScheme()) || "telegram.me".equals(host) || "telegram.dog".equals(host);
    }
}
