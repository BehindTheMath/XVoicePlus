package io.behindthemath.xvoiceplus;

import android.support.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.behindthemath.xvoiceplus.RegisteredAccountsParser.IntegralToString.bytesToHexString;

/**
 * Created by BehindTheMath on 2/22/2017.
 */

public class RegisteredAccountsParser {
    public static boolean isMatch(@NonNull final String account_name, @NonNull final String user_hash, @NonNull final byte[] registered_accounts_bytes) {
        //noinspection ConstantConditions
        if (account_name == null || user_hash == null || registered_accounts_bytes == null) {
            throw new IllegalArgumentException("Arguments cannot be null.");
        }

        final String account_name_hex = bytesToHexString(account_name.getBytes(), true);
        final String user_hash_hex = bytesToHexString(user_hash.getBytes(), true);
        final String account_data_hex = bytesToHexString(registered_accounts_bytes, true);

        Pattern pattern = Pattern.compile("010A.{2}" + account_name_hex + "122C" + user_hash_hex + "1A");
        Matcher matcher = pattern.matcher(account_data_hex);
        return matcher.find();
    }

    public static String getAccountNameFromUserHash(@NonNull final String user_hash, @NonNull final byte[] registered_accounts_bytes) {
        //noinspection ConstantConditions
        if (user_hash == null || registered_accounts_bytes == null) {
            throw new IllegalArgumentException("Arguments cannot be null.");
        }

        final String user_hash_hex = bytesToHexString(user_hash.getBytes(), true);
        final String registered_accounts_hex = bytesToHexString(registered_accounts_bytes, true);

        Pattern pattern = Pattern.compile(".*010A.{2}(.*)122C" + user_hash_hex + "1A");
        Matcher matcher = pattern.matcher(registered_accounts_hex);
        if (matcher.find()) {
            final String account_name_hex_string = matcher.group(1);
            byte[] account_name_hex_byte = new byte[account_name_hex_string.length() / 2];
            for (int i = 0; i < account_name_hex_byte.length; i ++) {
                account_name_hex_byte[i] = Byte.parseByte(account_name_hex_string.substring(i * 2, (i * 2) + 2), 16);
            }
            return new String(account_name_hex_byte);
        } else return null;
    }

    public static String getUserHashFromAccountName(@NonNull final String account_name, @NonNull final byte[] registered_accounts_bytes) {
        //noinspection ConstantConditions
        if (account_name == null || registered_accounts_bytes == null) {
            throw new IllegalArgumentException("Arguments cannot be null.");
        }

        final String user_name_hex = bytesToHexString(account_name.getBytes(), true);
        final String registered_accounts_hex = bytesToHexString(registered_accounts_bytes, true);

        Pattern pattern = Pattern.compile(".*010A.{2}" + user_name_hex + "122C(.*)1A");
        Matcher matcher = pattern.matcher(registered_accounts_hex);
        if (matcher.find()) {
            final String user_hash_hex_string = matcher.group(1);
            byte[] user_hash_hex_byte = new byte[user_hash_hex_string.length() / 2];
            for (int i = 0; i < user_hash_hex_byte.length; i ++) {
                user_hash_hex_byte[i] = Byte.parseByte(user_hash_hex_string.substring(i * 2, (i * 2) + 2), 16);
            }
            return new String(user_hash_hex_byte);
        } else return null;
    }

    static class IntegralToString {
        /**
         * From java.lang.IntegralToString#bytesToHexString()
         *
         * Returns a two-digit hex string. That is, -1 becomes "ff" or "FF" and 2 becomes "02".
         *
         * @param bytes
         * @param upperCase
         * @return
         */
        static String bytesToHexString(byte[] bytes, boolean upperCase) {
            final char[] DIGITS = {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                    'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
                    'u', 'v', 'w', 'x', 'y', 'z'
            };
            final char[] UPPER_CASE_DIGITS = {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
                    'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
                    'U', 'V', 'W', 'X', 'Y', 'Z'
            };

            char[] digits = upperCase ? UPPER_CASE_DIGITS : DIGITS;
            char[] buf = new char[bytes.length * 2];
            int c = 0;
            for (byte b : bytes) {
                buf[c++] = digits[(b >> 4) & 0xf];
                buf[c++] = digits[b & 0xf];
            }
            return new String(buf);
        }
    }
}
