package com.runnirr.xvoiceplus;


import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.XResources;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import com.runnirr.xvoiceplus.hooks.XSmsMethodHook;
import com.runnirr.xvoiceplus.receivers.MessageEventReceiver;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;

import static com.runnirr.xvoiceplus.hooks.XSmsMethodHook.HookType;
import static de.robv.android.xposed.XposedHelpers.*;

public class XVoicePlus implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static final String TAG = XVoicePlus.class.getName();

    public static final String GOOGLE_VOICE_PACKAGE = "com.google.android.apps.googlevoice";
    private static final String XVOICE_PLUS_PACKAGE = "com.runnirr.xvoiceplus";
    private static final String SENSE_SMS_PACKAGE = "com.htc.wrap.android.telephony";
    private static final String SMS_PACKAGE = "android.telephony";

    private static final String PERM_BROADCAST_SMS = "android.permission.BROADCAST_SMS";

    public boolean isEnabled() {
        return new XSharedPreferences("com.runnirr.xvoiceplus").getBoolean("settings_enabled", true);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws ClassNotFoundException {
        if (lpparam.packageName.equals(GOOGLE_VOICE_PACKAGE)) {
            Log.d(TAG, "Hooking google voice push notifications");
            hookGoogleVoice(lpparam);
        }
        // Sense based ROMs
        else if(lpparam.packageName.equals(SENSE_SMS_PACKAGE)) {
            Log.d(TAG, "Hooking sense SMS wrapper");
            hookSendSmsHtc(lpparam);
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
        hookSmsMessage();
        hookSendSms();
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

    private void hookSmsMessage() {
        final String INTERNAL_GSM_SMS_MESSAGE = "com.android.internal.telephony.gsm.SmsMessage";
        final String createFromPdu = "createFromPdu";

        findAndHookMethod(android.telephony.gsm.SmsMessage.class, createFromPdu, byte[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Log.d(TAG, "After hook of telephony.gsm.SmsMessage");
                android.telephony.gsm.SmsMessage originalResult = (android.telephony.gsm.SmsMessage) param.getResult();
                try {
                    if (originalResult != null && getObjectField(originalResult, "mWrappedSmsMessage") != null) {
                        Log.d(TAG, "message and wrapped message are non-null. use them");
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Original message body is not available. Try to use GSM");
                }
                Object gsmResult = callStaticMethod(findClass(INTERNAL_GSM_SMS_MESSAGE, null), createFromPdu, param.args[0]);
                setObjectField(originalResult, "mWrappedSmsMessage", gsmResult);
            }
        });
        findAndHookMethod(SmsMessage.class, createFromPdu, byte[].class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.d(TAG, "Create from PDU");
                if (!SmsUtils.FORMAT_3GPP.equals(param.args[1])) {
                    try {
                        Log.d(TAG, "Trying to parse fake pdu");
                        SmsMessage result = (SmsMessage) callStaticMethod(SmsMessage.class, "createFromPdu", param.args[0], SmsUtils.FORMAT_3GPP);
                        if (result != null && getObjectField(result, "mWrappedSmsMessage") != null) {
                            param.setResult(result);
                        } else {
                            Log.w(TAG, "Something with the message was null");
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Unable to parse message as " + SmsMessage.class.getName(), e);
                    }
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

    private void hookSendSms(){
        // AOSP
        Class clazz = findClass("android.telephony.SmsManager", null);
        ArrayList<Method> hookedMethods = new ArrayList<Method>();

        try {
            hookedMethods.add((Method) findAndHookMethod(clazz, "sendTextMessage",
                    String.class, String.class, String.class, PendingIntent.class, PendingIntent.class,
                    new XSmsMethodHook(this, HookType.AOSP)).getHookedMethod());
            hookedMethods.add((Method) findAndHookMethod(clazz, "sendMultipartTextMessage",
                    String.class, String.class, ArrayList.class, ArrayList.class, ArrayList.class,
                    new XSmsMethodHook(this, HookType.AOSP)).getHookedMethod());
            Log.d(TAG, "Hooked standard SmsManager methods");
        } catch(NoSuchMethodError ignored){
            Log.w(TAG, "Failed to hook standard SmsManager methods");
        }

        // Touchwiz
        try {
            hookedMethods.add((Method) findAndHookMethod(clazz, "sendMultipartTextMessage",
                    String.class, String.class, ArrayList.class, ArrayList.class, ArrayList.class,
                    Boolean.class, Integer.class, Integer.class, Integer.class,
                    new XSmsMethodHook(this, HookType.TOUCHWIZ)).getHookedMethod());
            hookedMethods.add((Method) findAndHookMethod(clazz, "sendMultipartTextMessage",
                    String.class, String.class, ArrayList.class, ArrayList.class, ArrayList.class,
                    Boolean.class, Integer.class, Integer.class, Integer.class, Integer.class,
                    new XSmsMethodHook(this, HookType.TOUCHWIZ)).getHookedMethod());
            hookedMethods.add((Method) findAndHookMethod(clazz, "sendMultipartTextMessage",
                    String.class, String.class, ArrayList.class, ArrayList.class, ArrayList.class,
                    String.class, Integer.class,
                    new XSmsMethodHook(this, HookType.TOUCHWIZ)).getHookedMethod());
            Log.d(TAG, "Hooked touchwiz SmsManager methods");
        } catch(NoSuchMethodError ignored) {}

        for(Method method : hookedMethods) {
            if(method == null) continue;
            ArrayList<String> paramTypes = new ArrayList<String>();
            for(Class type : method.getParameterTypes()) paramTypes.add(type.getSimpleName());
            Log.d(TAG, String.format("Hooked method %s.%s(%s)",
                    method.getDeclaringClass().getCanonicalName(),
                    method.getName(), paramTypes));
        }
    }

    // Sense based ROMs
    private void hookSendSmsHtc(LoadPackageParam lpparam) {
        findAndHookMethod(SENSE_SMS_PACKAGE + ".HtcWrapIfSmsManager", lpparam.classLoader, "sendMultipartTextMessage",
                String.class, String.class, ArrayList.class, ArrayList.class, Bundle.class,
                new XSmsMethodHook(this, HookType.SENSE));

    }
}
