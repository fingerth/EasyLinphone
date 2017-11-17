package com.xuchongyang.easyphone;

import android.content.Context;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.view.SurfaceView;

import com.xuchongyang.easyphone.callback.PhoneCallback;
import com.xuchongyang.easyphone.callback.RegistrationCallback;
import com.xuchongyang.easyphone.linphone.LinphoneManager;
import com.xuchongyang.easyphone.linphone.LinphoneUtils;
import com.xuchongyang.easyphone.linphone.PhoneBean;
import com.xuchongyang.easyphone.service.LinphoneService;

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;

import static java.lang.Thread.sleep;

/**
 * Created by Mark Xu on 2017/9/20.
 * Site: http://xuchongyang.com
 */

public class EasyLinphone {
    private static ServiceWaitThread mServiceWaitThread;
    private static String mUsername, mPassword, mServerIP;
    private static AndroidVideoWindowImpl mAndroidVideoWindow;
    private static SurfaceView mRenderingView;
    private static SurfaceView mPreviewView;
    private static LinphoneCore mLinphoneCore;

    /**
     * 开启服务
     * @param context 上下文
     */
    public static void startService(Context context) {
        if (!LinphoneService.isReady()) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(context, LinphoneService.class);
            context.startService(intent);
        }
    }

    /**
     * 设置 sip 账户信息
     * @param username sip 账户
     * @param password 密码
     * @param serverIP sip 服务器
     */
    public static void setAccount(String username, String password, String serverIP) {
        mUsername = username;
        mPassword = password;
        mServerIP = serverIP;
    }

    /**
     * 添加注册状态、通话状态回调
     * @param phoneCallback 通话回调
     * @param registrationCallback 注册状态回调
     */
    public static void addCallback(RegistrationCallback registrationCallback,
                                   PhoneCallback phoneCallback) {
        if (LinphoneService.isReady()) {
            LinphoneService.addRegistrationCallback(registrationCallback);
            LinphoneService.addPhoneCallback(phoneCallback);
        } else {
            mServiceWaitThread = new ServiceWaitThread(registrationCallback, phoneCallback);
            mServiceWaitThread.start();
        }
    }

    /**
     * 登录
     */
    public static void login() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!LinphoneService.isReady()) {
                    try {
                        sleep(80);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                loginToServer();
            }
        }).start();
    }

    /**
     * 登录 SIP 服务器
     */
    private static void loginToServer() {
        try {
            if (mUsername == null || mPassword == null || mServerIP == null) {
                throw new RuntimeException("The sip account is not configured.");
            }
            LinphoneUtils.getInstance().registerUserAuth(mUsername, mPassword, mServerIP);
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }

    /**
     * 呼叫指定号码
     * @param num 呼叫号码
     */
    public static void callTo(String num, boolean isVideoCall) {
        if (!LinphoneService.isReady() || !LinphoneManager.isInstantiated()) {
            throw new RuntimeException("LinphoneService is not ready or the LinphoneManager is not instantiated");
        }
        if (!num.equals("")) {
            PhoneBean phone = new PhoneBean();
            phone.setUserName(num);
            phone.setHost(mServerIP);
            LinphoneUtils.getInstance().startSingleCallingTo(phone, isVideoCall);
        }
    }

    /**
     * 接听来电
     */
    public static void acceptCall() {
        try {
            LinphoneManager.getLc().acceptCall(LinphoneManager.getLc().getCurrentCall());
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }

    /**
     * 挂断当前通话
     */
    public static void hangUp() {
        LinphoneUtils.getInstance().hangUp();
    }

    /**
     * 暂停通话
     */
    public static void pauseCall() {
        mLinphoneCore = LinphoneManager.getLcIfManagerNotDestroyOrNull();
        if (mLinphoneCore != null) {
            LinphoneCall linphoneCall = mLinphoneCore.getCurrentCall();
            mLinphoneCore.pauseCall(linphoneCall);
        }
    }

    /**
     * 恢复通话
     */
    public static void resumeCall() {
        mLinphoneCore = LinphoneManager.getLcIfManagerNotDestroyOrNull();
        if (mLinphoneCore != null) {
            LinphoneCall linphoneCall = mLinphoneCore.getCurrentCall();
            mLinphoneCore.resumeCall(linphoneCall);
        }
    }

    /**
     * 切换静音
     * @param isMicMuted 是否静音
     */
    public static void toggleMicro(boolean isMicMuted) {
        LinphoneUtils.getInstance().toggleMicro(isMicMuted);
    }

    /**
     * 切换免提
     * @param isSpeakerEnabled 是否免提
     */
    public static void toggleSpeaker(boolean isSpeakerEnabled) {
        LinphoneUtils.getInstance().toggleSpeaker(isSpeakerEnabled);
    }

    private static class ServiceWaitThread extends Thread {
        private PhoneCallback mPhoneCallback;
        private RegistrationCallback mRegistrationCallback;

        ServiceWaitThread(RegistrationCallback registrationCallback, PhoneCallback phoneCallback) {
            mRegistrationCallback = registrationCallback;
            mPhoneCallback = phoneCallback;
        }

        @Override
        public void run() {
            super.run();
            while (!LinphoneService.isReady()) {
                try {
                    sleep(80);
                } catch (InterruptedException e) {
                    throw new RuntimeException("waiting thread sleep() has been interrupted");
                }
            }
            LinphoneService.addPhoneCallback(mPhoneCallback);
            LinphoneService.addRegistrationCallback(mRegistrationCallback);
            mServiceWaitThread = null;
        }
    }

    /**
     * 判断当前通话为视频通话还是语音通话
     * @return 是否为视频通话
     */
    public static boolean getVideoEnabled() {
        LinphoneCallParams remoteParams = LinphoneManager.getLc().getCurrentCall().getRemoteParams();
        return remoteParams != null && remoteParams.getVideoEnabled();
    }

    /**
     * 设置 SurfaceView
     * @param renderingView 远程 SurfaceView
     * @param previewView 本地 SurfaceView
     */
    public static void setAndroidVideoWindow(final SurfaceView[] renderingView, final SurfaceView[] previewView) {
        mRenderingView = renderingView[0];
        mPreviewView = previewView[0];
        fixZOrder(mRenderingView, mPreviewView);
        mAndroidVideoWindow = new AndroidVideoWindowImpl(renderingView[0], previewView[0], new AndroidVideoWindowImpl.VideoWindowListener() {
            @Override
            public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl androidVideoWindow, SurfaceView surfaceView) {
                setVideoWindow(androidVideoWindow);
                renderingView[0] = surfaceView;
            }

            @Override
            public void onVideoRenderingSurfaceDestroyed(AndroidVideoWindowImpl androidVideoWindow) {
                removeVideoWindow();
            }

            @Override
            public void onVideoPreviewSurfaceReady(AndroidVideoWindowImpl androidVideoWindow, SurfaceView surfaceView) {
                mPreviewView = surfaceView;
                setPreviewWindow(mPreviewView);
            }

            @Override
            public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl androidVideoWindow) {
                removePreviewWindow();
            }
        });
    }

    /**
     * onVideoResume
     */
    public static void onVideoResume() {
        if (mRenderingView != null) {
            ((GLSurfaceView) mRenderingView).onResume();
        }

        if (mAndroidVideoWindow != null) {
            synchronized (mAndroidVideoWindow) {
                LinphoneManager.getLc().setVideoWindow(mAndroidVideoWindow);
            }
        }
    }

    /**
     * onVideoPause
     */
    public static void onVideoPause() {
        if (mAndroidVideoWindow != null) {
            synchronized (mAndroidVideoWindow) {
                LinphoneManager.getLc().setVideoWindow(null);
            }
        }

        if (mRenderingView != null) {
            ((GLSurfaceView) mRenderingView).onPause();
        }
    }

    /**
     * onVideoDestroy
     */
    public static void onVideoDestroy() {
        mPreviewView = null;
        mRenderingView = null;

        if (mAndroidVideoWindow != null) {
            mAndroidVideoWindow.release();
            mAndroidVideoWindow = null;
        }
    }

    private static void fixZOrder(SurfaceView rendering, SurfaceView preview) {
        rendering.setZOrderOnTop(false);
        preview.setZOrderOnTop(true);
        preview.setZOrderMediaOverlay(true); // Needed to be able to display control layout over
    }

    private static void setVideoWindow(Object o) {
        LinphoneManager.getLc().setVideoWindow(o);
    }

    private static void removeVideoWindow() {
        LinphoneCore linphoneCore = LinphoneManager.getLc();
        if (linphoneCore != null) {
            linphoneCore.setVideoWindow(null);
        }
    }

    private static void setPreviewWindow(Object o) {
        LinphoneManager.getLc().setPreviewWindow(o);
    }

    private static void removePreviewWindow() {
        LinphoneManager.getLc().setPreviewWindow(null);
    }

    /**
     * 获取 LinphoneCore
     * @return LinphoneCore
     */
    public static LinphoneCore getLC() {
        return LinphoneManager.getLc();
    }
}
