# 云卓 G20 遥控器设备参数

> 抓取时间：2026-07-30
> 设备：云卓 G20
> ADB 序列号：`36163552`

本记录用于 App 自动选择遥控器输入源。这里只验证 Android 设备、云卓系统服务和 RCSDK 摇杆入口，不发送机器狗移动命令。

## 设备标志

| 参数 | 值 |
| --- | --- |
| `ro.product.manufacturer` | `QUALCOMM` |
| `ro.product.brand` | `qti` |
| `ro.product.model` | `Bengal for arm64` |
| `ro.product.name` | `bengal_515` |
| `ro.product.device` | `bengal_515` |
| `ro.product.board` | `bengal` |
| `ro.hardware` | `qcom` |
| `ro.board.platform` | `bengal` |
| `ro.boot.ZBBoard` | `0` |

仓库内 `rcsdk-v1.9.2.aar` 的设备识别逻辑在 `Build.MODEL=Bengal for arm64` 时继续读取 `ro.boot.ZBBoard`：

| `ro.boot.ZBBoard` | RCSDK 设备类型 |
| --- | --- |
| `0` | `DeviceType.G20` |
| `1` | `DeviceType.G12` |
| `2` | `DeviceType.G16` |
| `99` | `DeviceType.G30` |

当前 G20 的主要运行时判据使用 `RCSDKUtils.getDeviceType() == DeviceType.G20`。`QUALCOMM`、`qti`、`bengal_515` 都是平台通用标志，不能单独作为 G20 的充分条件。

## Android 与构建信息

| 参数 | 值 |
| --- | --- |
| Android 版本 | `13` |
| API Level | `33` |
| Build ID | `TKQ1.230110.001` |
| Build 类型 | `user` |
| Build 标签 | `test-keys` |
| 安全补丁 | `2023-02-05` |
| 系统构建时间 | `2026-03-21 00:41:35 CST` |
| 系统语言 | `zh-CN` |
| Build fingerprint | `qti/bengal_515/bengal_515:13/TKQ1.230110.001/eng.admin.20260321.004555:user/test-keys` |
| Vendor fingerprint | `qti/bengal_515/bengal_515:13/TKQ1.230110.001/admin03210318:user/test-keys` |

## 屏幕参数

| 参数 | 值 |
| --- | --- |
| 物理分辨率 | `1200 x 1920` |
| 物理密度 | `480 dpi` |
| 当前覆盖密度 | `408 dpi` |
| 横屏实际显示 | `1920 x 1200` |
| 横屏 App 可用区域 | `1920 x 1078` |
| 刷新率 | `60.000004 fps` |
| 触摸类型 | 内置触摸屏 |

## CPU、内存与电池

| 参数 | 值 |
| --- | --- |
| ABI | `arm64-v8a, armeabi-v7a, armeabi` |
| 总内存 | `3741312 kB`，约 3.57 GiB |
| 抓取时可用内存 | `2413640 kB`，约 2.30 GiB |
| 电池技术 | `Li-ion` |
| 抓取时电量 | `90%` |
| 抓取时供电 | USB 供电并充电 |
| 抓取时电压 | `4064 mV` |
| 抓取时温度 | `29.0°C` |

电量、可用内存和温度属于抓取时状态，不作为设备识别条件。

## 云卓系统服务

| 包名 | 版本 | 安装位置 | 抓取时状态 |
| --- | --- | --- | --- |
| `com.skydroid.rcservice` | `5.4.v2`（versionCode 46） | `/system/app/rcserivce` | 进程运行中 |
| `com.skydroid.rc_daemon` | `1.0`（versionCode 1） | `/system/app/rcdaemon` | 进程运行中 |
| `com.skydroid.devicetool` | `3.1.10`（versionCode 92） | `/data/app/...` | 已安装 |

系统包只作为辅助诊断证据；正式输入源选择以 RCSDK 返回的 `DeviceType.G20` 为主，避免仅凭包名误判其他云卓机型。

## RCSDK 与摇杆参数

| 参数 | 值 |
| --- | --- |
| AAR | `rcsdk-v1.9.2.aar` |
| SHA-256 | `e5977ba3292246bcee026defdb8563a18adc9d5573fdfb20a6880ee469f0fde2` |
| G20 通道读取接口 | `RemoteControllerKey.KeyChannels` |
| 读取方式 | 主动 `GET` |
| 最小读取周期 | `100 ms` |
| 通道数量 | 最多 16 路 |
| RCSDK 串口 | `/dev/ttyHS2:115200` |
| 通道最小值 | `900` |
| 通道中位值 | `1500` |
| 通道最大值 | `2100` |

G20 没有低/中/高速实体切换键。G20 输入源不得从通道数据生成速度档请求，速度档只通过 App 屏幕上的低速、中速、高速选择器切换。

主 `app` 真机测试收到的首帧为：

```text
[1500, 1500, 1500, 1500, 2100, 900, 900, 900, 900, 900, 900, 900, 900, 1500, 900, 900]
```

前四路摇杆在未操作时都为 `1500`。CH5 等辅助通道可以保持在 `900` 或 `2100`，不能据此判断越界，也不能把 CH5 当成 G20 的速度档键。`firefighting_dog` 参考代码里的 `282/1002/1722` 来自另一台遥控器，不适用于当前 G20。

## 适配边界

1. 设备识别层调用 RCSDK 获取设备类型。
2. 输入源 factory 在 G20 上选择 RCSDK provider，在未匹配设备上回退到思翼 UniRC UDP provider。
3. G20 provider 只负责 SDK 生命周期、100ms 通道读取、超时和断连状态。
4. 通道值在统一输入层转换为 `MovementIntent`、`HeadControlIntent` 和原始通道调试快照。
5. Compose 和控制层不得直接调用 RCSDK，也不得直接拼装机器狗协议帧。
6. 断连、输入超时、页面后台或停止输入消费时，沿用现有统一零速度保护。

## 抓取命令

```bash
adb devices -l
adb shell getprop
adb shell wm size
adb shell wm density
adb shell dumpsys display
adb shell cat /proc/meminfo
adb shell dumpsys battery
adb shell pm list packages
adb shell dumpsys package com.skydroid.rcservice
adb shell dumpsys package com.skydroid.rc_daemon
adb shell dumpsys package com.skydroid.devicetool
adb shell ps -A
```
