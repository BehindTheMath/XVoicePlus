package io.behindthemath.xvoiceplus;

import com.crossbowffs.remotepreferences.RemotePreferenceProvider;

import java.util.Arrays;
import java.util.List;

import static io.behindthemath.xvoiceplus.XVoicePlus.LEGACY_GOOGLE_VOICE_PACKAGE;
import static io.behindthemath.xvoiceplus.XVoicePlus.NEW_GOOGLE_VOICE_PACKAGE;
import static io.behindthemath.xvoiceplus.XVoicePlus.XVOICE_PLUS_PACKAGE;
import static io.behindthemath.xvoiceplus.XVoicePlus.XVOICE_PLUS_PREFERENCES_FILE_NAME;

/**
 * Created by BehindTheMath on 2/23/2017.
 */

public class XVoicePlusRemotePreferenceProvider extends RemotePreferenceProvider {
    private static final String TAG = XVoicePlusRemotePreferenceProvider.class.getSimpleName();

    public XVoicePlusRemotePreferenceProvider() {
        super(XVOICE_PLUS_PACKAGE, new String[] {XVOICE_PLUS_PREFERENCES_FILE_NAME});
    }

    @Override
    protected boolean checkAccess(String prefName, String prefKey, boolean write) {
        // Allow read access to the "settings_enabled" preference for all packages
        if ("settings_enabled".equals(prefKey) && !write) return true;

        // Allow read access to the "account" preference for GV
        if ("account".equals(prefKey) && !write && isCallingPackageGV()) return true;

        // Allow read/write access to the "user_hash" preference for GV
        if ("user_hash".equals(prefKey) && isCallingPackageGV()) return true;

        return false;
    }

    private boolean isCallingPackageGV() {
        final String callingPackage = getCallingPackage();
        return LEGACY_GOOGLE_VOICE_PACKAGE.equals(callingPackage) ||
                NEW_GOOGLE_VOICE_PACKAGE.equals(callingPackage);
    }
}
