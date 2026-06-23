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

## 当前结论

| 验证项 | 结论 |
| --- | --- |
| 串口通道帧 | `/dev/ttyHS3` 已验证可用 |
| 本机 UDP 服务 | `com.siyi.udpservice` 已验证监听 `127.0.0.1:19856`，App 可绑定并请求打开桥 |
| UDP 通道帧 | 本机桥已收到有效 `CMD_ID = 0x42` 通道帧 |
| UDP 与串口同时输出 | 已验证会竞争；不适合作为两个 App 的独立输入 |
| 不同 UDP 客户端端口同时订阅 | 未验证 |
| `freq = 0` 对串口输出影响 | 未验证，App 仍不得默认发送关闭频率帧 |

## 右侧系统导航栏验证

`Standard-10inch_A2` 横屏时系统导航键位于右侧。新 App 已取消 `enableEdgeToEdge()`，视频页也不再隐藏系统导航栏。真机窗口配置显示：

```text
mBounds=Rect(0, 0 - 1920, 1200)
mAppBounds=Rect(0, 0 - 1812, 1200)
mNavigationBarPosition=2
```

结论：App 内容区域应停在 `1812px` 宽度内，右侧约 `108px` 由系统导航栏保留，不做完全沉浸式全屏。
