package io.behindthemath.xvoiceplus;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

import static io.behindthemath.xvoiceplus.XVoicePlus.XVOICE_PLUS_PREFERENCES_FILE_NAME;

/**
 * Created by BehindTheMath on 3/24/2017.
 */

public class SharedPrefsManager {
    private Context context;

    public SharedPrefsManager(Context context) {
        this.context = context;
    }

    public SharedPreferences getSettings() {
        return context.getSharedPreferences(XVOICE_PLUS_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences getAppSettings() {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public SharedPreferences getRecentMessages() {
        return context.getSharedPreferences("recent_messages", Context.MODE_PRIVATE);
    }

    public boolean syncEnabled() {
        return (getSettings().getBoolean("settings_sync_on_receive", false) ||
                getSettings().getBoolean("settings_sync_on_send", false) ||
                Long.valueOf(getSettings().getString("settings_polling_frequency", "-1")) != -1L);
    }

    /**
     * Mark an outgoing text as recently sent, so if it comes in via round trip, we ignore it.
     */
    public void addMessageToRecentList(String text) {
        SharedPreferences savedRecent = getRecentMessages();
        Set<String> recentMessages = savedRecent.getStringSet("recent", new HashSet<String>());
        recentMessages.add(text);
        savedRecent.edit().putStringSet("recent", recentMessages).apply();
    }

    public boolean removeMessageFromRecentList(String text) {
        SharedPreferences savedRecent = getRecentMessages();
        Set<String> recentMessage = savedRecent.getStringSet("recent", new HashSet<String>());
        if (recentMessage.remove(text)) {
            savedRecent.edit().putStringSet("recent", recentMessage).apply();
            return true;
        }
        return false;
    }

    void clearRecentList() {
        getRecentMessages().edit().putStringSet("recent", new HashSet<String>()).apply();
    }
}
