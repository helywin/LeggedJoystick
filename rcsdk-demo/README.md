更新日志
```
v1.9.2
1.assets文件夹下的程序支持16KB page size

v1.9.1
1.支持16KB page size

v1.9.0     
1.G16
2.G系列信号数据:is_pairing与dev_connect同步
3.相机协议增加切换长短焦接口

v1.8.4
修复Bug     
1.H16获取不到通道设置参数

v1.8.3
修复Bug     
1.G系列获取版本号偶尔崩溃问题
2.H12上获取感量报错问题
3.其他Bug

v1.8.1
新增功能
1.G系列新上传模式接口
    AirLinkKey.KeyGLinkSpeedMode
        GLinkSpeedMode.UPLOAD:上传模式
        GLinkSpeedMode.NORMAL:关闭上传模式
修复Bug     
1.处理G系列对频失败时没有恢复原来的对频状态
2.处理G系列连接状态错误问题

v1.8.0
新增功能
1.支持G30
2.PayloadManager新增createXXXPayload方法
3.物理按钮控制云台功能-新增选项:向下/回中/向上、向下/回中
4.Debug打印开关--RCSDKManager.setDebug

修复Bug
1.C20自定义遥控器按钮LED控制
2.串口通讯BUG（调用RCSDKManager.disconnectRC()，后重新连接遥控器会导致串口连接直接断开）

优化
1.Pipeline/Payload多次连接重复监听问题--目前修改为只会监听一次
2.优化云台控制速度
3.设置MAC的对频方式接口参数修改
4.RCSDKManager.disconnectRC()调用后无法重新连接问题
5.RCSDKManager.disconnectRC()关闭PayloadManager/PipelineManager中的连接--目前修改为不关闭PayloadManager/PipelineManager中的连接

v1.7.9
1.修复BUG
    G系列获取信号强度偶尔数据卡死不刷新问题
2.新增接口
    AirLink.KeySkySetAutoMCSDuration:持续时间内自适应MCS(G系列)
  通用云台相机控制接口
    PayloadManager.getXXXPayload(PayloadType.COMMON,5000,"192.168.144.108",5000) as CommonPayload?
    
v1.7.8
1.G系列对频时间改为30s
2.新增G系列退出对频模式接口
    RemoteControllerKey.KeyRequestStopPair(停止对频，G系列有效)
3.关闭调试日志
    
v1.7.7
1.修复1.7.6部分场景下对频无响应问题
2.新增Key
    RemoteControllerKey.KeyButtonToneAndLongPress(按键音和长按设置，支持G系列遥控器MCU固件1.3及以上。可参考设备助手2.9.5)
    RemoteControllerKey.KeyButtonSaveValue(掉电保存按钮值，支持G系列遥控器MCU固件1.3及以上。可参考设备助手2.9.5)
    RemoteControllerKey.KeyRollerMode(滚轮模式，支持G系列遥控器MCU固件1.3及以上。可参考设备助手2.9.5)
    RemoteControllerKey.KeyMixedControlAndNoReturnCenter(混控和油门死区设置。支持G系列遥控器MCU固件1.3及以上，可参考设备助手2.9.5)
    RemoteControllerKey.KeyButtonLockMode(自锁模式，支持G系列遥控器MCU固件1.3及以上。可参考设备助手2.9.5)

v1.7.6
1.支持G系列射频固件20250402对频
2.G系列接口稳定性
3.TCP连接收到FIN时回调onDisconnect

v1.7.4
1.修复Bug
    *部分情况下在G系列遥控器中崩溃问题

v1.7.3
1.G12/G20数传管道修改为UDP（需要更新20250110及以后的Android系统版本）
2.修复Bug
    部分情况下自定义遥控按钮工具类可能引发阻塞问题
    
v1.7.2
1.修复Bug
    部分遥控器(H12Pro、G12、G20)偶尔信号显示错误问题
2.新增Key
    915模块使能(支持G20)
    RemoteControllerKey.KeyModule915Enable
    
v1.7.1
1.修复Bug

v1.6.8
1.修复G12、G20数传管道内存泄漏问题

v1.6.6
1.G20遥控器
2.新增Key
    AirLinkKey.KeyRCSetReTxCount(配置地面端重传次数,提升链路可靠性,支持G12、G20)
    AirLinkKey.KeySkySetReTxCount(配置天空端重传次数,提升链路可靠性,支持G12、G20)
    AirLinkKey.KeySetAutoMCS(自适应MCS,可提升上行速度,支持G12、G20)

v1.6.5
1.修复Bug
    H20遥控器获取不到信号百分比问题

v1.6.4
1.G12遥控器
2.修复Bug
    部分情况下KeyManager会阻塞300ms的问题
    
v1.6.3
1.修复Bug
    获取遥控器通道指令
    
v1.6.2
1.新增Key：
    AirLinkKey.KeyRCVersion(遥控器端无线模块版本号,目前支持H20,H30)
    AirLinkKey.KeySkyVersion(天空端端无线模块版本号,目前支持H20,H30)
    AirLinkKey.KeySkyMCUVersion(天空端MUC版本号,目前支持H30)
    AirLinkKey.KeyRawSignalQuality(原始信号数据,目前支持H16/H12Pro/H20/H30)
2.SDK版本号获取
3.修复H20波特率设置错误问题
4.底层请求逻辑优化*

v1.5.2
1.C12无级变倍
    C12::addZoomRatios
    C12::subtractZoomRatios
2.H20遥控器自定义按钮波轮控制默认反向
3.C12云台控制新增接口
    同时控制俯仰偏航：C12::controlYawPitch（需要云台固件0.5及以上）

v1.5.2
1.C12无级变倍
    C12::addZoomRatios
    C12::subtractZoomRatios
2.H20遥控器自定义按钮波轮控制默认反向
3.C12云台控制新增接口
    同时控制俯仰偏航：C12::controlYawPitch（需要云台固件0.5及以上）

v1.5.0
1.新增SDK工具类获取遥控器型号方法
com.skydroid.rcsdk.utils.RCSDKUtils

v1.4.9 
1.修复部分情况下拍照阻塞的bug
2.修复H20串口0、串口1相反问题
3.H30数传通讯默认使用UDP通讯

v1.4.7
1.修复部分H16数传接收多次问题
2.C10Pro OSD设置

v1.4.5
1.C12云台相机
2.C10Pro云台相机（建议使用C10Pro类代替C10ProCamera类与C10ProGimbal类，C10Pro相机固件需要v0.2.7及以上才支持C10Pro类，v0.2.7以前使用C10ProCamera类与C10ProGimbal类）
3.新增遥控器自定义按钮事件工具类（详细使用方法请查阅相关代码：CustomRCButtonsActivity）
4.修复Bug
    disconnectRC崩溃问题等

v1.3.4
1.新增Key：
    AirLinkKey.KeyH20Bandwidth(设置/获取H20遥控器带宽)

v1.3.3
1.EC10遥控器

v1.3.2
1.C20 LED开关

v1.3.1
1.优化TCP通讯重连机制
2.调整遥控器协议超时时间（100ms）

v1.3.0
1.新增Key：
    RemoteControllerKey.KeyModel(获取遥控器固件型号)
    RemoteControllerKey.KeyVersion(获取遥控器固件版本号)
2.优化CPU占用
3.优化UDP通讯过滤规则
4.优化混淆规则,防止与其他第三方库冲突

v1.2.1
1.修复H20信号强度错误问题
2.C10/C10Pro/C20云台控制新增角度控制方法
3.调整C10/C10Pro/C20云台速度控制参数(请参考文档C10/C10Pro/C20云台控制章节)
4.支持串口双轴云台相机控制

v1.1.0
支持H20遥控器

v0.9.7
支持C20云台相机

v0.8.5
1.修复同时多个TCP连接时阻塞问题
2.修复PipelineManager,PayloadManager连接不上时无法关闭问题

v0.8.3
1.新增云卓配件管理（C10、三体相机等）
2.修复UDPPipeline连接状态错误问题
3.新增Key:
    AirLinkKey.KeyH16RawSignalQuality(获取H16原始信号值DBM)

v0.7.1
支持H30遥控器

v0.6
支持H16遥控器

v0.1
发布第一版
```

# Demo 工程

下载或者克隆Git上的Android示例代码工程:https://gitee.com/skydroid/rcsdk-demo

<font color=blue>
使用注意事项:<br>
1.请确保其他应用(包含助手、地面站)处于停止关闭状态,避免端口占用导致数据链路失败;<br>
2.获取摇杆杆量值,无法主动上报,请求一次获取一次,推荐至少100ms读取一次;<br>
3.数传管道,未连接 接收机 时,数传管道 连接失败;<br>
</font>

<br>
<br>
如下是H12Pro+S1pro+C12Pro的测试效果图:

![image](https://gitee.com/skydroid/rcsdk-demo/raw/master/image/rcsdk_demo_1.png)
![image](https://gitee.com/skydroid/rcsdk-demo/raw/master/image/rcsdk_demo_2.png)


# RCSDK目前支持的遥控器产品
H12、H12Pro、H16/H16Pro、H30、H20、G12、G20、G30、G16

# RCSDK架构体系概述
移动应用程序一般通过下图所示的几个主要类来访问RCSDK：
![image](https://gitee.com/skydroid/rcsdk-demo/raw/master/image/rcsdk.png)

- RCSDKManager： RCSDK工具包的入口类，管理RCSDK的初始化，反初始化，连接，以及监听硬件产品的连接事件。
- KeyManager： RCSDK使用了以Key为基础元素的参数设置和参数获取功能接口
- PipelineManager：与第三方设备数据传输的入口
- PayloadManager：控制云卓相关配件(C10、三体相机等)的入口


# 空白项目集成 SDK
本指引介绍如何将 RCSDK-Demo 中的 RCSDK包移植到用户的空白项目中

```
本指引中使用的 Android Studio 版本为 Android Studio Chipmunk | 2021.2.1 Patch 1

SDK所需权限
<uses-permission android:name="android.permission.INTERNET" />

Kotlin版本为：1.6.10

混淆
-keep class com.skydroid.**{*;}
```
- ### 导入SDK AAR包

```
rcsdk-v1.9.2.aar
h16_airlink.aar //H16图传模块 minSdk 24
```

- ### 修改build.gradle(app) 文件
在 dependencies 项里添加SDK包
```
    implementation files("libs/rcsdk-v1.9.2.aar")
    implementation files('libs/h16_airlink.aar')//可选,H16遥控器图传模块,如果不是H16遥控器,无需导入,该模块minSdk为24
```

- ### 修改 AndroidManifest.xml 文件

参照 Demo 的 AndroidManifest.xml添加SDK 需要的最基础权限
```
<uses-permission android:name="android.permission.INTERNET" />
```

- ### 初始化RCSDK
在使用SDK各组件之前初始化context信息;  
初始化一次即可;  
推荐在Application中初始化;
```
RCSDKManager.initSDK(this,object :SDKManagerCallBack{
            override fun onRcConnectFail(e: SkyException?) {
                //连接失败
            }

            override fun onRcConnected() {
                //设备连接
            }

            override fun onRcDisconnect() {
                //设备断开连接
            }
        })
```

- ### 连接遥控器
```
RCSDKManager.connectToRC()
```

- ### 断开遥控器
注意：不使用时需要断开连接，否则会一直占用端口
```
RCSDKManager.disconnectRC()
```

# KeyManager
遥控器参数设置、获取功能接口

- ### SET
```
//设置遥控器控制模式
KeyManager.set(RemoteControllerKey.KeyControlMode, ControlMode.JP) {
                        e ->
                    if (e == null){
                        log("设置摇杆模式成功") //success
                    }else{
                        log("设置摇杆模式失败：${e}") } //fail
                    }
```

- ### GET
```
获取遥控器控制模式
KeyManager.get(RemoteControllerKey.KeyControlMode,object :
                    CompletionCallbackWith<ControlMode> {
                    override fun onSuccess(result: ControlMode?) {
                        //获取成功
                        log(result)
                    }

                    override fun onFailure(e: SkyException?) {
                        //获取失败
                        log(e)
                    }
                })
```

- ### ACTION
```
遥控器对频
KeyManager.action(RemoteControllerKey.KeyRequestPairing){
                e ->
                if (e == null){
                    log("进入对频模式成功") //success
                }else{
                    log("对频失败：${e}") //fail
                }
            }
```

- ### LISTEN
```
var keySignalQualityListener = KeyListener<Int>{
        oldValue, newValue ->
        Log.e(TAG,"信号强度:${oldValue},${newValue}")
    }
    
//监听H12Pro信号强度 (取值范围: 0-100%)
KeyManager.listen(AirLinkKey.KeySignalQuality,keySignalQualityListener)

//取消监听H12Pro信号强度
KeyManager.cancelListen(keySignalQualityListener)
```

# PipelineManager
与第三方设备通讯接口

- ### 与第三方设备(例如飞控)通讯
```
//创建通讯管道
pipeline = PipelineManager.createPipeline()
pipeline?.let {
    //设置监听
    it.onCommListener = object : CommListener{
        override fun onConnectSuccess() {
            log("管道连接成功")
        }

        override fun onConnectFail(e: SkyException?) {
            log("管道连接失败${e}")
        }

        override fun onDisconnect() {
            log("管道断开连接")
        }

        override fun onReadData(data: ByteArray?) {
            //第三方设备发送的数据
        }

    }
    //连接通讯管道
    PipelineManager.connectPipeline(it)
}

//发送数据到第三方设备
pipeline?.let {
    it.writeData(bytes)
}

//断开通讯管道
pipeline?.let {
    PipelineManager.disconnectPipeline(it)
}
```

自定义创建通讯管道方法
```
//根据遥控器类型创建通讯管道
PipelineManager.createPipeline(DeviceType.H12Pro)

//创建自定义串口通讯管道
PipelineManager.createSerialPipeline("/dev/ttyHS1",921600)

//创建UDP通讯管道
//参数1:本地端口号;参数2:远程接收端IP;参数3:远程接收端端口号
PipelineManager.createUDPPipeline(14550,"192.168.144.10",14550)

//创建TCP通讯管道
PipelineManager.createTCPPipeline("192.168.144.101",14550)

//创建串口0通讯管道
PipelineManager.createPipeline(Uart.UART0)

//创建串口1通讯管道
PipelineManager.createPipeline(Uart.UART1)

//创建G12G20通讯管道(适用于G12、G20)
PipelineManager.createG12G20Pipeline()

```

# Key
### RemoteControllerKey
- ##### 遥控器摇杆模式
```
    /**
     * 遥控器摇杆模式
     * 访问方式
     * SET,GET
     * 支持ALL
     */
    val KeyControlMode: KeyInfo<ControlMode> = KeyInfo.Builder<ControlMode>()
        .canSet(true)
        .canGet(true)
```

- ##### H12摇杆通道设置
```
    /**
     * H12通道
     * 访问方式
     * SET,GET
     * 支持H12
     */
    val KeyH12ChannelSettings: KeyInfo<H12ChannelSettings> = KeyInfo.Builder<H12ChannelSettings>()
        .canSet(true)
        .canGet(true)
```

- ##### 摇杆通道设置
```
    /**
     * 通道设置
     * 访问方式
     * SET,GET
     * 支持H12Pro/H16/H30/H20/G12/G20/G30/G16
     */
    val KeyChannelSettings: KeyInfo<ChannelSettings> = KeyInfo.Builder<ChannelSettings>()
        .canSet(true)
        .canGet(true)
```

- ##### 遥控器对频
```
    /**
     * 遥控器对频
     * 访问方式
     * ACTION
     * 支持ALL
     */
    val KeyRequestPairing: KeyInfo<EmptyMsg> = KeyInfo.Builder<EmptyMsg>()
        .canAction(true)
```

- ##### 遥控器序列号
```
    /**
     * 遥控器序列号
     * 访问方式
     * GET
     * 支持ALL
     */
    val KeySerialNumber: KeyInfo<String> = KeyInfo.Builder<String>()
        .canGet(true)
```

- ##### 遥控器摇杆感量
```
    /**
     * 遥控器摇杆感量
     * 访问方式
     * GET
     * 支持H12/H12Pro/H30/H20/G12/G20/G30/G16
     */
    val KeyChannels: KeyInfo<IntArray> = KeyInfo.Builder<IntArray>()
        .canGet(true)
```

- ##### H16遥控器摇杆感量
```
    /**
     * H16遥控器摇杆感量
     * 访问方式
     * LISTEN
     * 支持H16
     */
    val KeyH16Channels: KeyInfo<IntArray> = KeyInfo.Builder<IntArray>()
        .canListen(true)
```

- ##### 教练模式
```
    /**
     * 教练模式
     * 访问方式
     * SET,GET
     * 支持H12Pro/H16/H30
     */
    val KeyCoachMode: KeyInfo<CoachMode> = KeyInfo.Builder<CoachMode>()
        .canSet(true)
        .canGet(true)
```

- ##### 自定义数据
```
    /**
     * 自定义数据 200byte
     * 访问方式
     * GET,SET
     * 支持ALL
     */
    val KeyCustomData: KeyInfo<ByteArray> = KeyInfo.Builder<ByteArray>()
        .canGet(true)
        .canSet(true)
```

- ##### 遥控器型号
```
    /**
     * 遥控器型号
     * 访问方式
     * GET
     * 支持ALL
     */
    val KeyModel:KeyInfo<String> = KeyInfo.Builder<String>()
        .canGet(true)
```

- ##### 遥控器版本
```
    /**
     * 遥控器版本
     * 访问方式
     * GET
     * 支持ALL
     */
    val KeyVersion:KeyInfo<String> = KeyInfo.Builder<String>()
        .canGet(true)
```

- ##### 915模块使能
```
    /**
     * 控制915模块使能
     * 访问方式
     * SET,GET
     * 支持G20
     */
    val KeyModule915Enable:KeyInfo<Boolean> = KeyInfo.Builder<Boolean>()
        .canSet(true)
        .canGet(true)
```

### AirLinkKey

- ##### 接收机串口0波特率
```
    /**
     * 图传接收机串口0波特率
     * 访问方式
     * SET,GET
     * 支持H12Pro/G12/G20/G30/G16
     */
    val KeyUart0BaudRate:KeyInfo<UartBaudRate> = KeyInfo.Builder<UartBaudRate>()
        .canSet(true)
        .canGet(true)
```

- ##### H12Pro接收机RC通道失控保护值
```
    /**
     * 接收机RC通道失控保护值
     * 访问方式
     * SET,GET
     * 支持H12Pro
     */
    val KeyLostSBUSValues:KeyInfo<LostSBUSValues> = KeyInfo.Builder<LostSBUSValues>()
        .canSet(true)
        .canGet(true)
```

- ##### 接收机信号质量(原始数据)
```
    /**
     * 接收机信号质量(原始数据)
     * 访问方式
     * LISTEN
     * 支持H16/H12Pro/H20/H30/G12/G20/G30/G16
     */
    val KeyRawSignalQuality:KeyInfo<String> = KeyInfo.Builder<String>()
        .canListen(true)
    获取的数据格式如下：
        G系列
            {
                "dev_connect": false, //连接状态
                "is_pairing":false, //是否对频中状态
	            "ap_ldpc_err": "0", //遥控器-交织块中解码错误的LDPC块个数所占的比例
	            "ap_ldpc_num": "0", //遥控器-解码错误的帧个数比例
	            "ap_snr": "0", //遥控器-SNR
	            "ap_gain_a": "0", //遥控器-A路天线接收信号强度
	            "ap_gain_b": "0", //遥控器-B路天线接收信号强度
	            "ap_tx_mcs": "0", //遥控器-发射MCS
	            "ap_tx_power": "0", //遥控器-发送功率
	            "ap_tx_chan": "0", //遥控器-发射信道
	            "ap_tx_freq_khz": "0", //遥控器-发送频段
	            "ap_lfs_2g_band_chan_snr": "0",
	            "ap_lfs_2g_band_gain_a": "0",
	            "ap_lfs_2g_band_gain_b": "0",
	            "ap_lfs_5g_band_chan_snr": "0",
	            "ap_lfs_5g_band_gain_a": "0",
	            "ap_lfs_5g_band_gain_b": "0",
	            "ap_main_loc": "0",
	            "ap_sync_num": "0",
	            "dev_ldpc_err": "0", //接收机-交织块中解码错误的LDPC块个数所占的比例
	            "dev_ldpc_num": "0", //接收机-解码错误的帧个数比例
	            "dev_snr": "0", //接收机-SNR
	            "dev_gain_a": "0", //接收机-A路天线接收信号强度
	            "dev_gain_b": "0", //接收机-B路天线接收信号强度
	            "dev_tx_mcs": "0", //接收机-发射MCS
	            "dev_tx_power": "0", //接收机-发送功率
	            "dev_tx_chan": "0", //接收机-发射信道
	            "dev_tx_freq_khz": "0", //接收机-发送频段
	            "dev_lfs_2g_band_chan_snr": "0",
	            "dev_lfs_2g_band_gain_a": "0",
	            "dev_lfs_2g_band_gain_b": "0",
	            "dev_lfs_5g_band_chan_snr": "0",
	            "dev_lfs_5g_band_gain_a": "0",
	            "dev_lfs_5g_band_gain_b": "0",
	            "dev_sync_num": "0",
	            "acs_chan": 0,
	            "work_chan": 0,
	            "signal": 0 //根据遥控器-SNR计算出来的用于参考的信号质量百分比（遥控器SNR<=0:信号质量为0；遥控器SNR>=16:信号质量为100）
            }
```

- ##### 接收机信号质量
```
    /**
     * 接收机信号质量
     * 访问方式
     * LISTEN
     * 支持H12Pro/H16/H30/H20/G12/G20/G30/G16
     */
    val KeySignalQuality:KeyInfo<Int> = KeyInfo.Builder<Int>()
        .canListen(true)
```

- ##### H12接收机信号质量
```
    /**
     * H12接收机信号质量
     * 访问方式
     * GET
     * 仅支持H12
     */
    val KeyH12SignalQuality:KeyInfo<Int> = KeyInfo.Builder<Int>()
        .canGet(true)
```

- ##### H12接收机选项设置
```
    /**
     * 接收机选项设置
     * 访问方式
     * SET,GET
     * 仅支持H12
     */
    val KeyReceiverOptions: KeyInfo<ReceiverOptions> = KeyInfo.Builder<ReceiverOptions>()
        .canSet(true)
        .canGet(true)
```

- ##### H16接收机串口0波特率
```
    /**
     * H16接收机串口0波特率
     * 访问方式
     * SET,GET
     * 仅支持H16
     */
    val KeyH16Uart0BaudRate:KeyInfo<UartBaudRate> = KeyInfo.Builder<UartBaudRate>()
        .canSet(true)
        .canGet(true)
```

- ##### H16接收机串口1波特率
```
    /**
     * H16接收机串口1波特率
     * 访问方式
     * SET,GET
     * 仅支持H16
     */
    val KeyH16Uart1BaudRate:KeyInfo<UartBaudRate> = KeyInfo.Builder<UartBaudRate>()
        .canSet(true)
        .canGet(true)
```

- ##### H16接收机信号质量
从1.1.0版本起，推荐使用KeySignalQuality
```
    /**
     * H16接收机信号质量
     * 访问方式
     * LISTEN
     * 仅支持H16
     */
    val KeyH16SignalQuality:KeyInfo<Int> = KeyInfo.Builder<Int>()
        .canListen(true)
```

- ##### H16接收机信号质量(原始数据)
```
    /**
     * H16接收机信号质量(原始数据)
     * 访问方式
     * LISTEN
     * 仅支持H16
     */
    val KeyH16RawSignalQuality:KeyInfo<String> = KeyInfo.Builder<String>()
        .canListen(true)
```

- ##### H30接收机信号质量
从1.1.0版本起，推荐使用KeySignalQuality
```
    /**
     * H30接收机信号质量
     * 访问方式
     * LISTEN
     * 仅支持H30
     */
    val KeyH30SignalQuality:KeyInfo<Int> = KeyInfo.Builder<Int>()
        .canListen(true)
```

- ##### H30接收机串口波特率
```
    /**
     * H30接收机串口波特率
     * 访问方式
     * SET,GET
     * 仅支持H30
     */
    val KeyH30UartBaudRate:KeyInfo<H30UartBaudRate> = KeyInfo.Builder<H30UartBaudRate>()
        .canSet(true)
        .canGet(true)
```

- ##### H20接收机串口0波特率
```
    /**
     * H20接收机串口0波特率
     * 访问方式
     * SET,GET
     * 仅支持H20
     */
    val KeyH20Uart0BaudRate:KeyInfo<H20UartBaudRate> = KeyInfo.Builder<H20UartBaudRate>()
        .canSet(true)
        .canGet(true)
```

- ##### H20接收机串口1波特率
```
    /**
     * H20接收机串口1波特率
     * 访问方式
     * SET,GET
     * 仅支持H20
     */
    val KeyH20Uart1BaudRate:KeyInfo<H20UartBaudRate> = KeyInfo.Builder<H20UartBaudRate>()
        .canSet(true)
        .canGet(true)
```

- ##### H20接收机串口1波特率
```
    /**
     * H20带宽设置
     * 访问方式
     * SET,GET
     * 仅支持H20
     * Bandwidth.ul：上行带宽
     * Bandwidth.dl：下行带宽
     */
    val KeyH20Bandwidth:KeyInfo<Bandwidth> = KeyInfo.Builder<Bandwidth>()
        .canSet(true)
        .canGet(true)
```

- ##### 遥控器无线模块版本
```
    /**
     * 遥控器无线模块版本
     * 访问方式
     * GET
     * 支持G12/G20/G30/G16/H30/H20
     */
    val KeyRCVersion:KeyInfo<String> = KeyInfo.Builder<String>()
        .canGet(true)
```

- ##### 天空端无线模块版本
```
    /**
     * 天空端无线模块版本
     * 访问方式
     * GET
     * 支持G12/G20/G30/G16/H30/H20
     */
    val KeySkyVersion:KeyInfo<String> = KeyInfo.Builder<String>()
        .canGet(true)
```

- ##### 天空端MCU版本
```
    /**
     * 天空端MCU版本
     * 访问方式
     * GET
     * 支持H30
     */
    val KeySkyMCUVersion:KeyInfo<String> = KeyInfo.Builder<String>()
        .canGet(true)
```

- ##### 地面端重传次数(G系列)
```
    /**
     * 地面端设置重传次数,默认6次，重启后失效(恢复默认6次)
     * 设置范围:0-500  0:表示重传到对,保证了链路的可靠性
     * 访问方式
     * SET
     * 支持G12/G20/G30/G16
     */
    val KeyRCSetReTxCount:KeyInfo<Int> = KeyInfo.Builder<Int>()
        .canSet(true)
```


- ##### 天空端重传次数(G系列)
```
    /**
     * 天空端设置重传次数,默认6次，重启后失效(恢复默认6次)
     * 设置范围:0-500  0:表示重传到对,保证了链路的可靠性
     * 访问方式
     * SET
     * 支持G12/G20/G30/G16
     */
    val KeySkySetReTxCount:KeyInfo<Int> = KeyInfo.Builder<Int>()
        .canSet(true)
```

- ##### 自适应MCS(G系列)
```
    /**
     * 注意* 该接口不要和KeySkySetAutoMCSDuration接口混合用
     * 自适应MCS
     * 开启后可提提升上行速度,重启后失效
     * 适用于上传文件,上传前开启,上传完成关闭
     * 访问方式
     * SET
     * 支持G12/G20/G30/G16
     */
    val KeySetAutoMCS:KeyInfo<Boolean> = KeyInfo.Builder<Boolean>()
        .canSet(true)
```

- ##### 持续时间内自适应MCS(G系列)
```
    /**
     * 注意* 该接口不要和KeySetAutoMCS接口混合用
     * 持续时间内自适应MCS
     * 开启后可提提升上行速度,持续时间内有效
     * 适用于上传文件,上传前开启,上传完成关闭
     * 访问方式
     * SET
     * 支持G12/G20/G30/G16
     * 使用说明：
     * 当设置持续时间为6s,遥控器将进入自适应MCS持续6s，如果在第4s时，再次设置持续时间为6s。接收机将重新计时。
     * 文件上传过程中可定时发送，文件上传完成后停止发送。该接口可防止程序不小心崩溃，遥控器还处于自适应MCS模式的情况。
     */
    val KeySkySetAutoMCSDuration:KeyInfo<SetAutoAndDuration> = KeyInfo.Builder<SetAutoAndDuration>()
        .canSet(true)
        
```

- ##### MAC地址(G系列)
```
    /**
     * MAC地址
     * 访问方式
     * GET
     * 支持G12/G20/G30/G16
     */
    val KeyMAC:KeyInfo<String> = KeyInfo.Builder<String>()
        .canGet(true)   
```

- ##### 射频开关(G系列)
```
    /**
     * 设置射频开关
     * 访问方式
     * SET,GET
     * 支持G12/G20/G30/G16
     */
    val KeyRCRFEnable:KeyInfo<Boolean> = KeyInfo.Builder<Boolean>()
        .canSet(true)
        .canGet(true)
```

- ##### 设置MAC的对频方式(G系列)
```
    /**
     * 设置MAC的对频方式
     * 访问方式
     * SET
     * 支持G12/G20/G30/G16
     */
    val KeyRequestPairingAtSetMac:KeyInfo<String> = KeyInfo.Builder<String>()
        .canSet(true)
```

- ##### 上传模式(G系列)
```
    /**
     * G系列上传/下载模式
     * 访问方式
     * GET/SET
     * 支持G12/G20/G30/G16
     */
    val KeyGLinkSpeedMode:KeyInfo<GLinkSpeedMode> = KeyInfo.Builder<GLinkSpeedMode>()
        .canSet(true)
        .canGet(true)
        
    //打开上传模式
    KeyManager.set(AirLinkKey.KeyGLinkSpeedMode,GLinkSpeedMode.UPLOAD){
        if(it == null){
            //成功
        }else{
            //失败
        }
    }
    //关闭上传模式
    KeyManager.set(AirLinkKey.KeyGLinkSpeedMode,GLinkSpeedMode.NORMAL){
        if(it == null){
            //成功
        }else{
            //失败
        }
    }
```

# PayloadManager
云卓相关配件通讯接口
```
//C10相机控制
val c10 = PayloadManager.getTCPPayload(PayloadType.C10, "192.168.144.108", 5000) as C10?
//内部已经实现重连机制，无需再实现
if (c10 != null) {
    c10.setCommListener(object : CommListener {
        override fun onConnectSuccess() {
             log("C10连接成功")
        }

        override fun onConnectFail(e: SkyException) {

        }

        override fun onDisconnect() {
            log("C10断开连接")
        }

        override fun onReadData(bytes: ByteArray) {

        }
    })
    
    //连接C10相机
    PayloadManager.connectPayload(c10)
}

//控制C10一键回中
c10.akey(AKey.MID)

//断开C10相机连接
PayloadManager.disconnectPayload(c10)
```

### 三体相机(串口版)控制
```
//获取三体相机(串口版)
//获取实例后需要调用连接方法才能控制
val threeBodyCamera = PayloadManager.getSerialPortPayload(PayloadType.THREE_BODY_CAMERA, "/dev/ttyHS0", 4000000) as ThreeBodyCamera?
 
//拍照
threeBodyCamera?.snapshot()

//开始录像
threeBodyCamera?.toggleReCord(true)

//结束录像
threeBodyCamera?.toggleReCord(false)

//切换LED
threeBodyCamera?.toggleLED()

//同步时间（要在收到帧数据后再调用才有效）
threeBodyCamera?.setTime(System.currentTimeMillis())
```

### 双轴云台相机(串口版)控制
```
 val dualAxisGimbalCamera = PayloadManager.getSerialPortPayload(PayloadType.DUAL_AXIS_GIMBAL_CAMERA,"/dev/ttyHS0",4000000) as DualAxisGimbalCamera

//一键控制
//向下
dualAxisGimbalCamera?.akey(AKey.DOWN)
//回中
dualAxisGimbalCamera?.akey(AKey.MID)
//向上
dualAxisGimbalCamera?.akey(AKey.TOP)

//控制俯仰
//向上
dualAxisGimbalCamera?.controlPitch(true)
//向下
dualAxisGimbalCamera?.controlPitch(false)

//同步时间（要在收到帧数据后再调用才有效）
dualAxisGimbalCamera?.setTime(System.currentTimeMillis())
```

### 旧款三体相机(网口版)控制
```
//获取三体相机(网口版)
//获取实例后需要调用连接方法才能控制
val threeBodyCamera2 = PayloadManager.getTCPPayload(PayloadType.THREE_BODY_CAMERA2, "192.168.144.108", 5001) as ThreeBodyCamera2?

//切换LED
threeBodyCamera2?.toggleLED()
threeBodyCamera2?.toggleLED(boolean)

```

### C10云台相机控制
```
//获取C10云台相机
//获取实例后需要调用连接方法才能控制
val c10 = PayloadManager.getTCPPayload(PayloadType.C10, "192.168.144.108", 5000) as C10?

//一键控制
//向下
c10?.akey(AKey.DOWN)
//回中
c10?.akey(AKey.MID)
//向上
c10?.akey(AKey.TOP)
        
//拍照
c10?.takePicture()

//开始录像
c10?.startRecordVideo()
        
//停止录像
c10?.stopRecordVideo()

//速度控制偏航，-63.5 ~ +63.5，单位°/s 负数向左，正数向右
c10?.controlYaw(1f)
        
//速度控制俯仰，-63.5 ~ +63.5，单位°/s 负数向下，正数向上
c10?.controlPitch(-1f)

//控制偏航角度, -90.00 ~ +90.00，单位°
c10?.gotoYaw(30f)

//控制俯仰角度，-90.00 ~ +90.00，单位°
c10?.gotoPitch(-90f)
```

### C20相机控制
```
//获取C20相机
//获取实例后需要调用连接方法才能控制
val c20Camera = PayloadManager.getTCPPayload(PayloadType.C20_CAMERA, "192.168.144.108", 8100) as C20Camera?

//拍照
c20Camera?.takePicture()
//开始录像
c20Camera?.startRecordVideo()
//停止录像
c20Camera?.stopRecordVideo()

//变倍变焦
//开始变倍
c20Camera?.startZoomIn()
c20Camera?.startZoomOut()
//开始变焦
c20Camera?.startFucusFar()
c20Camera?.startFucusNear()
//停止变倍变焦
c20Camera?.stopZoomOrFucus()

//日夜模式
//设置
c20Camera?.setDayNightMode()
//查询
c20Camera?.getDayNightMode()

//翻转
//设置
c20Camera?.setFlip()
//查询
c20Camera?.setFlip()

更多接口详情查看
com.skydroid.rcsdk.common.payload.C20Camera

```

### C20云台控制
```
//获取C20云台
//获取实例后需要调用连接方法才能控制
val c20Gimbal = PayloadManager.getTCPPayload(PayloadType.C20_GIMBAL, "192.168.144.108", 5000) as C20Gimbal?

//一键控制
//向下
c20Gimbal?.akey(AKey.DOWN)
//回中
c20Gimbal?.akey(AKey.MID)
//向上
c20Gimbal?.akey(AKey.TOP)

//速度控制偏航，-63.5 ~ +63.5，单位°/s 负数向左，正数向右
c20Gimbal?.controlYaw(1f)
        
//速度控制俯仰，-63.5 ~ +63.5，单位°/s 负数向下，正数向上
c20Gimbal?.controlPitch(-1f)

//控制偏航角度, -90.00 ~ +90.00，单位°
c20Gimbal?.gotoYaw(30f)

//控制俯仰角度，-90.00 ~ +90.00，单位°
c20Gimbal?.gotoPitch(-90f)

//切换LED
c20Gimbal?.toggleLED()
c20Gimbal?.toggleLED(boolean)

```

### C10Pro相机/新款三体相机控制（0.2.7以下固件）
```
//获取C10Pro相机
//获取实例后需要调用连接方法才能控制
val c10ProCamera = PayloadManager.getUDPPayload(PayloadType.C10PRO_CAMERA,12580,"192.168.144.108",12580) as C10ProCamera?

//拍照
c10ProCamera?.takePicture()
命令示例:"AT+AZ -p2\r\n"

//开始录像
c10ProCamera?.startRecordVideo()
命令示例:"AT+AZ -p0\r\n"

//停止录像
c10ProCamera?.stopRecordVideo()
命令示例:"AT+AZ -p1\r\n"

//同步时间
c10ProCamera?.setTime()

//获取版本号
c10ProCamera?.getVersion()
命令示例:"AT+INFO\r\n"

//设置LED（针对新款三体相机有效）
c10ProCamera?.setLED()
命令示例:开 "AT+LED -e1\r\n";关 "AT+LED -e0\r\n"

更多接口详情查看
com.skydroid.rcsdk.common.payload.C10ProCamera

```

### C10Pro云台控制（0.2.7以下固件）
```
//获取C10Pro云台
//获取实例后需要调用连接方法才能控制
val c10ProGimbal = PayloadManager.getUDPPayload(PayloadType.C10PRO_GIMBAL, 5000, "192.168.144.108", 5000) as C10ProGimbal?

//一键控制
//向下
c10ProGimbal?.akey(AKey.DOWN)
//回中
c10ProGimbal?.akey(AKey.MID)
//向上
c10ProGimbal?.akey(AKey.TOP)

//速度控制偏航，-63.5 ~ +63.5，单位°/s 负数向左，正数向右
c10ProGimbal?.controlYaw(3f)
命令示例:"#TPUG2wGSY1E75"
c10ProGimbal?.controlYaw(-3f)
命令示例:"#TPUG2wGSYE276"

//速度控制俯仰，-63.5 ~ +63.5，单位°/s 负数向下，正数向上
c10ProGimbal?.controlPitch(3f)
命令示例:"#TPUG2wGSP1E6C"
c10ProGimbal?.controlPitch(-3f)
命令示例:"#TPUG2wGSPE26D"

//控制偏航角度, -90.00 ~ +90.00，单位°
c10ProGimbal?.gotoYaw(30f)

//控制俯仰角度，-90.00 ~ +90.00，单位°
c10ProGimbal?.gotoPitch(-90f)

```

### C10Pro云台相机/新款三体相机控制（0.2.7及以上固件）
```
//获取C10Pro云台相机
//获取实例后需要调用连接方法才能控制
c10p = PayloadManager.getUDPPayload(PayloadType.C10PRO,5000,"192.168.144.108",5000) as C10Pro?

//一键控制
//向下
c10p?.akey(AKey.DOWN)
//回中
c10p?.akey(AKey.MID)
//向上
c10p?.akey(AKey.TOP)

//速度控制偏航，-63.5 ~ +63.5，单位°/s 负数向左，正数向右
c10p?.controlYaw(3f)
命令示例:"#TPUG2wGSY1E75"
c10p?.controlYaw(-3f)
命令示例:"#TPUG2wGSYE276"
  
//速度控制俯仰，-63.5 ~ +63.5，单位°/s 负数向下，正数向上
c10p?.controlPitch(3f)
命令示例:"#TPUG2wGSP1E6C"
c10p?.controlPitch(-3f)
命令示例:"#TPUG2wGSPE26D"

//控制偏航角度, -90.00 ~ +90.00，单位°
c10p?.gotoYaw(30f)

//控制俯仰角度，-90.00 ~ +90.00，单位°
c10p?.gotoPitch(-90f)

//拍照
c10p?.takePicture(callBack:CompletionCallback?)
命令示例:"#TPUD2wCAP013E"

//开始录像
c10p?.startRecordVideo(callBack:CompletionCallback?)
命令示例:"#TPUD2wREC0144"

//结束录像
c10p?.stopRecordVideo(callBack:CompletionCallback?)
命令示例:"#TPUD2wREC0043"

//获取录像状态
c10p?.getRecordVideoState(callBack: CompletionCallbackWith<Boolean>)
命令示例:"#TPUD2rREC003E"

//同步时间（需要在出图后设置才有效）
c10p?.setTime(time:Long,callBack:CompletionCallback?)

//设置osd显示/关闭
c10p?.setOSD(boolean: Boolean,callBack: CompletionCallback?)

//获取相机版本号
c10p?.getCameraVersion(callBack: CompletionCallbackWith<String>)
命令示例:"#TPUD2rVER0051"

//设置/读取视频输出参数
c10p?.setVideoConfig()
c10p?.getVideoConfig()

//LED开关（针对新款三体相机有效）
c10p?.setLed(onOrOff:Boolean,callBack: CompletionCallback?)
```

### C12云台相机控制
```
//获取C12云台相机
//获取实例后需要调用连接方法才能控制
c12 = PayloadManager.getUDPPayload(PayloadType.C12,5000,"192.168.144.108",5000) as C12?

//一键控制
//向下
c12?.akey(AKey.DOWN)
//回中
c12?.akey(AKey.MID)
//向上
c12?.akey(AKey.TOP)

//速度控制偏航，-63.5 ~ +63.5，单位°/s 负数向左，正数向右
c12?.controlYaw(1f)
        
//速度控制俯仰，-63.5 ~ +63.5，单位°/s 负数向下，正数向上
c12?.controlPitch(-1f)

//控制偏航角度, -90.00 ~ +90.00，单位°
c12?.gotoYaw(30f)

//控制俯仰角度，-90.00 ~ +90.00，单位°
c12?.gotoPitch(-90f)

//设置倍率 0-4  0:原图,1-4:变倍
c12?.setZoomRatios(value:Int,callBack: CompletionCallback?)

//设置伪彩
//    WHITE_HOT,白热
//    SEPIA,辉金
//    IRONBOW,铁红
//    RAINBOW,彩虹
//    NIGHT,微光
//    AURORA,极光
//    RED_HOT,红热
//    JUNGLE,从林
//    MEDICAL,医疗
//    BLACK_HOT,黑热
//    GLORY_HOT;金红
c12?.setThermalPalette(palette: ThermalPalette, callBack: CompletionCallback?)

//拍照
c12?.takePicture(callBack:CompletionCallback?)

//开始录像
c12?.startRecordVideo(callBack:CompletionCallback?)

//结束录像
c12?.stopRecordVideo(callBack:CompletionCallback?)

//获取录像状态
c12?.getRecordVideoState(callBack: CompletionCallbackWith<Boolean>)

//同步时间（需要在出图后设置才有效）
c12?.setTime(time:Long,callBack:CompletionCallback?)

//获取相机版本号
c12?.getCameraVersion(callBack: CompletionCallbackWith<String>)

```

### 通用云台相机控制
```
通用的相机/云台，包含了所有的控制协议，需要开发者自己判断是否支持控制
支持：C10，C10Pro，C11，C12，C13，电子云台(C01、C01Pro)，三体网口相机，单双轴网口相机，C14
连接方式：根据相机类型自行判断

C10:
    TCP: 192.168.144.108:5000
    
新三体网口相机,C10Pro,C11,C12,C13,C01,C01Pro,C14
    UDP: 192.168.144.108:5000

以下使用C12进行测试
//获取实例后需要调用连接方法才能控制
val c12:CommonPayload? = PayloadManager.getUDPPayload(PayloadType.COMMON,5000,"192.168.144.108",5000) as CommonPayload?

//连接C12相机
PayloadManager.connectPayload(c12)
//断开C12相机连接(只有不再使用的情况下再断开)
PayloadManager.disconnectPayload(c12)
    
     /**
     * 获取当前变倍系数
     * C12,C13 范围：0-67,
     * C11 范围 0-90
     * C14 范围 0-150, 0<=变倍系数<70为短焦镜头, 变倍系数>=70为长焦镜头
     * 支持:C11,C12,C13,C14
     */
    fun getZoomRatios(callBack: CompletionCallbackWith<Int>)

    /**
     * 变倍 +
     * 支持:C11,C12,C13,C14
     */
    fun addZoomRatios(callBack: CompletionCallback?)
    
    /**
     * 变倍 -
     * 支持:C11,C12,C13,C14
     */
    fun subtractZoomRatios(callBack: CompletionCallback?)
    
    /**
     * 变倍 长短焦镜头切换
     * 注* 切换成功后，对应的变倍系数也会跟着发生变化
     * 支持:C14
     */
    fun setZoomForLens(value:ZoomForLens,callBack:CompletionCallback?)
    
     /**
     * 根据变倍系数获取当前 长短焦镜头
     * 支持:C14
     */
    fun getZoomForLens(callBack: CompletionCallbackWith<ZoomForLens>)
    
    /**
     * 设置伪彩
     * WHITE_HOT,白热
     * SEPIA,辉金
     * IRONBOW,铁红
     * RAINBOW,彩虹
     * NIGHT,微光
     * AURORA,极光
     * RED_HOT,红热
     * JUNGLE,从林
     * MEDICAL,医疗
     * BLACK_HOT,黑热
     * GLORY_HOT;金红
     * 支持:C12,C13,C14
     */
    fun setThermalPalette(palette: ThermalPalette, callBack: CompletionCallback?)

    /**
     * 获取伪彩
     * 支持:C12,C13,C14
     */
    fun getThermalPalette(callBack: CompletionCallbackWith<ThermalPalette>)
    
    /**
     * 拍照
     * 支持:C10,C10Pro,C12,C13,C11,C14
     */
    fun takePicture(callBack: CompletionCallback?)
    
    /**
     * 开始录像
     * 支持:C10,C10Pro,C12,C13,C11,C14
     */
    fun startRecordVideo(callBack: CompletionCallback?)
    
    /**
     * 结束录像
     * 支持:C10,C10Pro,C12,C13,C11,C14
     */
    fun stopRecordVideo(callBack: CompletionCallback?)
    
    /**
     * 获取录像状态
     * 支持:C10,C10Pro,C12,C13,C11,C14
     */
    fun getRecordVideoState(callBack: CompletionCallbackWith<Boolean>)
    
    /**
     * 同步时间
     * 注* 需要在出图后设置才有效
     * 支持:C10,C10Pro,新三体,C12,C13,C11,C14,C01,C01P
     */
    fun setTime(time:Long,callBack: CompletionCallback?)
    
    /**
     * 配置视频流参数（翻转），设置前先获取一下，再修改其中的值，其他值原封不动
     * 注* 不建议修改除了翻转以外的其他值
     * 支持:C12,C13,C11,C14,C01,C01P
     */
    fun setVideoConfig(config: VideoConfig,callBack: CompletionCallback?)
    
    /**
     * 获取视频流设置的参数（翻转）
     * 支持:C12,C13,C11,C14,C01,C01P
     */
    fun getVideoConfig(callBack: CompletionCallbackWith<VideoConfig>)
    
    /**
     * 获取SD卡容量
     * 剩余容量与总容量都为0：表示内存卡未插入
     * 支持:C12,C13,C11,C14
     */
    fun getSDCardCapacity(callBack: CompletionCallbackWith<SDCardCapacity>)
    
     /**
     * 一键操作
     * AKey.TOP 向上
     * AKey.MID 回中
     * AKey.DOWN 向下
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14,C01,C01P
     */
    fun akey(a:AKey)
    
    /**
     * 速度模式控制偏航
     * 参数:-63.5 ~ +63.5，单位°/s 负数向左，正数向右
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     */
    fun controlYaw(speed:Float)
    
     /**
     * 速度模式控制俯仰
     * 参数:-63.5 ~ +63.5，单位°/s 负数向下，正数向上
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14,C01,C01P
     */
    fun controlPitch(speed:Float)
    
    /**
     * 控制偏航俯仰
     * 参数:-63.5 ~ +63.5，单位°/s 负数向下，正数向上
     * 云台固件版本0.5开始支持
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     */
    fun controlYawPitch(yawSpeed:Float,pitchSpeed:Float)
    
    /**
     * 角度控制
     * 偏航
     * 小数点后2位有效
     * （-90.00,+90.00）
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     */
    fun gotoYaw(angle:Float)
    
    /**
     * 角度控制
     * 俯仰
     * 小数点后2位有效
     * （-90.00,+90.00）
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     */
    fun gotoPitch(angle:Float)
    
    /**
     * 角度控制(不建议控制)
     * 横滚
     * 小数点后2位有效
     * 角度（-90.00,+90.00）
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     */
    fun gotoRoll(angle:Float)
    
     /**
     * 角度控制
     * 偏航俯仰
     * 小数点后2位有效
     * 角度（-90.00,90.00）、（-90.00,+90.00）
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     */
    fun gotoYawPitch(yawAngle:Float,pitchAngle:Float)
      
    /**
     * 设置云台装配模式
     * GimbalAssemblyMode.HOISTING 吊装
     * GimbalAssemblyMode.INVERSION 倒装
     * 支持:C10,C10Pro,C12,C13,C11,C14
     */
    fun setGimbalAssemblyMode(mode: GimbalAssemblyMode)
    
    /**
     * 设置持续云台推送姿态信息开关
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     * 设置推送云台姿态速率（0-100）
     * 0表示关闭
     * 1表示1hz
     * 设置完成以后，通过GimbalAttitudListener监听云台姿态
     * 注意，保证云台连接后再设置（最好是出图后再设置，多设置几次也可以）
     */
    fun setPushAttitudeEnable(rate: Int)
    
    /**
     * 监听云台推送姿态信息
     * 需要调用setPushAttitudeEnable后才有姿态数据
     */
    fun addGimbalAttitudListener(listener: GimbalAttitudListener)
    
    /**
     * 取消监听云台推送姿态信息
     */
    fun removeGimbalAttitudListener(listener: GimbalAttitudListener)
    
     /**
     * 设置OSD开关
     * 支持:C10Pro(0.2.7),新三体(0.2.7),C12,C13,C11,C14,C01,C01P
     */
    fun setOSD(boolean: Boolean,callBack: CompletionCallback?)
    
     /**
     * 获取OSD开关
     * 支持:C10Pro(0.2.7),新三体(0.2.7),C12,C13,C11,C14,C01,C01P
     */
    fun getOSD(callBack: CompletionCallbackWith<Boolean>)
    
     /**
     * 测距
     * 支持：C13,C14
     */
    fun getRanging(callBack: CompletionCallbackWith<Int>)
    
    /**
     * 设置LED
     * 支持：新三体相机
     */
    fun setLed(onOrOff:Boolean,callBack: CompletionCallback?)
    
    /**
     * 获取LED
     * 支持：新三体相机
     */
    fun getLed(callBack: CompletionCallbackWith<Boolean>)
     
    /**
     * 获取当前相机软件固件型号
     * 支持:C10Pro(0.2.7),新三体(0.2.7),C12,C13,C11,C14,C01,C01P
     */
    fun getCameraModel(callBack: CompletionCallbackWith<String>)
    
    /**
     * 获取相机固件版本号
     * 支持:C12,C13,C14,C01,C01P
     */
    fun getCameraVersion(callBack: CompletionCallbackWith<String>)
    
    /**
     * 获取机械云台型号
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     */
    fun getModel(callBack: CompletionCallbackWith<String>)
    
     /**
     * 获取机械云台版本
     * 支持:C10,C20,C10Pro,C12,C13,C11,C14
     */
    fun getGimbalVersion(callBack: CompletionCallbackWith<String>)
    
    
     /**
     * 自定义遥控器通道云台控制模式为 匀速或变速模式
     * GimbalMoveMode.UNIFORM_SPEED 匀速模式 始终以固定的速度控制云台
     *          通过setRCButtonControlUniformSpeed设置，默认10°/s
     * GimbalMoveMode.ACCELERATE_SPEED 变速模式 根据感量大小控制云台速度
     *          通过setRCButtonControlMinSpeed设置变速范围最小值，默认0.5°/s
     *          通过setRCButtonControlMaxSpeed设置变速范围最大值，默认67.5°/s
     *
     * 注* 自定义遥控器通道控制云台请先通过ButtonHelper(内部是通过调用速度模式控制云台)启用后才有效
     */
    fun setRCButtonControlSpeedMode(mode: GimbalMoveMode) 
    
    /**
     * 自定义遥控器通道云台控制变速模式变速范围最小值
     *
     * 注* 自定义遥控器通道控制云台请先通过ButtonHelper(内部是通过调用速度模式控制云台)启用后才有效
     */
    fun setRCButtonControlMinSpeed(speed: Float)
    
     /**
     * 自定义遥控器通道云台控制变速模式变速范围最大值
     *
     * 注* 自定义遥控器通道控制云台请先通过ButtonHelper(内部是通过调用速度模式控制云台)启用后才有效
     */
    fun setRCButtonControlMaxSpeed(speed: Float)
    
    /**
     * 自定义遥控器通道云台控制匀速模式速度值
     *
     * 注* 自定义遥控器通道控制云台请先通过ButtonHelper(内部是通过调用速度模式控制云台)启用后才有效
     */
    fun setRCButtonControlUniformSpeed(speed: Float)
    
     /**
     * 设置遥控器控制云台的速度缩放系数(默认1.0)
     * 使用场景:
     *      当变倍视野大的时候，可以尽量缩小该系数，这样就不会导致云台转动过快
     *
     * 注* 自定义遥控器通道控制云台请先通过ButtonHelper(内部是通过调用速度模式控制云台)启用后才有效
     */
    fun setRCButtonControlSpeedScale(scale: Float)
    
```

# 工具类
#### 自定义遥控器按钮(遥控器通道自定义/波轮控制/摇杆控制)
com.skydroid.rcsdk.common.button.ButtonHelper
```
详细使用方法参考
CustomRCButtonsActivity
```

#### RCSDKUitls
com.skydroid.rcsdk.utils.RCSDKUitls
```
getDeviceType 获取遥控器型号
getVersion 获取SDK版本号
```
