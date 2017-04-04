package io.behindthemath.xvoiceplus.messages;

import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Calendar;

public class SmsUtils {
    private static final String TAG = SmsUtils.class.getSimpleName();

    static final String FORMAT_3GPP = "3gpp";
    public static final int OP_WRITE_SMS = 15;
    // Fake so we know it's a fake message
    private static final String SERVICE_CENTER = "5555555555";

    static final int MAX_TP_UD_BYTES_BESIDES_HEADER = 248;

    // TP-MMS flag that signifies that this PDU is not the last one
    static final byte TP_MMS_FALSE_FLAG = 0x04;
    // TP-UDHI flag that signifies that this PDU contains a header
    static final byte TP_UDHI_TRUE_FLAG = 0x40;

    static final int VOICE_INCOMING_SMS = 10;
    static final int VOICE_OUTGOING_SMS = 11;

    static final int PROVIDER_INCOMING_SMS = 1;
    static final int PROVIDER_OUTGOING_SMS = 2;

    static final Uri URI_SENT = Uri.parse("content://sms/sent");
    static final Uri URI_RECEIVED = Uri.parse("content://sms/inbox");

    static class Pdu {
        byte[] smscBytesWithLength;

        /**
          The first octet of the PDU (after the SMSC data). This is required for the TP-MMS (More
          Messages to Send) bit, which signifies if there are more PDUs following this one, and for
          the TP-UDHI (User Data Header Indicator) bit, which signifies if the TP_UD contains a header.
         */
        byte firstByte;

        /**
         * TP-OA (Originating Address)
         */
        byte[] senderBytes;

        /**
         * TP-SCTS (Service Centre Time Stamp)
         */
        byte[] timeBytes;

        /**
         * TP-UDH (User Data Header)
         */
        byte[] header;

        /**
         * TP-UD (User Data)
         */
        byte[] bodyBytes;

        byte[] getBytes() throws IOException {
            try (ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                bo.write(smscBytesWithLength);
                bo.write(firstByte);

                // TP-OA length in nibbles, ignoring TON byte and pad
                int senderBytesLength = (senderBytes.length - 1) * 2;
                // Subtract one if there's an 'F' to pad at the end
                if ((senderBytes[senderBytes.length - 1] & 0xf0) == 0xf0) { --senderBytesLength; }
                bo.write(senderBytesLength);

                bo.write(senderBytes);
                // TP-PID (Protocol Identifier)
                bo.write(0x00);
                // TP-DCS (Data Coding Scheme): UCS2 encoding
                bo.write(0x08);
                bo.write(timeBytes);

                if (header == null) {
                    bo.write(bodyBytes.length);
                } else {
                    // TP-UDL (User Data Length)
                    bo.write(header.length + bodyBytes.length);
                    bo.write(header);
                }
                bo.write(bodyBytes);

                return bo.toByteArray();
            }
        }
    }

    static Object[] createFakeSms(String sender, String body, long date) throws IOException {
        byte[] smscBytesWithLength = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(SERVICE_CENTER);

        if (sender == null) Log.w(TAG, "sender == null, be skeptical");
        byte[] senderBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(sender);

        return createFakeSms(smscBytesWithLength, senderBytes, body, date);
    }

    private static Object[] createFakeSms(byte[] smscBytesWithLength, byte[] senderBytes, String body, long date) throws IOException {
        // TP-SCTS
        byte[] timeBytes = calculateTimeBytes(date);

        // Encode the body in UCS-2
        byte[] bodyBytes = body.getBytes("utf-16be");

        Pdu pdu;

        // TP-UDL is one octet, so max message length is 255 octets.
        if (bodyBytes.length <= 255) {
            pdu = generatePdu(true, null, smscBytesWithLength, senderBytes, timeBytes, bodyBytes);
            return new Object[] {pdu.getBytes()};
        } else {
            int totalNumParts = (int) Math.ceil(bodyBytes.length / (double) MAX_TP_UD_BYTES_BESIDES_HEADER);
            Object[] pdus = new Object[totalNumParts];

            for(int i = 0; i < totalNumParts; i++) {
                byte[] header = generateHeader(totalNumParts, i + 1);

                int bodyBytesStart = MAX_TP_UD_BYTES_BESIDES_HEADER * i;
                int bodyBytesEnd;
                if (i < totalNumParts - 1) {
                    // If this is not the last PDU, use the maximum amount of bytes
                    bodyBytesEnd = MAX_TP_UD_BYTES_BESIDES_HEADER;
                } else {
                    // If this is the last PDU, use whatever is left
                    bodyBytesEnd = bodyBytes.length;
                }

                byte[] bodyBytesPart = Arrays.copyOfRange(bodyBytes, bodyBytesStart, bodyBytesEnd);
                pdu = generatePdu(i == totalNumParts - 1, header, smscBytesWithLength, senderBytes, timeBytes, bodyBytesPart);
                pdus[i] = pdu.getBytes();
            }

            return pdus;
        }
    }

    /**
     * Generates a PDU based on the supplied arguments.
     *
     * @param isLastPdu {@code boolean} which signifies if this is the last (or only) PDU in the sequence.
     * @param header TP-UDH header, if applicable. Otherwise, {@code null}.
     * @param smscBytesWithLength TP-SMSC, preceded with the length in bytes.
     * @param senderBytes TP-OA.
     * @param timeBytes TP-SCTS.
     * @param bodyBytes TP-UD, not including the header.
     *
     * @return
     *
     * @throws IOException
     */
    private static Pdu generatePdu(boolean isLastPdu, byte[] header, byte[] smscBytesWithLength, byte[] senderBytes, byte[] timeBytes, byte[] bodyBytes) throws IOException {
        Pdu pdu = new SmsUtils.Pdu();
        pdu.smscBytesWithLength = smscBytesWithLength;

        if (isLastPdu) {
            pdu.firstByte = TP_MMS_FALSE_FLAG;
            // 0x44 signifies this is not the last PDU and this PDU has a header
            if (header != null) pdu.firstByte |= TP_UDHI_TRUE_FLAG;
        } else {
            pdu.firstByte = TP_UDHI_TRUE_FLAG;
        }

        pdu.senderBytes = senderBytes;
        pdu.timeBytes = timeBytes;
        pdu.header = header;
        pdu.bodyBytes = bodyBytes;

        return pdu;
    }

    /**
     * Generates a {@code byte[]} with the TP-UDH
     *
     * @param totalNumParts Total number of PDUs this message will be broken up into.
     * @param numPart The position of this PDU in the sequence.
     *
     * @return
     *
     * @throws IOException
     */
    private static byte[] generateHeader(int totalNumParts, int numPart) throws IOException {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            // UDH length
            bo.write(0x06);
            // IEI (Information Element Identifier): Concatenated short messages, 16-bit reference number
            bo.write(0x08);
            // IE length
            bo.write(0x04);
            // CSMS (Concatenated SMS) reference number byte 1
            bo.write(0xAB);
            // CSMS reference number byte 2
            bo.write(0xCD);
            // Total number of parts
            bo.write(totalNumParts);
            // This part's number in the sequence
            bo.write(numPart);

            return bo.toByteArray();
        }
    }

    /**
     * Calculates the TP-SCTS octets.
     *
     * @param date The date, represented in a {@code long}.
     *
     * @return A {@code byte[]} with the TP-SCTS octets.
     */
    private static byte[] calculateTimeBytes(long date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        byte[] timeBytes = new byte[7];
        timeBytes[0] = reverseByte((byte) decToHex((calendar.get(Calendar.YEAR) % 100)));
        timeBytes[1] = reverseByte((byte) decToHex((calendar.get(Calendar.MONTH) + 1)));
        timeBytes[2] = reverseByte((byte) decToHex((calendar.get(Calendar.DAY_OF_MONTH))));
        timeBytes[3] = reverseByte((byte) decToHex((calendar.get(Calendar.HOUR_OF_DAY))));
        timeBytes[4] = reverseByte((byte) decToHex((calendar.get(Calendar.MINUTE))));
        timeBytes[5] = reverseByte((byte) decToHex((calendar.get(Calendar.SECOND))));
        timeBytes[6] = reverseByte((byte) longToTimezone(calendar.get(Calendar.ZONE_OFFSET) +
                calendar.get(Calendar.DST_OFFSET)));
        
        return timeBytes;
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
        return result;
    }

    public static String bytesToHex(byte[] bytes) {
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];

        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }
}