# RK3588 上 legged_driver 部署与安卓端联调记录

## 目标

把 `/home/jiang/code/legged_driver` 部署到机器狗 RK3588 板子的用户目录下，并让新遥控 App 通过机器狗热点连接 `legged_driver` 的 ZMQ 服务。本文只记录服务端部署、网络入口和安卓端联调状态，不记录 UniRC 输入链路；UniRC 验证见 `docs/remote_input_device_verification.md`。

## 目标设备

| 项目 | 当前值 |
| --- | --- |
| 登录用户 | `robot` |
| 设备地址 | `192.168.234.1` |
| 板卡型号 | `Firefly AIO-3588SJD4 HDMI(Linux)` |
| CPU 架构 | `aarch64` |
| 内核 | `5.10.160-rt78-preempt` |
| 代码目录 | `/home/robot/legged_driver` |
| 可执行文件 | `/home/robot/legged_driver/bin/legged_driver` |
| ZMQ 监听 | `0.0.0.0:33445` |

## 部署结果

代码已经同步到 RK3588 的 `/home/robot/legged_driver`，并在目标机器上原生构建：

```bash
cmake -S . -B build -DBUILD_TEST=OFF -DBUILD_EXAMPLE=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build build --target legged_driver -j$(nproc)
```

运行配置位于 `/home/robot/legged_driver/bin/config/legged_driver.json`：

| 配置项 | 当前值 |
| --- | --- |
| `dog_ip` | `127.0.0.1` |
| `robot_port` | `8081` |
| `local_ip` | `127.0.0.1` |
| `local_port` | `43988` |

`librobot_sdk.so.0.0.6` 已通过 `/home/robot/legged_driver/lib/librobot_sdk.so.0.0.6 -> ../sdk/lib/aarch64/librobot_sdk.so.0.0.6` 解析，`ldd bin/legged_driver` 能解析 SDK 库和 `libspdlog.so.1.15`。

## 机器狗本机 SDK 配置

`/opt/export/config/sdk_config.yaml` 已备份并改为本机回环地址：

| 配置项 | 当前值 |
| --- | --- |
| `target_ip` | `127.0.0.1` |
| `target_port` | `43988` |

`/opt/runtime/bin/start_motion_control.sh` 已备份并补充运行环境：

```bash
export LD_LIBRARY_PATH=/opt/export/mc/bin
export SDK_CLIENT_IP="127.0.0.1"
export ROBOT_TYPE=ZGWS
cd /opt/export/mc/bin && taskset -c 7 ./mc_ctrl r
```

当前运行中的 `mc_ctrl` 环境未必已经继承 `SDK_CLIENT_IP`，但 `legged_driver` 已经可以连接成功。下次重启 `mc_ctrl` 或机器狗启动流程后，脚本配置会生效。

## systemd 服务

服务文件位于 `/etc/systemd/system/legged-driver.service`，当前设置为开机启用：

| 项目 | 当前值 |
| --- | --- |
| `User` | `robot` |
| `WorkingDirectory` | `/home/robot/legged_driver/bin` |
| `ExecStart` | `/home/robot/legged_driver/bin/legged_driver` |
| `Restart` | `on-failure` |
| `RestartSec` | `5` |
| `LD_LIBRARY_PATH` | `/home/robot/legged_driver/lib:/home/robot/legged_driver/sdk/lib/aarch64:/opt/export/mc/bin` |

已验证：

```bash
systemctl is-enabled legged-driver.service
systemctl is-active legged-driver.service
```

结果为 `enabled` 和 `active`。`ss -ltnp` 显示 `legged_driver` 监听 `0.0.0.0:33445`，`mc_ctrl` 监听 `0.0.0.0:8081`。

## 运行日志状态

`legged_driver` 日志显示当前配置为 `robot=127.0.0.1:8081`，backend 为 `sdk`，并进入 `CONNECTED` 状态：

```text
机器人连接成功
当前连接客户端数量: 0, 应用模式: AUTO, 机器人连接状态: CONNECTED
```

开发机通过机器狗热点已经验证 TCP 端口可达：

```bash
timeout 3 bash -lc '</dev/tcp/192.168.234.1/33445'
```

## 机器狗热点

RK3588 上 `/tmp/hostapd.conf` 当前热点信息：

| 项目 | 当前值 |
| --- | --- |
| `ssid` | `M1-64F010` |
| `wpa_passphrase` | `12345678` |
| `wpa_key_mgmt` | `WPA-PSK` |

开发机已经通过无线网卡连接到 `M1-64F010`，并能访问 `192.168.234.1:33445`。

## 安卓端默认连接参数

新遥控 App 的真实联调默认参数应保持为：

| 设置项 | 当前值 |
| --- | --- |
| ZMQ IP | `192.168.234.1` |
| ZMQ 端口 | `33445` |
| 视频地址 | `rtsp://192.168.234.1:8554/test` |
| 工程 Mock | `false` |
| UniRC UDP | `127.0.0.1:19856` |

App 连接到 `legged_driver` 后不自动接管；只有用户手动点击接管后才允许发送接管命令和移动命令。

## 当前安卓设备测试状态

当前连接的 ADB 设备：

| 项目 | 当前值 |
| --- | --- |
| ADB serial | `d` |
| 型号 | `Standard_10inch_A2` |
| Android | `13` |

已连接机器狗热点 `M1-64F010`，安卓端 `wlan0` 地址为 `192.168.234.206`，信号约 `-46 dBm`，能够 ping 通 `192.168.234.1`。

```bash
adb -s d shell ping -c 1 -W 2 192.168.234.1
```

结果为 0% 丢包，延迟约 16ms。

## 安卓真实联调结果

2026-06-24 已用 `RIDReceiver_1.0.2_debug_202606241043.apk` 完成真实 ZMQ 连接验收。测试前清空旧偏好，工程 Mock 为关闭状态，App 使用默认 `192.168.234.1:33445`。

初次真实联调暴露两个问题：

1. App 只发送 heartbeat，等待服务端有效消息后才发送订阅；真实 `legged_driver` 不会直接回复客户端 heartbeat，只会在收到订阅后发送快照，导致 App 侧 2.5 秒连接验证超时。
2. 修复握手顺序后，服务端收到订阅请求但报 CRC 失败。根因是 Wire 对 `repeated SubscriptionTopic topics` 生成了非 packed 编码，而 C++ proto3 重序列化时按默认 packed 编码计算 CRC。已在本地 `proto/message.proto` 对 `topics` 显式标注 `[packed = true]`，并增加单元测试约束 Wire 输出为 packed 编码。

修复后，App 连接按钮点击后进入已连接状态，服务端日志持续收到客户端 `remote_73e2e0f7` 心跳，`当前连接客户端数量` 为 1。App UI 验收结果：

| 项目 | 结果 |
| --- | --- |
| 连接按钮 | 显示为 `断开连接` |
| 调试面板 | `驱动 已连接  机器 在线  故障 0` |
| 订阅数据 | 收到服务器心跳、机器人状态、MotionData 和 Odometry |
| 电量显示 | 收到并显示电量 `32` |
| 控制权 | 未点击接管，按钮显示占用状态，不发送运动或动作命令 |

本轮只验证连接、心跳、订阅和状态显示，未做真机接管和运动控制。

## 下一步测试流程

1. 低速真机联调前，先确认周围环境安全、机器狗处于可控姿态。
2. 点击接管前再次确认 App UI 显示 `驱动 已连接  机器 在线`。
3. 首次运动测试只使用低速档，先验证前进、平移、转向三个轴的方向。
4. 如方向与预期相反，只调整工程调试页轴反向配置，不修改协议发送层符号约定。
5. 真机运动测试完成后记录控制权 ACK、零速度保护和断开后的服务端超时状态。
