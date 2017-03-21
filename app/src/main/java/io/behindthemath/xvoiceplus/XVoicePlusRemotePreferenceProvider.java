package io.behindthemath.xvoiceplus;

import com.crossbowffs.remotepreferences.RemotePreferenceProvider;

import java.util.Arrays;
import java.util.List;

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
        return isCallingPackageGV() && isPermittedAccessibleSetting(prefKey);
    }

    private boolean isCallingPackageGV() {
        return NEW_GOOGLE_VOICE_PACKAGE.equals(getCallingPackage());
    }

    private boolean isPermittedAccessibleSetting(String prefKey) {
        return isPermittedReadOnlySetting(prefKey) || isPermittedReadWriteSetting(prefKey);
    }

    private boolean isPermittedReadOnlySetting(String prefKey) {
        List permittedReadableSettings = Arrays.asList("account", "settings_enabled");
        return permittedReadableSettings.contains(prefKey);
    }

    private boolean isPermittedReadWriteSetting(String prefKey) {
        return "user_hash".equals(prefKey);
    }
}
