# 摇杆共用探测工具

这个目录放独立探测工具，用于在接入 Android App 前验证 UniRC 摇杆数据能否被多个 App 共用。

它刻意放在正式 App 模块外，只用于传输层行为验证：

| 探测项 | 用途 |
| --- | --- |
| `local-reuse` | 在同一个本地 UDP 端口绑定两个接收者，并向该端口发送本机单播包。 |
| `recv` | 启动一个 UDP 接收进程。运行两份可以模拟两个 App。 |
| `send` | 向指定接收端口发送本机 UDP 包。 |
| `unirc-udp` | 向 `192.168.144.20:19856` 发送 UniRC `CMD_ID = 0x42` 开启帧，并接收通道帧。 |
| `serial-read` | 从 `/dev/ttyHS3` 等串口设备读取 UniRC 帧。 |

编译：

```bash
javac tools/joystick_probe/JoystickProbe.java
```

本机 UDP 端口复用检查：

```bash
java -cp tools/joystick_probe JoystickProbe local-reuse --port 41986 --count 20
```

双进程 UDP 接收检查：

```bash
java -cp tools/joystick_probe JoystickProbe recv --port 41986 --seconds 20 --reuse true
java -cp tools/joystick_probe JoystickProbe recv --port 41986 --seconds 20 --reuse true
java -cp tools/joystick_probe JoystickProbe send --host 127.0.0.1 --port 41986 --count 20
```

UniRC UDP 检查：

```bash
java -cp tools/joystick_probe JoystickProbe unirc-udp --remote 192.168.144.20 --remote-port 19856 --local-port 41986 --freq 5 --seconds 15
```

运行两个相同 `--local-port` 的 `unirc-udp` 进程，可以测试同端口复用。运行两个不同本地端口的 `unirc-udp` 进程，可以测试 UniRC UDP 端是否支持多个订阅者。

串口检查：

```bash
stty -F /dev/ttyHS3 115200 raw -echo
java -cp tools/joystick_probe JoystickProbe serial-read --device /dev/ttyHS3 --seconds 15
```

结果解释：

| 结果 | 含义 |
| --- | --- |
| 同端口 UDP 接收者不能同时收到每个单播包 | UDP 端口复用不足以让多个 App 共享同一份单播流。 |
| 不同本地 UDP 端口都能收到 UniRC 通道帧 | 多个 App 可以各自用不同客户端端口订阅 UDP。 |
| 只有最后一个 UDP 请求方收到帧 | UniRC UDP 输出更像单订阅者模式，需要一个采集者在 Android 内部分发。 |
| UDP 和串口能同时收到帧 | 一个 App 可用 UDP，另一个 App 可用串口，但要考虑设备权限和串口独占。 |

## 当前实测结果

在本机 Linux 环境中执行：

```bash
java -cp /tmp/joystick_probe_classes JoystickProbe local-reuse --port 41986 --count 20
```

结果为：

| 接收者 | 收到包数 |
| --- | --- |
| receiver-1 | 0 |
| receiver-2 | 20 |

结论：两个进程即使都设置 `SO_REUSEADDR` 并绑定同一个 UDP 端口，单播包也不会被复制给两个接收者，而是全部进入其中一个 socket。因此不能把“多个 App 绑定同一个本地 UDP 端口”作为摇杆数据共享方案。

当前环境执行 UniRC UDP 订阅：

```bash
java -cp /tmp/joystick_probe_classes JoystickProbe unirc-udp --remote 192.168.144.20 --remote-port 19856 --local-port 41986 --freq 5 --seconds 5
```

结果 5 秒内未收到回包。这个结果只能说明当前运行环境没有收到 UniRC UDP 数据，不能证明遥控器不支持 UDP 输出；需要在连接 UniRC 网络后继续测试。

## 建议验证顺序

1. 先确认单个 `unirc-udp` 进程能收到 CH1 到 CH16。
2. 再启动两个不同 `--local-port` 的 `unirc-udp` 进程，确认 UniRC 是否支持多个 UDP 订阅者。
3. 如果只有最后一个 UDP 订阅者能收到数据，就做一个采集进程或采集 App，再用 Android 显式广播、绑定服务或共享进程内总线分发。
4. 如果需要验证“一个 App 用 UDP、一个 App 用串口”，先确认设备上 `/dev/ttyHS3` 或 `/dev/ttyHS0` 存在，再同时运行 `unirc-udp` 和 `serial-read`。
