# RK3588 上 legged_driver 部署与安卓端联调记录

> 2026-09-28 更新：App 读取活动网卡本地 IPv4 决定底盘入口：`192.168.168.*` 对应 `192.168.168.168`，`192.168.144.*` 对应 `192.168.144.144`，`192.168.234.*` 对应 `192.168.234.1`；图传网段优先。控制 `33445`、救援版主控 `33446` 和头尾视频 `8554` 随标准入口一起切换，非标准手工地址保留。下文 2026-06 的热点联调是历史记录。

## 目标

把 `/home/jiang/code/legged_driver` 部署到机器狗 RK3588 板子的用户目录下，并让新遥控 App 通过当前网络入口连接 `legged_driver` 的 ZMQ 服务。本文只记录服务端部署、网络入口和安卓端联调状态，不记录 UniRC 输入链路；UniRC 验证见 `docs/remote_input_device_verification.md`。

## 目标设备

| 项目 | 当前值 |
| --- | --- |
| 登录用户 | `robot` |
| 本次图传地址 | `192.168.168.168` |
| 板卡型号 | `Firefly AIO-3588SJD4 HDMI(Linux)` |
| CPU 架构 | `aarch64` |
| 内核 | `5.10.160-rt78-preempt` |
| 代码目录 | `/home/robot/legged_driver` |
| 可执行文件 | `/home/robot/legged_driver/bin/legged_driver` |
| ZMQ 监听 | `0.0.0.0:33445` |

## 初次部署结果（2026-06）

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

### 2026-09-28 巡检版驱动升级

本次通过笔记本的 `192.168.168.110/24` 有线接口访问底盘 `192.168.168.168`。升级前，底盘可 ping 通、TCP `33445` 可连接，但运行中的旧驱动不支持 App 使用的产品身份与协议版本 2 心跳。将当前 `/home/jiang/code/legged_driver` 源码同步到板端独立目录 `/home/robot/legged_driver_upgrade_20260928` 后，在 RK3588 原生构建并确认产物为 ARM64，动态库均可解析。

先备份旧版二进制、配置、动态库和 systemd 单元至 `/home/robot/legged_driver_rollback_20260928`，再替换线上二进制与配置并重启 `legged-driver.service`。新配置保留原 SDK 地址、端口和速度限制，移除旧字段 `default_app_mode`，设置 `deployment_product=GENERAL_ROBOT`。运行中的二进制 SHA-256 为 `518fe7697d1ca280830d83c74d58a0202696242f52711bb11b22cb08e82cd0a3`。

升级后服务保持 `active`，监听 `0.0.0.0:33445`，SDK 后端进入 `CONNECTED`。笔记本只发送 `REMOTE_CONTROLLER + GENERAL_ROBOT + protocol_version=2` 心跳，收到服务端 `admitted=1`、`robot_connected=1`、`准入成功`；没有发送控制或运动命令。旧 `ROBOT_CONTROLLER` 客户端仍以协议版本 0 发心跳和命令，现被拒绝准入；这是本次选择巡检版产品身份后的实际运行状态。随后用户在思翼遥控器图传网络下实测已安装巡检版 App 可以连接底盘。此项为用户真机确认；由于 USB ADB 接入时安卓以太网会断开，Codex 未独立采集安卓连接日志，也未验证运动。

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

## 初次部署运行日志状态（2026-06）

`legged_driver` 日志显示当前配置为 `robot=127.0.0.1:8081`，backend 为 `sdk`，并进入 `CONNECTED` 状态：

```text
机器人连接成功
当前连接客户端数量: 0, 应用模式: AUTO, 机器人连接状态: CONNECTED
```

开发机通过机器狗热点已经验证 TCP 端口可达：

```bash
timeout 3 bash -lc '</dev/tcp/192.168.234.1/33445'
```

## 机器狗热点（2026-06 历史记录）

RK3588 上 `/tmp/hostapd.conf` 当前热点信息：

| 项目 | 当前值 |
| --- | --- |
| `ssid` | `M1-64F010` |
| `wpa_passphrase` | `12345678` |
| `wpa_key_mgmt` | `WPA-PSK` |

开发机已经通过无线网卡连接到 `M1-64F010`，并能访问 `192.168.234.1:33445`。

## 安卓端默认连接参数

以下是 2026-06 热点联调时的参数；当前入口选择规则见文首更新：

| 设置项 | 当前值 |
| --- | --- |
| ZMQ IP | `192.168.234.1` |
| ZMQ 端口 | `33445` |
| 头部视频 | `rtsp://192.168.234.1:8554/front` |
| 尾部视频 | `rtsp://192.168.234.1:8554/back` |
| 工程 Mock | `false` |
| UniRC UDP | `127.0.0.1:19856` |

App 连接到 `legged_driver` 后不自动接管；只有用户手动点击接管后才允许发送接管命令和移动命令。

## 安卓设备热点测试状态（2026-06 历史记录）

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

2026-06-24 已用 `LeggedJoystick_1.0.2_debug_202606241054.apk` 完成真实 ZMQ 连接、接管和释放验收。测试前清空旧偏好，工程 Mock 为关闭状态，App 使用默认 `192.168.234.1:33445`。

初次真实联调暴露两个问题：

1. App 只发送 heartbeat，等待服务端有效消息后才发送订阅；真实 `legged_driver` 不会直接回复客户端 heartbeat，只会在收到订阅后发送快照，导致 App 侧 2.5 秒连接验证超时。
2. 修复握手顺序后，服务端收到订阅请求但报 CRC 失败。根因是 Wire 对 `repeated SubscriptionTopic topics` 生成了非 packed 编码，而 C++ proto3 重序列化时按默认 packed 编码计算 CRC。已在本地 `proto/message.proto` 对 `topics` 显式标注 `[packed = true]`，并增加单元测试约束 Wire 输出为 packed 编码。

修复握手和 CRC 后，App 连接按钮点击后进入已连接状态，服务端日志持续收到客户端 `remote_73e2e0f7` 心跳，`当前连接客户端数量` 为 1。App UI 验收结果：

| 项目 | 结果 |
| --- | --- |
| 连接按钮 | 显示为 `断开连接` |
| 调试面板 | `驱动 已连接  机器 在线  故障 0` |
| 订阅数据 | 收到服务器心跳、机器人状态、MotionData 和 Odometry |
| 电量显示 | 收到并显示电量 `32` |
| 控制权 | 连接后显示 `可接管` 和 `接管`，不自动发送运动或动作命令 |

真实设备联调确认：`RobotState.control_source = CTRL_SOURCE_SDK` 表示 `legged_driver` 底层 SDK 通道正在工作，不代表当前 Android ZMQ 客户端已经占用控制权。App 侧控制权状态以 `TAKE_CONTROL`、`RELEASE_CONTROL` ACK 和控制权事件为准；释放中如果没有收到释放 ACK，可把 `RobotState.control_source` 回到 `CTRL_SOURCE_UNKNOWN` 或 `CTRL_SOURCE_OTHER` 作为释放完成兜底。

接管与释放验收结果：

| 项目 | 结果 |
| --- | --- |
| 接管请求 | 点击接管后服务端收到 `TAKE_CONTROL`，App 收到 ACK `error_code = 0`、`reason = OK` |
| 手动模式 | 接管成功后 App 发送 `SET_APP_MODE`，服务端状态变为 `MANUAL` |
| 速度档 | 接管成功后 App 发送当前速度档，低速默认档下服务端收到 `SET_SPEED_LEVEL` |
| UI 状态 | 接管成功后显示 `已接管` 和 `释放`，动作按钮进入可用状态 |
| 释放请求 | 点击释放后服务端收到 `RELEASE_CONTROL` |
| 自动模式 | 释放完成后 App 发送 `SET_APP_MODE`，服务端状态回到 `AUTO` |
| UI 释放兜底 | 未收到释放 ACK 时，App 通过底层控制来源回到未知状态退出 `释放中`，显示 `可接管` 和 `接管` |
| 运动命令 | 本轮未发送移动命令；UI 摇杆读数保持 `前进 0.00 平移 0.00 转向 0.00` |

最新服务端状态：

```text
当前连接客户端数量: 1, 应用模式: AUTO, 机器人连接状态: CONNECTED
```

本轮已验证连接、心跳、订阅、状态显示、接管和释放闭环，尚未做真机方向运动测试。

## 安卓视频流联调结果

2026-06-25 验证主屏双路 RTSP 视频。最初 App 默认地址使用 `/head` 和 `/tail`，`ffprobe` 返回 404。RK3588 上 `robot_camera_node` 和 `mediamtx` 的真实发布路径为：

| 视频源 | RTSP 地址 | 实测码流 |
| --- | --- | --- |
| 头部相机 | `rtsp://192.168.234.1:8554/front` | H.264, 1920x1080, 25fps |
| 尾部相机 | `rtsp://192.168.234.1:8554/back` | H.264, 1920x1080, 25fps |

App 已把默认地址改为 `/front` 和 `/back`，并在读取旧安装偏好时把旧默认 `/head`、`/tail` 迁移到新路径。真机安装 `LeggedJoystick_1.0.2_debug_202606251557.apk` 后，主屏背景和左上小窗均能显示真实相机画面，点击左上小窗可在 App 本地互换主背景和小窗视频源。本轮只验证视频链路，没有点击接管，也没有发送运动或动作命令。

截图记录：

| 场景 | 文件 |
| --- | --- |
| 默认头部背景、尾部小窗 | `docs/assets/implementation/main-control-real-video-20260625.png` |
| 点击小窗后互换 | `docs/assets/implementation/main-control-real-video-swapped-20260625.png` |
| 背景铺满、小窗 16:9 | `docs/assets/implementation/main-control-video-fill-20260625.png` |
| 背景铺满后点击小窗互换 | `docs/assets/implementation/main-control-video-fill-swapped-20260625.png` |

当前板端相机配置位于 `/opt/robot/install/robot_camera/share/robot_camera/config/zsm.yaml`，两路 `bps` 均为 `2000000`。1080p/25fps 使用 2Mbps 会在全屏背景下出现明显压缩感；如需提升画质，建议把两路 `bps` 提高到 `4000000` 或 `6000000` 后重启 `robot_camera`，再用 Android 端实测延迟和 Wi-Fi 稳定性。

## ZMQ 重连问题记录

2026-06-24 在切换使用原生遥控器后，出现新 App 点击连接后超时的问题。排查结果如下：

| 检查项 | 结果 |
| --- | --- |
| 安卓网络 | 遥控器仍在机器狗热点，`wlan0 = 192.168.234.206`，可 ping 通 `192.168.234.1` |
| 服务端端口 | `legged_driver` 仍为 `active`，`0.0.0.0:33445` 正常监听 |
| 服务端客户端数 | 失败时客户端数为 0，但存在一条旧 TCP `ESTAB` 连接 |
| Android 日志 | App 创建 ZMQ socket 后 2.5 秒内没有收到服务端消息，进入 `CONNECTION_TIMEOUT` |
| 强停验证 | 强停 App 后重启并点击连接可立即成功，说明不是原生遥控器永久占用机器狗或服务端端口 |

根因定位为 App 侧 ZMQ 连接失败/断开时资源释放不彻底：旧实现使用 `shutdownNow` 中断 I/O 线程，JeroMQ 在中断标记存在时关闭 context 可能抛 `Interrupted function`，导致底层 TCP 连接残留。修复后正常断开先让 I/O 线程自然退出，只有超时才强制中断；关闭 context 前清除中断标记，并在发送失败时输出明确日志。

已用 `LeggedJoystick_1.0.2_debug_202606241118.apk` 在真实遥控器上验证：

1. 首次点击连接后立即收到服务端心跳和状态订阅数据。
2. 同一 App 进程内点击断开，日志显示 `I/O 线程结束并已释放 ZMQ 资源`，没有再出现 `关闭 context 失败`。
3. 不强停 App 直接再次点击连接，可再次立即连接成功。
4. 连接成功后 UI 显示 `可接管`、`断开连接`、`驱动 已连接  机器 在线`。

## 下一步测试流程

1. 低速真机联调前，先确认周围环境安全、机器狗处于可控姿态。
2. 点击接管前再次确认 App UI 显示 `驱动 已连接  机器 在线`。
3. 首次运动测试只使用低速档，先验证前进、平移、转向三个轴的方向。
4. 如方向与预期相反，只调整工程调试页轴反向配置，不修改协议发送层符号约定。
5. 真机运动测试完成后记录控制权 ACK、零速度保护和断开后的服务端超时状态。
