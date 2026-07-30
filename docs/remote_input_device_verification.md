# 遥控输入设备实测记录

## 验证边界

本记录只覆盖 UniRC 遥控输入链路，不包含 ZMQ 和机器狗控制链路。ZMQ 连接、机器狗地址和 `legged_driver` 联调等到机器狗设备一起开启后再统一验证。

## 设备信息

| 项 | 结果 |
| --- | --- |
| ADB 设备 | `d` |
| 设备型号 | `Standard-10inch_A2` |
| Android 版本 | `13` |
| App 安装 | `adb install -r app/build/outputs/apk/debug/RIDReceiver_1.0.2_debug_202606231502.apk` 成功 |
| App 启动 | `com.helywin.leggedjoystick` 可启动，最新验证启动后 PID 为 `9471` |

## 串口节点

设备上存在以下串口节点：

| 节点 | 权限 | 归属 |
| --- | --- | --- |
| `/dev/ttyHS0` | `crwxrwxrwx` | `bluetooth:net_bt` |
| `/dev/ttyHS1` | `crwxrwxrwx` | `system:system` |
| `/dev/ttyHS2` | `crwxrwxrwx` | `bluetooth:bluetooth` |
| `/dev/ttyHS3` | `crwxrwxrwx` | `root:root` |

这些节点当前权限允许普通 App 进程打开，但 Android 标准 SDK 没有直接设置串口波特率的 API。正式 App 如果要内置串口输入源，需要额外确定串口初始化方式，或引入可靠的串口访问层。

## `/dev/ttyHS3` 验证结果

验证命令：

```bash
adb shell 'stty -F /dev/ttyHS3 115200 raw -echo -ctlecho 2>&1; for i in 1 2 3; do printf "\x55\x66\x01\x01\x00\x00\x00\x42\x02\xB5\xC0" > /dev/ttyHS3; sleep 0.05; done; timeout 3 dd if=/dev/ttyHS3 bs=42 count=1 2>/dev/null | xxd -p -c 42 || true'
```

收到通道帧：

```text
55 66 00 20 00 02 00 42
dc 05 dc 05 dc 05 dc 05 e8 03 dc 05 dc 05 dc 05
dc 05 dc 05 dc 05 1a 04 1a 04 1a 04 1a 04 1a 04
d4 5f
```

解析结果：

| 项 | 结果 |
| --- | --- |
| 帧长度 | `42` 字节 |
| `CMD_ID` | `0x42` |
| `Data_len` | `32` |
| CRC16 | 收到 `0x5FD4`，计算 `0x5FD4`，校验通过 |
| CH1-CH16 | `[1500, 1500, 1500, 1500, 1000, 1500, 1500, 1500, 1500, 1500, 1500, 1050, 1050, 1050, 1050, 1050]` |

结论：`/dev/ttyHS3` 可以通过 UniRC SDK 通道协议收取有效 `CMD_ID = 0x42` 通道帧。

## `/dev/ttyHS0` 验证结果

验证命令：

```bash
adb shell 'stty -F /dev/ttyHS0 115200 raw -echo -ctlecho 2>&1; for i in 1 2 3; do printf "\x55\x66\x01\x01\x00\x00\x00\x42\x02\xB5\xC0" > /dev/ttyHS0; sleep 0.05; done; timeout 3 dd if=/dev/ttyHS0 bs=42 count=1 2>/dev/null | xxd -p -c 42 || true'
```

3 秒内没有收到通道帧。

结论：当前设备状态下未确认 `/dev/ttyHS0` 可收 UniRC 通道帧。

## UDP 验证结果

设备上存在本机 UDP 服务监听 `19856`：

```text
UNCONN 0 0 *:19856 *:*
package:com.siyi.udpservice uid:10053
process: com.siyi.udpservice
```

系统包 `com.siyi.udpservice` 的行为边界如下：

| 项 | 结果 |
| --- | --- |
| UDP 监听 | 使用 `DatagramChannel` 绑定本机 `19856` |
| 串口节点 | 使用 `/dev/ttyHS3` |
| 数据方向 | UDP 收到的数据写入串口；串口读到的数据按最近 UDP 客户端地址发回 |
| 打开方式 | 通过 `com.siyi.udpservice.ISerialAidlInterface` Binder 事务打开串口桥 |

新 App 已验证可以绑定该服务并请求打开本机 UDP 串口桥：

```text
[SIYI UDP] 已绑定系统 UDP 桥服务
[SIYI UDP] 已请求打开本机 UDP 串口桥
[UniRC] UDP 输入源启动，本地端口=46581，远端=127.0.0.1:19856
[UniRC] 已发送通道订阅请求，频率=HZ_50
[UniRC] 已收到首帧通道数据，序列=57862，通道=[1500, 1500, 1500, 1500, 1000, 1500, 1500, 1500, 1500, 1500, 1500, 1050, 1050, 1050, 1050, 1050]
```

系统服务侧也能看到打开串口日志：

```text
UdpService: 打开串口：com.siyi.udpservice.SerialPort@...
serial_port: Opening serial port /dev/ttyHS3 with flags 0x2
```

当前已经从本机 UDP 桥收到有效 `CMD_ID = 0x42`、`Data_len = 32` 的通道帧，首帧通道值与 `/dev/ttyHS3` 串口实测值一致。App 已改为非阻塞 UDP 接收循环和串口流重组解析，会在链路空闲时持续按 1 秒间隔重发 `HZ_50` 订阅。

结论：本机 `127.0.0.1:19856` 已验证可以作为当前 App 的默认 UDP 输入入口，但它更像是 `/dev/ttyHS3` 串口桥，而不是独立于串口的第二路物理通道。因此“本 App 用 UDP、其他 App 直接读串口”仍可能竞争同一条 `/dev/ttyHS3` 数据流，不能据此判定 UDP 与串口独立。

## 不同 UDP 客户端端口并发验证

验证日期：2026-06-24。

验证方法：

1. 停止本 App，仅保留 `com.siyi.udpservice` 监听 `127.0.0.1:19856`。
2. 使用临时 `app_process` 测试程序在设备本机创建两个 UDP 客户端端口。
3. 分别验证 `41001`、`41002` 单独发送 `HZ_50` 订阅时可收到有效 `CMD_ID = 0x42` 通道帧。
4. 两个端口并发时都以 250ms 间隔重复发送 `HZ_50` 订阅并持续监听。
5. 再做一次只发一次订阅的切换对照：A 先订阅，500ms 后 B 订阅，两个端口都不再重复发送订阅。

重复订阅结果：

```text
A-baseline local_port=41001 valid_frames=125 invalid_frames=3 first_seq=43865 last_seq=43992
B-baseline local_port=41002 valid_frames=126 invalid_frames=3 first_seq=44007 last_seq=44135
A-parallel local_port=41001 valid_frames=34 invalid_frames=6 first_seq=44163 last_seq=44399
B-parallel local_port=41002 valid_frames=215 invalid_frames=6 first_seq=44151 last_seq=44411
```

只发一次订阅的切换结果：

```text
A-once local_port=41011 valid_frames=25 invalid_frames=0 first_seq=46953 last_seq=46977
B-once local_port=41012 valid_frames=249 invalid_frames=0 first_seq=46978 last_seq=47226
```

结论：两个不同 UDP 客户端端口都能单独订阅并收到通道帧；并发重复订阅时两个端口也都能收到部分帧。但 `com.siyi.udpservice` 更接近“最近 UDP 客户端地址”模型，不会把同一份通道流稳定广播给多个端口。A 先订阅、B 后订阅的对照里，A 只收到切换前约 0.5 秒数据，B 收到后续大部分数据。因此不能把“不同客户端端口订阅”作为多个 App 稳定共用摇杆的方案；多 App 共用仍应改为单采集者分发。

## UDP 与直接串口读取并发验证

验证方法：

1. 停止新 App 和 `com.siyi.udpservice`。
2. 只通过 `/dev/ttyHS3` 发送 `HZ_50` 订阅并直接读取串口，建立串口基线。
3. 启动新 App，让新 App 通过本机 UDP 桥接收 UniRC 通道帧。
4. 在新 App 保持 UDP 输入的同时，用 shell 模拟另一个 App 直接读取 `/dev/ttyHS3`，不再额外发送串口订阅。

基线结果：

```text
bytes=1600 valid_frames=37
first_seq=20058
last_seq=20094
```

并发结果：

```text
bytes=1600 valid_frames=6
first_seq=39687
last_seq=39719
```

并发时新 App 的 UDP 输入出现超时和恢复抖动：

```text
[Controller] 外部遥控输入状态: TIMEOUT UniRC 输入超时
[UniRC] 通道数据已恢复，序列=39700
[Controller] 外部遥控输入状态: TIMEOUT UniRC 输入超时
[UniRC] 通道数据已恢复，序列=39738
```

结论：本机 UDP 桥和另一个进程直接读取 `/dev/ttyHS3` 会竞争同一条串口字节流。它们不是稳定独立的两路输入。新 App 使用本机 UDP 桥时，不应再要求其他 App 直接读取 `/dev/ttyHS3`；如果必须多 App 共用摇杆数据，应改为单采集者分发。

## `freq = 0` 影响范围验证

验证日期：2026-06-24。

验证方法：

1. 停止本 App 和 `com.siyi.udpservice`，避免 UDP 桥读串口造成干扰。
2. 直接配置 `/dev/ttyHS3` 为 `115200 raw`。
3. 向 `/dev/ttyHS3` 写入 `HZ_50` 订阅帧，读取 2 秒建立基线。
4. 向 `/dev/ttyHS3` 写入 `freq = 0` 帧，立即读取 2 秒。
5. 再次向 `/dev/ttyHS3` 写入 `freq = 0` 帧，等待 1 秒后读取 3 秒，排除残留缓冲影响。
6. 最后写入 `HZ_50` 恢复通道输出。

使用的控制帧：

| 含义 | 帧 |
| --- | --- |
| `HZ_50` | `55 66 01 01 00 00 00 42 06 31 80` |
| `freq = 0` | `55 66 01 01 00 00 00 42 00 f7 e0` |

结果：

```text
baseline: bytes=4117 valid_frames=98 invalid_frames=0 first_seq=49902 last_seq=49999
after_stop: bytes=4116 valid_frames=98 invalid_frames=0 first_seq=50012 last_seq=50109
stop_wait_after: bytes=6217 valid_frames=148 invalid_frames=0 first_seq=50928 last_seq=51075
restore_check: bytes=2058 valid_frames=49 invalid_frames=0 first_seq=51098 last_seq=51146
```

结论：在当前 `Standard-10inch_A2` 设备状态下，直接向 `/dev/ttyHS3` 发送 UniRC `freq = 0` 后，串口通道帧仍按约 50Hz 持续输出。也就是说，本次验证没有发现 `freq = 0` 会关闭串口侧通道输出。由于新 App 当前通过 `com.siyi.udpservice` 使用 UDP 桥，且多 App 共用仍要求单采集者分发，第一版仍保持“停止本 App 输入消费时不主动发送 `freq = 0`”的保守策略。

## 当前结论

| 验证项 | 结论 |
| --- | --- |
| 串口通道帧 | `/dev/ttyHS3` 已验证可用 |
| 本机 UDP 服务 | `com.siyi.udpservice` 已验证监听 `127.0.0.1:19856`，App 可绑定并请求打开桥 |
| UDP 通道帧 | 本机桥已收到有效 `CMD_ID = 0x42` 通道帧 |
| UDP 与串口同时输出 | 已验证会竞争；不适合作为两个 App 的独立输入 |
| 不同 UDP 客户端端口同时订阅 | 已验证不适合作为稳定多 App 共用方案；服务更接近最近客户端地址模型 |
| `freq = 0` 对串口输出影响 | 串口直测未发现会停止通道输出；第一版仍不主动发送关闭频率帧 |

## 右侧系统导航栏验证

`Standard-10inch_A2` 横屏时系统导航键位于右侧。新 App 已取消 `enableEdgeToEdge()`，视频页也不再隐藏系统导航栏。真机窗口配置显示：

```text
mBounds=Rect(0, 0 - 1920, 1200)
mAppBounds=Rect(0, 0 - 1812, 1200)
mNavigationBarPosition=2
```

结论：App 内容区域应停在 `1812px` 宽度内，右侧约 `108px` 由系统导航栏保留，不做完全沉浸式全屏。

## 云卓 G20 RCSDK 输入验证

验证日期：2026-07-30。

设备参数详见 `docs/skydroid_g20_device_parameters.md`。本轮只验证遥控输入，不连接机器狗控制链路，不发送移动命令。

验证版本：

```text
LeggedJoystick_1.0.2_debug_202607301655.apk
rcsdk-v1.9.2.aar
```

验证方法：

1. 在主 `app` 启动时读取 RCSDK 设备类型，确认输入源 factory 的自动选择结果。
2. 使用主工程 `SkydroidG20DeviceTest` 直接启动 G20 输入源，不启动 Activity，也不依赖 ZMQ。
3. 等待 RCSDK 连接 `/dev/ttyHS2:115200`，再通过 `RemoteControllerKey.KeyChannels` 主动读取一帧通道。
4. 断开输入源，确认测试期间没有创建机器狗控制命令。

自动选择日志：

```text
[Controller] 自动选择遥控输入源: provider=skydroid_g20_rcsdk,
rcSdkDevice=G20, model=Bengal for arm64, boardVariant=0
```

RCSDK 连接与首帧：

```text
[G20] RCSDK 已连接，开始读取摇杆通道
[G20真机测试] 通道=[1500, 1500, 1500, 1500, 2100, 900, 900, 900, 900, 900, 900, 900, 900, 1500, 900, 900]
[G20真机测试] 输入状态=RUNNING, 信息=G20 摇杆通道已连接
```

真机测试结果：

```text
OK (1 test)
Time: 0.129
```

结论：

| 验证项 | 结论 |
| --- | --- |
| 自动设备识别 | `Bengal for arm64` 且 `ro.boot.ZBBoard=0` 被 RCSDK 1.9.2 识别为 `DeviceType.G20` |
| 输入源选择 | 主 App 自动选择 `skydroid_g20_rcsdk`，不启动思翼 UDP 桥 |
| RCSDK 通道 | `/dev/ttyHS2:115200` 已连接，`KeyChannels` 可主动读取 |
| G20 通道基准 | 真机使用 `900/1500/2100`，不能套用参考项目的 `282/1002/1722` |
| 速度档 | G20 不输出硬件速度档请求，只保留屏幕低/中/高速切换 |
| 安全边界 | 本轮没有连接 ZMQ，也没有发送机器狗移动命令 |
