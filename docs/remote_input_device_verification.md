# 遥控输入设备实测记录

## 验证边界

本记录只覆盖 UniRC 遥控输入链路，不包含 ZMQ 和机器狗控制链路。ZMQ 连接、机器狗地址和 `legged_driver` 联调等到机器狗设备一起开启后再统一验证。

## 设备信息

| 项 | 结果 |
| --- | --- |
| ADB 设备 | `d` |
| 设备型号 | `Standard-10inch_A2` |
| Android 版本 | `13` |
| App 安装 | `adb install -r app/build/outputs/apk/debug/RIDReceiver_1.0.2_debug_202606231412.apk` 成功 |
| App 启动 | `com.helywin.leggedjoystick` 可启动，启动后 PID 为 `7280` |

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

当前设备网络状态下，`wlan0` 为 `NO-CARRIER/DOWN`，对 UniRC UDP 默认地址和机器狗默认地址均无路由：

```text
ping 192.168.144.20 -> Network is unreachable
ping 192.168.234.1  -> Network is unreachable
```

结论：当前未验证 UniRC UDP 通道输出，也不能判断 UDP 与串口是否能同时输出。后续需要在设备连到 UniRC UDP 网络后继续验证。

## 当前结论

| 验证项 | 结论 |
| --- | --- |
| 串口通道帧 | `/dev/ttyHS3` 已验证可用 |
| UDP 通道帧 | 未验证，设备当前无到 `192.168.144.20` 的路由 |
| UDP 与串口同时输出 | 未验证 |
| 不同 UDP 客户端端口同时订阅 | 未验证 |
| `freq = 0` 对串口输出影响 | 未验证，App 仍不得默认发送关闭频率帧 |
