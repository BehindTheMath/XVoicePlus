package com.runnirr.xvoiceplus;


import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.XResources;
import android.os.Build;
import android.util.Log;
import com.runnirr.xvoiceplus.hooks.XSmsMethodHook;
import com.runnirr.xvoiceplus.receivers.MessageEventReceiver;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.util.ArrayList;
import java.util.HashSet;

import static de.robv.android.xposed.XposedHelpers.*;

public class XVoicePlus implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static final String TAG = XVoicePlus.class.getName();

    private static final String GOOGLE_VOICE_PACKAGE = "com.google.android.apps.googlevoice";
    private static final String XVOICE_PLUS_PACKAGE = "com.runnirr.xvoiceplus";
    private static final String PERM_BROADCAST_SMS = "android.permission.BROADCAST_SMS";

    private XSmsMethodHook smsManagerHook;

    public boolean isEnabled() {
        return new XSharedPreferences("com.runnirr.xvoiceplus").getBoolean("settings_enabled", true);
    }

    public XVoicePlus() {
        smsManagerHook = new XSmsMethodHook(this);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws ClassNotFoundException {
        if (lpparam.packageName.equals(GOOGLE_VOICE_PACKAGE)) {
            Log.d(TAG, "Hooking google voice push notifications");
            hookGoogleVoice(lpparam);
        }
    }

    private void hookGoogleVoice(LoadPackageParam lpparam) {
        findAndHookMethod(GOOGLE_VOICE_PACKAGE + ".PushNotificationReceiver", lpparam.classLoader,
                "onReceive", Context.class, Intent.class,
                new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.d(TAG, "Received incoming Google Voice notification");
                Context context = (Context) param.args[0];
                Intent gvIntent = (Intent) param.args[1];
                if (gvIntent != null && gvIntent.getExtras() != null) {
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
        XResources.setSystemWideReplacement("android", "bool", "config_sms_capable", true);

        hookXVoicePlusPermission();
        hookSmsManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            hookAppOps();
        }
    }

    @TargetApi(19)
    private void hookAppOps() {
        Log.d(TAG, "Hooking app ops");

        XposedBridge.hookAllConstructors(findClass("com.android.server.AppOpsService.Op", null),
                new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if(XVOICE_PLUS_PACKAGE.equals(param.args[1]) &&
                        (Integer) param.args[2] == SmsUtils.OP_WRITE_SMS) {

                    setIntField(param.thisObject, "mode", AppOpsManager.MODE_ALLOWED);
                }
            }

        });
    }

    private void hookXVoicePlusPermission(){
        final Class<?> pmServiceClass = findClass("com.android.server.pm.PackageManagerService", null);

        findAndHookMethod(pmServiceClass, "grantPermissionsLPw",
                "android.content.pm.PackageParser.Package", boolean.class, new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final String pkgName = (String) getObjectField(param.args[0], "packageName");

                if (XVOICE_PLUS_PACKAGE.equals(pkgName)) {
                    final Object extras = getObjectField(param.args[0], "mExtras");
                    final HashSet<String> grantedPerms = 
                            (HashSet<String>) getObjectField(extras, "grantedPermissions");
                    final Object settings = getObjectField(param.thisObject, "mSettings");
                    final Object permissions = getObjectField(settings, "mPermissions");

                    // Add android.permission.BROADCAST_SMS to xvoiceplus
                    if (!grantedPerms.contains(PERM_BROADCAST_SMS)) {
                        final Object pAccessBroadcastSms = callMethod(permissions, "get",
                                PERM_BROADCAST_SMS);
                        grantedPerms.add(PERM_BROADCAST_SMS);
                        int[] gpGids = (int[]) getObjectField(extras, "gids");
                        int[] bpGids = (int[]) getObjectField(pAccessBroadcastSms, "gids");
                        gpGids = (int[]) callStaticMethod(param.thisObject.getClass(),
                                "appendInts", gpGids, bpGids);
                    }
                }
            }
        });
    }

    private void hookSmsManager(){
        Class clazz = findClass("android.telephony.SmsManager", null);
        try {
            findAndHookMethod(clazz, "sendTextMessage",
                    String.class, String.class, String.class, PendingIntent.class, PendingIntent.class,
                    smsManagerHook);
            findAndHookMethod(clazz, "sendMultipartTextMessage",
                    String.class, String.class, ArrayList.class, ArrayList.class, ArrayList.class,
                    smsManagerHook);
            Log.d(TAG, "Hooked standard SmsManager methods");
        } catch(NoSuchMethodError ex) {
            Log.w(TAG, "Failed to hook standard SmsManager methods");
        }
    }
}
