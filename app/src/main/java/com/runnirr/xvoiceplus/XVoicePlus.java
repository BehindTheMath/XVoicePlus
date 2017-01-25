package com.runnirr.xvoiceplus;

import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.XResources;
import android.telephony.SmsManager;
import android.util.Log;

import com.runnirr.xvoiceplus.hooks.XSmsMethodHook;
import com.runnirr.xvoiceplus.receivers.MessageEventReceiver;

import java.util.ArrayList;
import java.util.HashSet;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

public class XVoicePlus implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static final String TAG = XVoicePlus.class.getName();

    private static final String GOOGLE_VOICE_PACKAGE = "com.google.android.apps.googlevoice";
    private static final String XVOICE_PLUS_PACKAGE = "com.runnirr.xvoiceplus";
    private static final String BROADCAST_SMS_PERMISSION = "android.permission.BROADCAST_SMS";

    public static final String APP_NAME = XVoicePlus.class.getSimpleName();

    private XSmsMethodHook smsManagerHook;

    public boolean isEnabled() {
        return new XSharedPreferences(XVOICE_PLUS_PACKAGE).getBoolean("settings_enabled", true);
    }

    public XVoicePlus() {
        smsManagerHook = new XSmsMethodHook(this);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws ClassNotFoundException {
        // Hooks com.android.* packages
        if (lpparam.packageName.equals("android") && lpparam.processName.equals("android")) {
            hookXVoicePlusPermission(lpparam);

            hookAppOps(lpparam);
        }

        if (GOOGLE_VOICE_PACKAGE.equals(lpparam.packageName)) {
            hookGoogleVoice(lpparam);
        }
    }

    /**
     * Hooks incoming Google Voice messages.
     * @param lpparam
     */
    private void hookGoogleVoice(LoadPackageParam lpparam) {
        Log.d(TAG, "Hooking google voice push notifications");

        findAndHookMethod(GOOGLE_VOICE_PACKAGE + ".PushNotificationReceiver", lpparam.classLoader,
                "onReceive", Context.class, Intent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Log.d(TAG, "Received incoming Google Voice notification");
                        Context context = (Context) param.args[0];
                        Intent gvIntent = (Intent) param.args[1];
                        if (gvIntent != null && gvIntent.getExtras() != null) {
                            // send the incoming message to be processed
                            Intent intent = new Intent()
                                    .setAction(MessageEventReceiver.INCOMING_VOICE)
                                    .putExtras(gvIntent.getExtras());
                            context.sendOrderedBroadcast(intent, null);
                        } else {
                            Log.w(TAG, "Null intent when hooking incoming GV message");
                        }
                    }
                });
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        // Hooks android.* packages
        // Enable SMS on tablets
        XResources.setSystemWideReplacement("android", "bool", "config_sms_capable", true);

        hookSmsManager();
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
     * In order to send the android.provider.Telephony.SMS_DELIVER and android.provider.Telephony.SMS_RECEIVED
     * broadcast intents, the app requires the BROADCAST_SMS permission. However, since this permission
     * is reserved for system packages, it cannot be declared in the manifest. This method hooks the
     * PackageManagerService to grant the permission.
     * @param lpparam
     */
    private void hookXVoicePlusPermission(LoadPackageParam lpparam){
        Log.d(TAG, "Hooking " + APP_NAME + " permissions");

        final Class<?> packageManagerServiceClass = findClass("com.android.server.pm.PackageManagerService", lpparam.classLoader);

        findAndHookMethod(packageManagerServiceClass, "grantPermissionsLPw", "android.content.pm.PackageParser.Package",
                boolean.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final String pkgName = (String) getObjectField(param.args[0], "packageName");

                        if (XVOICE_PLUS_PACKAGE.equals(pkgName)) {
                            // Returns: (PackageSetting) Object PackageParser$Package.mExtras
                            final Object extras = getObjectField(param.args[0], "mExtras");
                            final HashSet<String> grantedPerms =
                                    (HashSet<String>) getObjectField(extras, "grantedPermissions");
                            // Returns: com.android.server.pm.Settings PackageManagerService.mSettings
                            final Object settings = getObjectField(param.thisObject, "mSettings");
                            // Returns: ArrayMap<String, BasePermission> Settings.mPermissions
                            final Object permissions = getObjectField(settings, "mPermissions");

                            // Add android.permission.BROADCAST_SMS to xvoiceplus
                            if (!grantedPerms.contains(BROADCAST_SMS_PERMISSION)) {
                                // Returns: BasePermission
                                final Object pAccessBroadcastSms = callMethod(permissions, "get",
                                        BROADCAST_SMS_PERMISSION);
                                grantedPerms.add(BROADCAST_SMS_PERMISSION);
                                // Returns: ((PackageSetting) GrantedPermissions).gids
                                int[] gpGids = (int[]) getObjectField(extras, "gids");
                                // Returns: BasePermission.gids
                                int[] bpGids = (int[]) getObjectField(pAccessBroadcastSms, "gids");
                                gpGids = (int[]) callStaticMethod(param.thisObject.getClass(),
                                        "appendInts", gpGids, bpGids);
                                Log.d(TAG, "Permission added: " + pAccessBroadcastSms + "; ret=" + gpGids);
                            }
                        }
                    }
                });
    }

    private void hookSmsManager(){
        try {
            findAndHookMethod(SmsManager.class, "sendTextMessage", String.class, String.class,
                    String.class, PendingIntent.class, PendingIntent.class, smsManagerHook);
            findAndHookMethod(SmsManager.class, "sendMultipartTextMessage", String.class, String.class,
                    ArrayList.class, ArrayList.class, ArrayList.class, smsManagerHook);
            Log.d(TAG, "Hooked SmsManager methods successfully");
        } catch (XposedHelpers.ClassNotFoundError e) {
            Log.e(TAG, "Class android.telephony.SmsManager not found", e);
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "SmsManager methods not found", e);
        }
    }
}
