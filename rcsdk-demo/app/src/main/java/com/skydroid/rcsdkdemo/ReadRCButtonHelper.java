package com.skydroid.rcsdkdemo;

import com.skydroid.rcsdk.KeyManager;
import com.skydroid.rcsdk.common.DeviceType;
import com.skydroid.rcsdk.common.callback.CompletionCallbackWith;
import com.skydroid.rcsdk.common.callback.KeyListener;
import com.skydroid.rcsdk.common.error.SkyException;
import com.skydroid.rcsdk.key.RemoteControllerKey;

/**
 * @author 咔一下
 * @date 2024/4/3 13:11
 * @email 1501020210@qq.com
 * @describe 采集遥控器通道值工具类
 */
public class ReadRCButtonHelper {

    private final static long INTERVAL = 99;
    private long lastH16CallRcChannelValueListenerTime = 0;
    private RCButtonValueListener listener;
    private LopperThread lopperThread;
    private final DeviceType deviceType;
    private boolean isStart = false;

    public ReadRCButtonHelper(DeviceType deviceType){
        this.deviceType = deviceType;
    }

    private final KeyListener<int[]> channelKeyListener = new KeyListener<int[]>() {
        @Override
        public void onValueChange(int[] ints, int[] t1) {
            long nowTime = System.currentTimeMillis();
            if (nowTime - lastH16CallRcChannelValueListenerTime >= INTERVAL){
                RCButtonValueListener l = listener;
                if (l != null){
                    l.onRCButtonValue(t1);
                }
                lastH16CallRcChannelValueListenerTime = nowTime;
            }
        }
    };

    private final CompletionCallbackWith<int[]> channelKeyCallBack = new CompletionCallbackWith<int[]>() {
        @Override
        public void onSuccess(int[] ints) {
            RCButtonValueListener l = listener;
            if (l != null){
                l.onRCButtonValue(ints);
            }
        }

        @Override
        public void onFailure(SkyException e) {

        }
    };

    public RCButtonValueListener getListener() {
        return listener;
    }

    public void setListener(RCButtonValueListener listener) {
        this.listener = listener;
    }

    public synchronized void start(){
        if (isStart){
            return;
        }
        switch (deviceType){
            case H16:
                KeyManager.INSTANCE.listen(RemoteControllerKey.INSTANCE.getKeyH16Channels(), channelKeyListener);
                break;
            default:
                lopperThread = new LopperThread();
                lopperThread.start();
                break;
        }
        isStart = true;
    }

    public synchronized void stop(){
        if (!isStart){
            return;
        }
        isStart = false;
        KeyManager.INSTANCE.cancelListen(channelKeyListener);
        if (lopperThread != null){
            lopperThread.close();
            lopperThread = null;
        }
    }

    public static interface RCButtonValueListener{
        public void onRCButtonValue(int[] buttons);
    }

    private class LopperThread extends Thread{

        private boolean isRun = false;

        @Override
        public void run() {
            super.run();
            while (isRun){
                KeyManager.INSTANCE.get(RemoteControllerKey.INSTANCE.getKeyChannels(),channelKeyCallBack);
                synchronized (this){
                    try {
                        wait(INTERVAL);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public synchronized void start() {
            isRun = true;
            super.start();
        }

        public void close(){
            synchronized (this){
                isRun = false;
                try {
                    notify();
                }catch (Exception e){}
            }
            try {
                join();
            } catch (InterruptedException e) {
                try {
                    interrupt();
                }catch (Exception e1){}
            }
        }
    }


}
