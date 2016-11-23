package com.runnirr.xvoiceplus;


import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.XResources;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.runnirr.xvoiceplus.receivers.MessageEventReceiver;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.util.ArrayList;
import java.util.Set;

import static de.robv.android.xposed.XposedHelpers.*;

public class XVoicePlus implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static final String TAG = XVoicePlus.class.getName();

    private static final String GOOGLE_VOICE_PACKAGE = "com.google.android.apps.googlevoice";
    private static final String XVOICE_PLUS_PACKAGE = "com.ihelp101.voiceminus";
    private static final String PERM_BROADCAST_SMS = "android.permission.BROADCAST_SMS";
    private static final String PERM_INTERCEPT_SMS = "android.permission.INTERCEPT_SMS";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws ClassNotFoundException {

        if (lpparam.packageName.equals("android") && lpparam.processName.equals("android")) {
            hookXVoicePlusPermission(lpparam);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
                hookAppOps(lpparam);
            }
        }

        if (lpparam.packageName.equals(GOOGLE_VOICE_PACKAGE)) {
            hookGoogleVoice(lpparam);
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        XResources.setSystemWideReplacement("android", "bool", "config_sms_capable", true);
    }

    private void hookGoogleVoice(LoadPackageParam lpparam) {
        findAndHookMethod(GOOGLE_VOICE_PACKAGE + ".PushNotificationReceiver", lpparam.classLoader, "onReceive", Context.class, Intent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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

    @TargetApi(19)
    private void hookAppOps(LoadPackageParam loadPackageParam) {
        XposedBridge.hookAllConstructors(findClass("com.android.server.AppOpsService.Op", loadPackageParam.classLoader),
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (XVOICE_PLUS_PACKAGE.equals(param.args[1]) &&
                                (Integer) param.args[2] == SmsUtils.OP_WRITE_SMS) {

                            setIntField(param.thisObject, "mode", AppOpsManager.MODE_ALLOWED);
                        }
                    }

                });
    }

    private void hookXVoicePlusPermission(LoadPackageParam loadPackageParam){
        final Class<?> pmServiceClass = findClass("com.android.server.pm.PackageManagerService", loadPackageParam.classLoader);

        findAndHookMethod(pmServiceClass, "grantPermissionsLPw", "android.content.pm.PackageParser.Package", boolean.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final String pkgName = (String) getObjectField(param.args[0], "packageName");

                if (XVOICE_PLUS_PACKAGE.equals(pkgName)) {
                    final Object extras = XposedHelpers.getObjectField(param.args[0], "mExtras");
                    final Set<String> grantedPerms = (Set<String>) XposedHelpers.getObjectField(extras, "grantedPermissions");
                    final Object settings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                    final Object permissions = XposedHelpers.getObjectField(settings, "mPermissions");


                    if (!grantedPerms.contains(PERM_BROADCAST_SMS)) {
                        final Object pBroadcast = XposedHelpers.callMethod(permissions, "get", PERM_BROADCAST_SMS);
                        grantedPerms.add(PERM_BROADCAST_SMS);
                        int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                        int[] bpGids = (int[]) XposedHelpers.getObjectField(pBroadcast, "gids");
                        gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), "appendInts", gpGids, bpGids);
                        XposedBridge.log("Permission added: " + pBroadcast + "; ret=" + gpGids);
                    }

                    if (!grantedPerms.contains(PERM_INTERCEPT_SMS)) {
                        final Object pBroadcast = XposedHelpers.callMethod(permissions, "get", PERM_INTERCEPT_SMS);
                        grantedPerms.add(PERM_INTERCEPT_SMS);
                        int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                        int[] bpGids = (int[]) XposedHelpers.getObjectField(pBroadcast, "gids");
                        gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), "appendInts", gpGids, bpGids);
                        XposedBridge.log("Permission added: " + pBroadcast + "; ret=" + gpGids);
                    }
                }
            }
        });
    }
}
