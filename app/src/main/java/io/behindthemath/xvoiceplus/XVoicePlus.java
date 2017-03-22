package io.behindthemath.xvoiceplus;

import android.app.AndroidAppHelper;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.XResources;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.telephony.SmsManager;
import android.util.Log;

import com.crossbowffs.remotepreferences.RemotePreferenceAccessException;
import com.crossbowffs.remotepreferences.RemotePreferences;

import java.util.ArrayList;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import io.behindthemath.xvoiceplus.hooks.GCMListenerServiceHook;
import io.behindthemath.xvoiceplus.hooks.XSendSmsMethodHook;
import io.behindthemath.xvoiceplus.receivers.MessageEventReceiver;

import static de.robv.android.xposed.XposedHelpers.ClassNotFoundError;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

public class XVoicePlus implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static final String TAG = XVoicePlus.class.getSimpleName();

    public static final String XVOICE_PLUS_PACKAGE = BuildConfig.APPLICATION_ID;
    public static final String APP_NAME = TAG;
    public static final String XVOICE_PLUS_PREFERENCES_FILE_NAME = XVOICE_PLUS_PACKAGE + "_preferences";

    public static final String LEGACY_GOOGLE_VOICE_PACKAGE = "com.google.android.apps.googlevoice";
    public static final String NEW_GOOGLE_VOICE_PACKAGE = "com.google.android.apps.voice";
    private static final String NEW_GOOGLE_VOICE_GCM_PACKAGE = NEW_GOOGLE_VOICE_PACKAGE + ".backends.gcm";

    private static final String BROADCAST_SMS_PERMISSION = "android.permission.BROADCAST_SMS";


    public static boolean isEnabled() {
        //return new XSharedPreferences(XVOICE_PLUS_PLUS_PACKAGE).getBoolean("settings_enabled", false);

        Context appContext = AndroidAppHelper.currentApplication().getApplicationContext();
        boolean settingsEnabled;

        try {
            settingsEnabled = new RemotePreferences(appContext, XVOICE_PLUS_PACKAGE, XVOICE_PLUS_PREFERENCES_FILE_NAME, true)
                    .getBoolean("settings_enabled", false);
        } catch (RemotePreferenceAccessException e) {
            Log.e(TAG, "Error accessing settings_enabled from RemotePreferences: ", e);
            return false;
        }

        return settingsEnabled;
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        // Hooks android.* packages

        // Enable SMS on tablets
        XResources.setSystemWideReplacement("android", "bool", "config_sms_capable", true);

        hookSmsManager();
    }

    /**
     * Hooks {@link SmsManager} to intercept outgoing messages.
     */
    private void hookSmsManager() {
        Log.d(TAG, "Attempting to hook SmsManager");

        try {
            findAndHookMethod(SmsManager.class, "sendTextMessage", String.class, String.class,
                    String.class, PendingIntent.class, PendingIntent.class, new XSendSmsMethodHook());
            findAndHookMethod(SmsManager.class, "sendMultipartTextMessage", String.class, String.class,
                    ArrayList.class, ArrayList.class, ArrayList.class, new XSendSmsMethodHook());

            Log.d(TAG, "Hooked SmsManager methods successfully");
        } catch (ClassNotFoundError e) {
            Log.e(TAG, "Class android.telephony.SmsManager not found", e);
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "SmsManager methods not found", e);
        }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws ClassNotFoundException {
        // Hooks com.android.* packages
        if (lpparam.packageName.equals("android") && lpparam.processName.equals("android")) {
            hookXVoicePlusPermissions(lpparam);

            hookAppOps(lpparam);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hookBroadcastPermissionCheck(lpparam);
            }
        }

        if (LEGACY_GOOGLE_VOICE_PACKAGE.equals(lpparam.packageName)) {
            hookGoogleVoice(lpparam);
        }
    }

    /**
     * In order to send the android.provider.Telephony.SMS_DELIVER and android.provider.Telephony.SMS_RECEIVED
     * broadcast intents, the app requires the BROADCAST_SMS permission. However, since this permission
     * is reserved for system packages, it cannot be declared in the manifest. This method hooks the
     * PackageManagerService to grant the permission.
     * @param lpparam
     */
    private void hookXVoicePlusPermissions(LoadPackageParam lpparam){
        Log.d(TAG, "Hooking " + APP_NAME + " permissions");

        final Class<?> packageManagerServiceClass = findClass("com.android.server.pm.PackageManagerService", lpparam.classLoader);

        findAndHookMethod(packageManagerServiceClass, "grantPermissionsLPw", "android.content.pm.PackageParser.Package",
                boolean.class, String.class, new XC_MethodHook() {
                    @SuppressWarnings("unchecked")
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final String pkgName = (String) getObjectField(param.args[0], "packageName");

                        if (XVOICE_PLUS_PACKAGE.equals(pkgName)) {
                            Log.d(TAG, "Fixing permissions for " + XVOICE_PLUS_PACKAGE);

                            // Returns: (PackageSetting) Object PackageParser$Package.mExtras
                            final Object extras = getObjectField(param.args[0], "mExtras");

                            // Returns: com.android.server.pm.Settings PackageManagerService.mSettings
                            final Object settings = getObjectField(param.thisObject, "mSettings");

                            // Returns: ArrayMap<String, BasePermission> Settings.mPermissions
                            final Object permissions = getObjectField(settings, "mPermissions");

                            // Returns: BasePermission
                            final Object broadcastSmsPermission = callMethod(permissions, "get",
                                    BROADCAST_SMS_PERMISSION);

                            if ((Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) || (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1)) {
                                final Set<String> grantedPermissions = (Set<String>) getObjectField(extras, "grantedPermissions");

                                if (!grantedPermissions.contains(BROADCAST_SMS_PERMISSION)) {
                                    grantedPermissions.add(BROADCAST_SMS_PERMISSION);

                                    // Returns: ((PackageSetting) GrantedPermissions).gids
                                    int[] grantedPermissionsGids = (int[]) getObjectField(extras, "gids");

                                    // Returns: BasePermission.gids
                                    int[] broadcastSmsPermissionGids = (int[]) getObjectField(broadcastSmsPermission, "gids");
                                    callStaticMethod(param.thisObject.getClass(), "appendInts", grantedPermissionsGids, broadcastSmsPermissionGids);
                                    Log.d(TAG, "Permission added: " + broadcastSmsPermission);
                                }
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                // Based on https://github.com/wasdennnoch/AndroidN-ify/blob/ebb3a60a155b30dc177cf4968cb28d1254171851/app/src/main/java/tk/wasdennnoch/androidn_ify/utils/PermissionGranter.java#L74-#L89
                                // and https://github.com/GravityBox/GravityBox/blob/marshmallow/src/com/ceco/marshmallow/gravitybox/PermissionGranter.java
                                final Object permissionsState = callMethod(extras, "getPermissionsState");

                                if (!(boolean) callMethod(permissionsState, "hasInstallPermission", BROADCAST_SMS_PERMISSION)) {
                                    // Try granting the permission
                                    int ret = (int) callMethod(permissionsState, "grantInstallPermission", broadcastSmsPermission);

                                    // com.android.server.pm.PermissionsState.PERMISSION_OPERATION_FAILURE
                                    final int PERMISSION_OPERATION_FAILURE = -1;

                                    if (ret != PERMISSION_OPERATION_FAILURE) {
                                        Log.d(TAG, "Permission added: " + broadcastSmsPermission);
                                    } else {
                                        Log.w(TAG, "Error adding permission: " + broadcastSmsPermission);
                                    }
                                }
                            }
                        }
                    }
                });
    }

    private void hookAppOps(LoadPackageParam lpparam) {
        Log.d(TAG, "Hooking App Ops");

        XposedBridge.hookAllConstructors(findClass("com.android.server.AppOpsService.Op", lpparam.classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if(XVOICE_PLUS_PACKAGE.equals(param.args[1]) &&
                                (Integer) param.args[2] == SmsUtils.OP_WRITE_SMS) {
                            Log.d(TAG, "App Ops hook: Setting OP_WRITE_SMS to MODE_ALLOWED");
                            setIntField(param.thisObject, "mode", AppOpsManager.MODE_ALLOWED);
                        }
                    }
                });
    }

    /**
     * Hooks com.android.server.am.ActivityManagerService.broadcastIntentLocked()
     *
     * Once we hooked the incoming message from Google Voice, we use the android.provider.Telephony.SMS_DELIVER
     * intent to spoof an incoming SMS. However, in Marshmallow this is a protected broadcast, so
     * it's only available to be sent by system packages. As a workaround, we hook the method that
     * checks if the broadcast is protected, and spoof the broadcast as if it's coming from PHONE_UID.
     *
     * @param lpparam
     */
    private void hookBroadcastPermissionCheck(final LoadPackageParam lpparam) {
        Log.d(TAG, "Hooking broadcast permissions");

        findAndHookMethod("com.android.server.am.ActivityManagerService", lpparam.classLoader, "broadcastIntentLocked",
                "com.android.server.am.ProcessRecord", String.class, Intent.class, String.class,
                "android.content.IIntentReceiver", int.class, String.class, Bundle.class, (new String[0]).getClass(),
                int.class, Bundle.class, boolean.class, boolean.class, int.class, int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Index of the callingUid argument
                        final int ARGUMENT_INDEX_CALLING_UID = 14;
                        int callingUid = (int) param.args[ARGUMENT_INDEX_CALLING_UID];
                        // Get our UID
                        int appUid = AndroidAppHelper.currentApplication().getPackageManager()
                                .getApplicationInfo(XVOICE_PLUS_PACKAGE, PackageManager.GET_META_DATA).uid;
                        // If the broadcast is from us
                        if ((boolean) callStaticMethod(UserHandle.class, "isSameApp", callingUid, appUid)) {
                            Log.d(TAG, "Hooking broadcast permissions: Overriding callingUid "
                                    + callingUid + " with Process.PHONE_UID (UID 1001)");
                            // Spoof the broadcast as if it's coming from PHONE_UID, so the system will let it through
                            param.args[ARGUMENT_INDEX_CALLING_UID] = Process.PHONE_UID;
                        }
                    }
                });
    }

    /**
     * Hooks incoming Google Voice messages.
     * @param lpparam
     */
    private void hookGoogleVoice(LoadPackageParam lpparam) {
        Log.d(TAG, "Hooking Google Voice incoming push notifications");

        // Try to hook GV 5.0+ first
        try {
            findAndHookMethod(NEW_GOOGLE_VOICE_GCM_PACKAGE + ".GcmListenerService", lpparam.classLoader,
                    "a", String.class, Bundle.class, new GCMListenerServiceHook());
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "Error hooking GV 5.0+: ", e);
        } catch (ClassNotFoundError e) {
            Log.d(TAG, "GV 5.0+ was not found. Trying to hook legacy GV (version 0.4.7.10 or lower)");

            // If we can't find GV 5.0+, try to hook the legacy version (0.4.7.10 or lower)
            try {
                findAndHookMethod(LEGACY_GOOGLE_VOICE_PACKAGE + ".PushNotificationReceiver", lpparam.classLoader,
                        "onReceive", Context.class, Intent.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Log.d(TAG, "Received incoming Google Voice notification");

                                if (isEnabled()) {
                                    Context context = (Context) param.args[0];
                                    Intent gvIntent = (Intent) param.args[1];

                                    if (gvIntent == null || gvIntent.getExtras() == null) {
                                        Log.w(TAG, "Null intent when hooking incoming GV message");
                                    } else if (gvIntent.getExtras().getString("sender_address") == null) {
                                        Log.d(TAG, "sender_address == null, so this must be a dummy intent, so we'll ignore it");
                                    } else {
                                        // Send the incoming message to be processed
                                        Intent intent = new Intent()
                                                .setAction(MessageEventReceiver.INCOMING_VOICE)
                                                .putExtras(gvIntent.getExtras());
                                        context.sendOrderedBroadcast(intent, null);
                                    }
                                } else {
                                    Log.d(TAG, "Module is disabled, so ignoring the incoming message");
                                }
                            }
                        });
            } catch (NoSuchMethodError error) {
                Log.e(TAG, "Error hooking legacy GV (version 0.4.7.10 or lower): ", error);
            } catch (ClassNotFoundError error) {
                Log.e(TAG, "Google Voice could not be found. Incoming messages will not be intercepted", error);
            }
        }
    }
}
