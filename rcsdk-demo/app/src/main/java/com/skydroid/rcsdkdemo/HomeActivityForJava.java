package com.skydroid.rcsdkdemo;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.skydroid.rcsdk.*;
import com.skydroid.rcsdk.comm.CommListener;
import com.skydroid.rcsdk.common.Uart;
import com.skydroid.rcsdk.common.callback.*;
import com.skydroid.rcsdk.common.error.SkyException;
import com.skydroid.rcsdk.common.payload.*;
import com.skydroid.rcsdk.common.pipeline.Pipeline;
import com.skydroid.rcsdk.common.remotecontroller.ControlMode;
import com.skydroid.rcsdk.key.AirLinkKey;
import com.skydroid.rcsdk.key.RemoteControllerKey;
import com.skydroid.rcsdk.utils.RCSDKUtils;
import com.skydroid.rcsdkdemo.other.AppUtils;
import com.skydroid.rcsdkdemo.other.EnumInfoKey;
import com.skydroid.rcsdkdemo.other.ReceiveInfo;

import java.util.Arrays;

/**
 * @author 咔一下
 * @date 2023/10/11 9:50
 * @email 1501020210@qq.com
 * @describe
 * <p>
 * 加入C10Pro 新旧固件 相机控制;UI重新整理; by ljb on 2024.06.13.
 */
public class HomeActivityForJava extends AppCompatActivity {
    public static final String TAG = "HomeActivityForJava";
    private ReceiveInfo mReceiveInfo = new ReceiveInfo();
    private final KeyListener<Integer> keySignalQualityListener = new KeyListener<Integer>() {
        @Override
        public void onValueChange(Integer oldValue, Integer newValue) {
            printInfo(EnumInfoKey.Signal,"信号强度:" + newValue);
        }
    };

    private final KeyListener<int[]> keyH16ChannelsListener = new KeyListener<int[]>() {
        @Override
        public void onValueChange(int[] oldValue, int[] newValue) {
            printInfo(EnumInfoKey.H16Channels,Arrays.toString(newValue));
        }
    };

    private final MutableLiveData<String> infoLiveData = new MutableLiveData<String>();
    private TextView tvInfo = null;
    private EditText etData = null;

    private Pipeline pipeline = null;
    private C10Pro c10Pro = null;// 适用于0.2.7及以上固件 相机控制 + 全版本的云台控制
    private C10ProCamera c10ProCamera = null;// 适用于0.2.7以下固件 相机控制
    // TODO 注意:
    // TODO 使用时,请确保其他应用(包含助手、地面站)处于停止关闭状态,避免端口占用导致数据链路失败;
    // TODO 获取摇杆杆量值,无法主动上报,请求一次获取一次,推荐至少100ms读取一次;
    // TODO 数传管道,未连接 接收机 时,数传管道 连接失败;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        tvInfo = findViewById(R.id.tv_info);
        etData = findViewById(R.id.et_data);
        infoLiveData.observe(this, new Observer<String>() {
            @Override
            public void onChanged(String str) {
                tvInfo.setText(str);
            }
        });
        // TODO 初始化SDK,初始化一次即可;
        RCSDKManager.INSTANCE.initSDK(this, new SDKManagerCallBack() {
            @Override
            public void onRcConnected() {
                //创建通讯管道(内部有断开重连机制，只需要调用一次连接即可)
                // 数传管道,未连接 接收机 时,数传管道 连接失败;
                Pipeline pipeline = PipelineManager.INSTANCE.createPipeline(Uart.UART0);
                pipeline.setOnCommListener(getCommListener(0, "数传管道"));
                //连接通讯管道
                PipelineManager.INSTANCE.connectPipeline(pipeline);
                HomeActivityForJava.this.pipeline = pipeline;
            }

            @Override
            public void onRcConnectFail(@Nullable SkyException e) {

            }

            @Override
            public void onRcDisconnect() {

            }
        });
        RCSDKManager.INSTANCE.setMainThreadCallBack(true); //设置在主线程回调
        //连接到遥控器
        RCSDKManager.INSTANCE.connectToRC();

        //旧三体相机网口版
//        ThreeBodyCamera2 threeBodyCamera2 = (ThreeBodyCamera2)PayloadManager.INSTANCE.getTCPPayload(PayloadType.THREE_BODY_CAMERA2, "192.168.144.108", 5001);
        //旧三体相机串口版
//        ThreeBodyCamera threeBodyCamera = (ThreeBodyCamera)PayloadManager.INSTANCE.getSerialPortPayload(PayloadType.THREE_BODY_CAMERA, "/dev/ttyHS0", 4000000);
        //C20相机
//        C20Camera c20Camera = (C20Camera)PayloadManager.INSTANCE.getTCPPayload(PayloadType.C20_CAMERA, "192.168.144.108", 8100);
        //C20云台
//        C20Gimbal c20Gimbal = (C20Gimbal)PayloadManager.INSTANCE.getTCPPayload(PayloadType.C20_GIMBAL, "192.168.144.108", 5000);

        //C10Pro相机控制（或新三体相机网口版）
        c10Pro = (C10Pro) PayloadManager.INSTANCE.getUDPPayload(PayloadType.C10PRO,5000,"192.168.144.108",5000);
        //内部已经实现重连机制，无需再实现
        if (c10Pro != null){
            c10Pro.setCommListener(getCommListener(1, "C10Pro"));
            PayloadManager.INSTANCE.connectPayload(c10Pro);
        }
        c10ProCamera = (C10ProCamera) PayloadManager.INSTANCE.getUDPPayload(PayloadType.C10PRO_CAMERA,12580,"192.168.144.108",12580);
        //内部已经实现重连机制，无需再实现
        if (c10ProCamera != null){
            c10ProCamera.setCommListener(getCommListener(2, "C10pCamera"));
            PayloadManager.INSTANCE.connectPayload(c10ProCamera);
        }
        initTestView();
        setTitle("RCSDK_Demo_V" + RCSDKUtils.getVersion() + " java版  Device:" +RCSDKUtils.getDeviceType() );
    }

    private CommListener getCommListener(int type, String tag) {
        return new CommListener() {
            @Override
            public void onConnectSuccess() {
                log(tag + " 连接成功");
            }

            @Override
            public void onConnectFail(SkyException e) {
                log(tag + " 连接失败" + e);
            }

            @Override
            public void onDisconnect() {
                log(tag + " 断开连接");
            }

            @Override
            public void onReadData(byte[] bytes) {
                if (type == 0) {
                    log(tag + " 收到长度 " + bytes.length + " ,,, 数据 " + new String(bytes));
                    // 数传管道
                    printInfo(EnumInfoKey.DataTransmission, "数传：" + new String(bytes));
                }
            }
        };
    }

    private void initTestView() {
        findViewById(R.id.btn_pairing).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KeyManager.INSTANCE.action(RemoteControllerKey.INSTANCE.getKeyRequestPairing(), new CompletionCallback() {
                    @Override
                    public void onResult(SkyException e) {
                        printInfo(EnumInfoKey.Other, AppUtils.getSkyExceptionInfo("对频", e, ""));
                    }
                });
            }
        });
        findViewById(R.id.btn_set_control_mode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KeyManager.INSTANCE.set(RemoteControllerKey.INSTANCE.getKeyControlMode(), ControlMode.USA, new CompletionCallback() {
                    @Override
                    public void onResult(SkyException e) {
                        printInfo(EnumInfoKey.SetControlMode, AppUtils.getSkyExceptionInfo("设置摇杆模式", e, ""));
                    }
                });
            }
        });
        findViewById(R.id.btn_get_control_mode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //获取遥控器手型模式
                KeyManager.INSTANCE.get(RemoteControllerKey.INSTANCE.getKeyControlMode(), new CompletionCallbackWith<ControlMode>() {
                    @Override
                    public void onSuccess(ControlMode controlMode) {
                        printInfo(EnumInfoKey.GetControlMode,"获取摇杆模式：" + controlMode.name());
                    }

                    @Override
                    public void onFailure(SkyException e) {
                        printInfo(EnumInfoKey.GetControlMode,"获取摇杆模式失败：" + e);
                    }
                });
            }
        });
        findViewById(R.id.btn_get_channels).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //获取摇杆杆量
                switch (RCSDKManager.INSTANCE.getDeviceType()){
                    case H16:
                        //防止反复监听
                        KeyManager.INSTANCE.cancelListen(keyH16ChannelsListener);
                        //H16/H16Pro的摇杆杆量为LISTEN方式,设置监听器后，会一直回调，直到取消监听
                        KeyManager.INSTANCE.listen(RemoteControllerKey.INSTANCE.getKeyH16Channels(), keyH16ChannelsListener);
                        break;
                    default:
                        //H12/H12Pro/H30摇杆杆量为GET方式，需要主动请求，请求一次获取一次
                        KeyManager.INSTANCE.get(RemoteControllerKey.INSTANCE.getKeyChannels(), new CompletionCallbackWith<int[]>() {
                            @Override
                            public void onSuccess(int[] value) {
                                printInfo(EnumInfoKey.Channels,"获取摇杆杆量：" + Arrays.toString(value));
                            }

                            @Override
                            public void onFailure(SkyException e) {
                                printInfo(EnumInfoKey.Channels,"获取摇杆失败：" + e);
                            }
                        });
                        break;
                }
            }
        });
        // 信号强度 取值范围: 0-100%
        findViewById(R.id.btn_get_signal).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (RCSDKManager.INSTANCE.getDeviceType()){
                    case H12:
                        //H12的信号强度为GET方式，需要主动请求，请求一次获取一次
                        KeyManager.INSTANCE.get(AirLinkKey.INSTANCE.getKeyH12SignalQuality(), new CompletionCallbackWith<Integer>() {
                            @Override
                            public void onSuccess(Integer integer) {
                                printInfo(EnumInfoKey.Signal,"H12信号强度：" + integer);
                            }

                            @Override
                            public void onFailure(SkyException e) {
                                printInfo(EnumInfoKey.Signal,"H12信号强度获取失败：" + e);
                            }
                        });
                        break;

                    default:
                        //防止反复监听
                        KeyManager.INSTANCE.cancelListen(keySignalQualityListener);
                        //除了H12,其他遥控器的信号强度为LISTEN方式,设置监听器后，会一直回调，直到取消监听
                        KeyManager.INSTANCE.listen(AirLinkKey.INSTANCE.getKeySignalQuality(),keySignalQualityListener);
                        break;
                }

            }
        });

        findViewById(R.id.btn_akey).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                c10pCameraControl(false);
            }
        });
        findViewById(R.id.btn_akey_027).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                c10pCameraControl(true);
            }
        });
        findViewById(R.id.btn_rc_buttons).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivityForJava.this,CustomRCButtonsActivity.class));
            }
        });
        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mReceiveInfo.cleatInfo();
                tvInfo.setText("");
            }
        });
        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (etData == null) {
                    return;
                }
                String temp = etData.getText().toString();
                if (TextUtils.isEmpty(temp)) {
                    Toast.makeText(getApplicationContext(), "请输入要发送的字符!", Toast.LENGTH_SHORT).show();
                    return;
                }
                HomeActivityForJava.this.pipeline.writeData(temp.getBytes());
                Toast.makeText(getApplicationContext(), "发送 $temp", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_led).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ledCameraControl(false);
            }
        });
        findViewById(R.id.btn_led_027).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ledCameraControl(true);
            }
        });
    }

    /**
     * 新网口三体相机控制LED灯
     */
    private void ledCameraControl(boolean isCameraVer027AndAbove){
        AppUtils.showC10pCameraControlDialog(this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case 0:
                        if (isCameraVer027AndAbove){
                            if (c10Pro != null) {
                                c10Pro.setLed(true, new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.LED, AppUtils.getSkyExceptionInfo("LED开灯", e, "新固件"));
                                    }
                                });
                            }
                        }else {
                            if (c10ProCamera != null){
                                c10ProCamera.setLED(true, new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.LED, AppUtils.getSkyExceptionInfo("LED开灯", e, "旧固件"));
                                    }
                                });
                            }
                        }
                        break;
                    case 1:
                        if (isCameraVer027AndAbove){
                            if (c10Pro != null) {
                                c10Pro.setLed(false, new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.LED, AppUtils.getSkyExceptionInfo("LED关灯", e, "新固件"));
                                    }
                                });
                            }
                        }else {
                            if (c10ProCamera != null){
                                c10ProCamera.setLED(false, new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.LED, AppUtils.getSkyExceptionInfo("LED关灯", e, "旧固件"));
                                    }
                                });
                            }
                        }
                        break;
                }
            }
        });
    }

    /**
     * 云台控制_相机控制
     */
    private void c10pCameraControl(boolean isCameraVer027AndAbove) {
        AppUtils.showC10pCameraControlDialog(this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                switch (i) {
                    case 0:
                        if (c10ProCamera != null) {
                            c10ProCamera.getVersion(new CompletionCallbackWith<String>() {
                                @Override
                                public void onSuccess(String version) {
                                    printInfo(EnumInfoKey.CameraVersion, "获取到相机版本号:" + version);
                                }

                                @Override
                                public void onFailure(SkyException p0) {
                                    printInfo(EnumInfoKey.CameraVersion, "获取到相机版本号:" + p0);
                                }
                            });
                        }
                        break;

                    case 1:
                        if (c10Pro != null) {
                            c10Pro.akey(AKey.DOWN);
                        }
                        break;
                    case 2:
                        if (c10Pro != null) {
                            c10Pro.akey(AKey.MID);
                        }
                    case 3:
                        if (c10Pro != null) {
                            c10Pro.akey(AKey.TOP);
                        }
                        break;
                    case 4:
                        if (isCameraVer027AndAbove) {
                            if (c10Pro != null) {
                                c10Pro.takePicture(new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.TakePicture, AppUtils.getSkyExceptionInfo("拍照", e, "新固件"));
                                    }
                                });
                            }

                        } else {
                            if (c10ProCamera != null) {
                                c10ProCamera.takePicture(new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.TakePicture, AppUtils.getSkyExceptionInfo("拍照", e, "旧固件"));
                                    }
                                });
                            }
                        }
                        break;
                    case 5:
                        if (isCameraVer027AndAbove) {
                            if (c10Pro != null) {
                                c10Pro.startRecordVideo(new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.RecordVideo, AppUtils.getSkyExceptionInfo("开始录像", e, "新固件"));
                                    }
                                });
                            }

                        } else {
                            if (c10ProCamera != null) {
                                c10ProCamera.startRecordVideo(new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.RecordVideo, AppUtils.getSkyExceptionInfo("开始录像", e, "旧固件"));
                                    }
                                });
                            }
                        }
                        break;
                    case 6:
                        if (isCameraVer027AndAbove) {
                            if (c10Pro != null) {
                                c10Pro.stopRecordVideo(new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.RecordVideo, AppUtils.getSkyExceptionInfo("停止录像", e, "新固件"));
                                    }
                                });
                            }

                        } else {
                            if (c10ProCamera != null) {
                                c10ProCamera.stopRecordVideo(new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.RecordVideo, AppUtils.getSkyExceptionInfo("停止录像", e, "旧固件"));
                                    }
                                });
                            }
                        }
                        break;
                    case 7:
                        if (isCameraVer027AndAbove) {
                            if (c10Pro != null) {
                                c10Pro.setTime(System.currentTimeMillis(), new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.CameraTime, AppUtils.getSkyExceptionInfo("时间设置", e, "新固件"));
                                    }
                                });
                            }

                        } else {
                            if (c10ProCamera != null) {
                                c10ProCamera.setTime(System.currentTimeMillis(), new CompletionCallback() {
                                    @Override
                                    public void onResult(SkyException e) {
                                        printInfo(EnumInfoKey.CameraTime, AppUtils.getSkyExceptionInfo("时间设置", e, "旧固件"));
                                    }
                                });
                            }
                        }
                        break;
                    case 8:// 航向命令，右，速度30
                        if (c10Pro != null) {
                            //c10Pro.controlYaw(3f);// 方法一 接口
                            c10Pro.writeData("#TPUG2WGSY1E75".getBytes());// 方法二 协议
                        }
                        break;
                    case 9:// 航向命令，左，速度-30
                        if (c10Pro != null) {
                            //c10Pro.controlYaw(-3f);// 方法一 接口
                            c10Pro.writeData("#TPUG2wGSYE276".getBytes());// 方法二 协议
                        }
                        break;
                    case 10:// 俯仰命令，上，速度30
                        if (c10Pro != null) {
                            //c10Pro.controlPitch(3f);// 方法一 接口
                            c10Pro.writeData("#TPUG2WGSP1E6C".getBytes());// 方法二 协议
                        }
                        break;
                    case 11:// 俯仰命令，下，速度-30
                        if (c10Pro != null) {
                            //c10Pro.controlPitch(-3f);// 方法一 接口
                            c10Pro.writeData("#TPUG2WGSPE26D".getBytes());// 方法二 协议
                        }
                        break;
                }
            }
        });
    }

    private void printInfo(EnumInfoKey key, Object obj){
        if (obj == null){
            return;
        }
        String sb = mReceiveInfo.updateInfo(key, obj);
        if (sb != null) {
            infoLiveData.postValue(sb);
        }
        log("printInfo -------key " + key + ",,,obj " + obj);
    }

    private void log(Object obj){
        if (obj == null){
            return;
        }
        Log.e(TAG,obj.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        infoLiveData.removeObservers(this);
        //断开遥控器连接（如果不断开，程序还在运行的时候，其他程序会出端口占用情况）
        RCSDKManager.INSTANCE.disconnectRC();
        KeyManager.INSTANCE.cancelListen(keySignalQualityListener);
        Pipeline p = this.pipeline;
        if (p != null){
            PipelineManager.INSTANCE.disconnectPipeline(p);
        }
        C10Pro localC10p = this.c10Pro;
        if (localC10p != null){
            PayloadManager.INSTANCE.disconnectPayload(localC10p);
        }
    }
}
