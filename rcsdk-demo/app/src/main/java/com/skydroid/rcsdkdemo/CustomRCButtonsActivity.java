package com.skydroid.rcsdkdemo;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.skydroid.rcsdk.PayloadManager;
import com.skydroid.rcsdk.RCSDKManager;
import com.skydroid.rcsdk.comm.CommListener;
import com.skydroid.rcsdk.common.button.ButtonAction;
import com.skydroid.rcsdk.common.button.ButtonConfig;
import com.skydroid.rcsdk.common.button.ButtonHandler;
import com.skydroid.rcsdk.common.button.ButtonHandlerListener;
import com.skydroid.rcsdk.common.button.ButtonHelper;
import com.skydroid.rcsdk.common.button.HandleButtonMode;
import com.skydroid.rcsdk.common.callback.CompletionCallback;
import com.skydroid.rcsdk.common.error.ErrorException;
import com.skydroid.rcsdk.common.error.SkyException;
import com.skydroid.rcsdk.common.payload.C10Pro;
import com.skydroid.rcsdk.common.payload.PayloadType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 咔一下
 * @date 2024/4/3 10:15
 * @email 1501020210@qq.com
 * @describe 自定义遥控器按钮
 */
public class CustomRCButtonsActivity extends AppCompatActivity {

    private TextView tv_info;

    //采集遥控器按钮通道值工具类
    private ReadRCButtonHelper readRCButtonHelper;

    //遥控器自定义按钮工具类
    private ButtonHelper c10pButtonHelper;
    private ButtonHelper customButtonHelper;
    private final ButtonHandlerListener buttonHandlerListener = new ButtonHandlerListener() {
        @Override
        public void onButtonActionResult(@NonNull ButtonAction buttonAction, boolean b) {
            if (buttonAction == ButtonAction.GIMBAL_YAW || buttonAction == ButtonAction.GIMBAL_PITCH){
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv = tv_info;
                    if (tv != null){
                        tv.setText("执行动作：" + buttonAction.name() + "，结果：" + b);
                    }
                }
            });

        }
    };

    //C10Pro
    private C10Pro c10Pro;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_rc_buttons);
        tv_info = findViewById(R.id.tv_info);

        //采集遥控器按钮通道数据到自定义按钮工具类
        ReadRCButtonHelper readRCButtonHelper = new ReadRCButtonHelper(RCSDKManager.INSTANCE.getDeviceType());
        this.readRCButtonHelper = readRCButtonHelper;
        readRCButtonHelper.setListener(new ReadRCButtonHelper.RCButtonValueListener() {
            @Override
            public void onRCButtonValue(int[] buttons) {
                ButtonHelper c10pButtonHelper = CustomRCButtonsActivity.this.c10pButtonHelper;
                ButtonHelper customButtonHelper = CustomRCButtonsActivity.this.customButtonHelper;
                if (c10pButtonHelper != null){
                    c10pButtonHelper.receiveButtonData(buttons);
                }
                if (customButtonHelper != null){
                    customButtonHelper.receiveButtonData(buttons);
                }
            }
        });
        readRCButtonHelper.start();

        ButtonHelper c10pButtonHelper = new ButtonHelper();
        ButtonHelper customButtonHelper = new ButtonHelper();
        this.c10pButtonHelper = c10pButtonHelper;
        this.customButtonHelper = customButtonHelper;
        //监听执行结果
        c10pButtonHelper.addListener(buttonHandlerListener);
        customButtonHelper.addListener(buttonHandlerListener);

        //遥控器自定义按钮工具类-启用
        c10pButtonHelper.start();
        customButtonHelper.start();

        //连接C10Pro
        C10Pro c10Pro = (C10Pro)PayloadManager.INSTANCE.getUDPPayload(PayloadType.C10PRO,5000,"192.168.144.108",5000);
        c10Pro.setCommListener(new CommListener() {
            @Override
            public void onConnectSuccess() {
                log("C10Pro 连接成功");
            }

            @Override
            public void onConnectFail(SkyException e) {
                log("C10Pro 连接失败:" + e);
            }

            @Override
            public void onDisconnect() {
                log("C10Pro 断开连接");
            }

            @Override
            public void onReadData(byte[] bytes) {
                log("C10Pro 读到数据:" + bytes);
            }
        });
        this.c10Pro = c10Pro;
        PayloadManager.INSTANCE.connectPayload(c10Pro);
        //配置按钮通道
        List<ButtonConfig> c10pConfigs = new ArrayList<>();
        //适用于H12Pro的配置
        //11通道（H12Pro G滚轮）控制云台偏航  HandleButtonMode有2种类型 -- ALWAYS:持续调用,适用于摇杆控制云台。CHANGE:通道值变化才调用,适用一键控制，拍照，录像等
        c10pConfigs.add(new ButtonConfig(10,ButtonAction.GIMBAL_YAW,HandleButtonMode.ALWAYS));
        //12通道（H12Pro H滚轮）控制云台俯仰
        c10pConfigs.add(new ButtonConfig(11,ButtonAction.GIMBAL_PITCH,HandleButtonMode.ALWAYS));
        //9通道（H12Pro C按钮）控制云台回中
        c10pConfigs.add(new ButtonConfig(8,ButtonAction.GIMBAL_MID,HandleButtonMode.CHANGE));
        //10通道（H12Pro D按钮）控制云台向下
        c10pConfigs.add(new ButtonConfig(9,ButtonAction.GIMBAL_DOWN,HandleButtonMode.CHANGE));

        /*
        //创建默认配置
        //H12Pro 11、12通道控制云台；
        //H16,H30 13、14通道控制云台；
        //H20 14通道控制云台，7通道拍照
        List<ButtonConfig> defConfigs = ButtonHelper.Companion.createDefaultConfig();
        */

        //关联配置与C10Pro
        c10pButtonHelper.setConfig(c10pConfigs,c10Pro);

        //适用于H12Pro的自定义按钮事件
        List<ButtonConfig> customButtonConfigs = new ArrayList<>();
        //6通道（H12Pro A按钮）-自定义0
        customButtonConfigs.add(new ButtonConfig(5,ButtonAction.CUSTOM_0,HandleButtonMode.CHANGE));
        //7通道（H12Pro F拨杆）-自定义1
        customButtonConfigs.add(new ButtonConfig(6,ButtonAction.CUSTOM_1,HandleButtonMode.CHANGE));
        //自定义按钮事件处理器
        ButtonHandler buttonHandler = new ButtonHandler() {
            @Override
            public void onHandleButton(@NonNull ButtonAction buttonAction, int oldValue, int newValue, @NonNull int[] ints, @NonNull CompletionCallback completionCallback) {
                switch (buttonAction){
                    case CUSTOM_0:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(),"处理自定义按钮事件0：" + oldValue + "，" + newValue,Toast.LENGTH_SHORT).show();
                                //标识执行结果-成功
                                completionCallback.onResult(null);
                            }
                        });
                        break;
                    case CUSTOM_1:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(),"处理自定义按钮事件1：" + oldValue + "，" + newValue,Toast.LENGTH_SHORT).show();
                                if (newValue == 1950){
                                    //标识执行结果-成功
                                    completionCallback.onResult(null);
                                }else {
                                    //标识执行结果-失败
                                    completionCallback.onResult(new ErrorException());
                                }

                            }
                        });
                        break;
                }
            }
        };
        //关联配置与事件处理器
        customButtonHelper.setConfig(customButtonConfigs,buttonHandler);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (readRCButtonHelper != null) {
            readRCButtonHelper.stop();
        }
        //遥控器自定义按钮工具类-关闭
        ButtonHelper c10pButtonHelper = this.c10pButtonHelper;
        ButtonHelper customButtonHelper = this.customButtonHelper;
        if (c10pButtonHelper != null){
            c10pButtonHelper.removeListener(buttonHandlerListener);
            c10pButtonHelper.stop();
        }
        if (customButtonHelper != null){
            customButtonHelper.removeListener(buttonHandlerListener);
            customButtonHelper.stop();
        }
        C10Pro c10Pro = this.c10Pro;
        if (c10Pro != null){
            PayloadManager.INSTANCE.disconnectPayload(c10Pro);
        }

    }

    private void log(Object obj) {
        if (obj == null) {
            return;
        }
        Log.e(CustomRCButtonsActivity.class.toString(), obj.toString());
    }
}
