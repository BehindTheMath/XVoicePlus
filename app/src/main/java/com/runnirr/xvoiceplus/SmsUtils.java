package com.runnirr.xvoiceplus;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;

import de.robv.android.xposed.XposedBridge;

public class SmsUtils {
    private static final String TAG = SmsUtils.class.getName();

    public static final String FORMAT_3GPP = "3gpp";
    public static final int OP_WRITE_SMS = 15;
    public static final String SERVICE_CENTER = "5555555555";   // Fake so we know its a fake message


    public static void createFakeSms(Context context, String sender, String body, long date) throws IOException {
        byte[] pdu = null;
        byte[] scBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(SERVICE_CENTER);
        byte[] senderBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(sender);
        int lsmcs = scBytes.length;
        byte[] dateBytes = new byte[7];
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        dateBytes[0] = reverseByte((byte) decToHex((calendar.get(Calendar.YEAR) % 100)));
        dateBytes[1] = reverseByte((byte) decToHex((calendar.get(Calendar.MONTH) + 1)));
        dateBytes[2] = reverseByte((byte) decToHex((calendar.get(Calendar.DAY_OF_MONTH))));
        dateBytes[3] = reverseByte((byte) decToHex((calendar.get(Calendar.HOUR_OF_DAY))));
        dateBytes[4] = reverseByte((byte) decToHex((calendar.get(Calendar.MINUTE))));
        dateBytes[5] = reverseByte((byte) decToHex((calendar.get(Calendar.SECOND))));
        dateBytes[6] = reverseByte((byte) longToTimezone(calendar.get(Calendar.ZONE_OFFSET) +
                calendar.get(Calendar.DST_OFFSET)));

        //Log.d(TAG, "GV Time: " + date);
        //Log.d(TAG, "TimeZone: " + calendar.getTimeZone().getDisplayName());
        //Log.d(TAG, "ZoneOffset: " + calendar.get(Calendar.ZONE_OFFSET));
        //Log.d(TAG, "DstOffset: " + calendar.get(Calendar.DST_OFFSET));
        //Log.d(TAG, "dateBytes: " + bytesToHex(dateBytes));


        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            bo.write(lsmcs);
            bo.write(scBytes);
            bo.write(0x04);
            bo.write((byte) sender.length());
            if (senderBytes != null) {
                bo.write(senderBytes);
            } else {
                Log.w(TAG, "senderBytes are null, be skeptical");
            }
            bo.write(0x00);
            bo.write(0x00); // encoding: 0 for default 7bit
            bo.write(dateBytes);
            try {
                String sReflectedClassName = "com.android.internal.telephony.GsmAlphabet";
                Class<?> cReflectedNFCExtras = Class.forName(sReflectedClassName);
                Method stringToGsm7BitPacked = cReflectedNFCExtras.getMethod(
                        "stringToGsm7BitPacked", String.class);
                stringToGsm7BitPacked.setAccessible(true);
                byte[] bodybytes = (byte[]) stringToGsm7BitPacked.invoke(null, body);
                bo.write(bodybytes);
            } catch (Exception e) {
                Log.e(TAG, "Reflection error creating pdu", e);
            }

            pdu = bo.toByteArray();
        } finally {
            bo.close();
        }

        broadcastMessage(context, pdu);
    }

    private static void broadcastMessage(Context context, byte[] pdu) {
        Log.d(TAG, "Creating fake sms. Broadcasting...");
        //Log.d(TAG, "Broadcasting pdu " + bytesToHex(pdu));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String deliver_action = "android.provider.Telephony.SMS_DELIVER";
            Intent intent = new Intent()
                .setAction(deliver_action)
                .setFlags(0)
                .putExtra("pdus", new Object[] { pdu })
                .putExtra("format", FORMAT_3GPP);
            context.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS");
        }
        String received_action = "android.provider.Telephony.SMS_RECEIVED";
        Intent intent = new Intent()
            .setAction(received_action)
            .setFlags(0)
            .putExtra("pdus", new Object[] { pdu })
            .putExtra("format", FORMAT_3GPP);
        context.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS");
    }

    private static byte reverseByte(byte b) {
        return (byte) ((b & 0xF0) >> 4 | (b & 0x0F) << 4);
    }

    private static int decToHex(int d) {
        //  14 --> 0x14
        // -32 --> 0xE0
        return d + ((d/10) * 6);
    }

    private static int longToTimezone(long millis) {
        int units = (int) Math.abs(millis / (60 * 1000 * 15));
        int mask = millis < 0 ? 0x80 : 0x00;
        int result = decToHex(units) | mask;
        //Log.d(TAG, "units: " + units);
        //Log.d(TAG, "mask hex: " + bytesToHex((byte) mask));
        //Log.d(TAG, "result hex: " + bytesToHex((byte) result));
        return result;
    }

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte... bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}