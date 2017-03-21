package io.behindthemath.xvoiceplus.gv;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class GvResponse {

    public static class Payload {
        @SerializedName("messageList")
        public ArrayList<Conversation> conversations = new ArrayList<>();
    }

    public static class Conversation {
        @SerializedName("children")
        public ArrayList<Message> messages = new ArrayList<>();
    }

    public static class Message {
        @SerializedName("startTime")
        public long date;

        @SerializedName("phoneNumber")
        public String phoneNumber;

        @SerializedName("message")
        public String message;

        // 10 is incoming
        // 11 is outgoing
        @SerializedName("type")
        public int type;

        @SerializedName("id")
        public String id;

        @SerializedName("conversationId")
        public String conversationId;

        @SerializedName("isRead")
        public int read;
    }

}
