package com.xuchongyang.easyphone.linphone;

import android.content.Context;
import android.util.Log;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LpConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Mark Xu on 17/3/13.
 * 语音通话工具类
 */

public class LinphoneUtils {
    private static final String TAG = "LinphoneUtils";
    private static volatile LinphoneUtils sInstance;
    private LinphoneCore mLinphoneCore;

    /**
     * 获取 LinphoneUtils 实例
     * @return LinphoneUtils 实例
     */
    public static LinphoneUtils getInstance() {
        if (sInstance == null) {
            synchronized (LinphoneUtils.class) {
                if (sInstance == null) {
                    sInstance = new LinphoneUtils();
                }
            }
        }
        return sInstance;
    }

    private LinphoneUtils() {
        mLinphoneCore = LinphoneManager.getLc();
        mLinphoneCore.enableEchoCancellation(true);
        mLinphoneCore.enableEchoLimiter(true);
    }

    /**
     * 注册到服务器
     * @param name 账号
     * @param password 密码
     * @param host IP
     * @throws LinphoneCoreException LinphoneCoreException
     */
    public void registerUserAuth(String name, String password, String host) throws LinphoneCoreException {
        String identify = "sip:" + name + "@" + host;
        String proxy = "sip:" + host;
        LinphoneAddress proxyAddress = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
        LinphoneAddress identifyAddress = LinphoneCoreFactory.instance().createLinphoneAddress(identify);
        LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(name, null, password,
                null, null, host);
        LinphoneProxyConfig prxCfg = mLinphoneCore.createProxyConfig(identifyAddress.asString(),
                proxyAddress.asStringUriOnly(), proxyAddress.asStringUriOnly(), true);
        prxCfg.enableAvpf(false);
        prxCfg.setAvpfRRInterval(0);
        prxCfg.enableQualityReporting(false);
        prxCfg.setQualityReportingCollector(null);
        prxCfg.setQualityReportingInterval(0);
        prxCfg.enableRegister(true);
        mLinphoneCore.addProxyConfig(prxCfg);
        mLinphoneCore.addAuthInfo(authInfo);
        mLinphoneCore.setDefaultProxyConfig(prxCfg);
    }

    /**
     * 开启通话
     * @param bean PhoneBean
     * @param isVideoCall 是否为视频通话
     * @return LinphoneCall
     */
    public LinphoneCall startSingleCallingTo(PhoneBean bean, boolean isVideoCall) {
        LinphoneAddress address;
        LinphoneCall call = null;
        try {
            address = mLinphoneCore.interpretUrl(bean.getUserName() + "@" + bean.getHost());
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
            return null;
        }
        address.setDisplayName(bean.getDisplayName());
        LinphoneCallParams params = mLinphoneCore.createCallParams(null);
        if (isVideoCall) {
            params.setVideoEnabled(true);
            params.enableLowBandwidth(false);
        } else {
            params.setVideoEnabled(false);
        }
        try {
            call = mLinphoneCore.inviteAddressWithParams(address, params);
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
        return call;
    }

    /**
     * 挂断电话
     */
    public void hangUp() {
        LinphoneCall currentCall = mLinphoneCore.getCurrentCall();
        if (currentCall != null) {
            mLinphoneCore.terminateCall(currentCall);
        } else if (mLinphoneCore.isInConference()) {
            mLinphoneCore.terminateConference();
        } else {
            mLinphoneCore.terminateAllCalls();
        }
    }

    /**
     * 是否静音
     * @param isMicMuted 是否静音
     */
    public void toggleMicro(boolean isMicMuted) {
        mLinphoneCore.muteMic(isMicMuted);
    }

    /**
     * 是否外放
     * @param isSpeakerEnabled 是否外放
     */
     public void toggleSpeaker(boolean isSpeakerEnabled) {
         mLinphoneCore.enableSpeaker(isSpeakerEnabled);
     }

    public static void copyIfNotExist(Context context, int resourceId, String target) throws IOException {
        File fileToCopy = new File(target);
        if (!fileToCopy.exists()) {
            copyFromPackage(context, resourceId, fileToCopy.getName());
        }
    }

    public static void copyFromPackage(Context context, int resourceId, String target) throws IOException {
        FileOutputStream outputStream = context.openFileOutput(target, 0);
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while ((readByte = inputStream.read(buff)) != -1) {
            outputStream.write(buff, 0, readByte);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    public static LpConfig getConfig(Context context) {
        LinphoneCore lc = getLc();
        if (lc != null) {
            return lc.getConfig();
        }

        if (LinphoneManager.isInstantiated()) {
            org.linphone.mediastream.Log.w("LinphoneManager not instanciated yet...");
            return LinphoneCoreFactory.instance().createLpConfig(context.getFilesDir().getAbsolutePath() + "/.linphonerc");
        }

        return LinphoneCoreFactory.instance().createLpConfig(LinphoneManager.getInstance().mLinphoneConfigFile);
    }

    private static LinphoneCore getLc() {
        if (!LinphoneManager.isInstantiated()) {
            return null;
        }
        return LinphoneManager.getLcIfManagerNotDestroyOrNull();
    }
}
