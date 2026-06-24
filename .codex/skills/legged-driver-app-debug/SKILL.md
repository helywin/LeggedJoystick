---
name: legged-driver-app-debug
description: Use when debugging, validating, or documenting Android remote App integration with legged_driver on RK3588 over ZMQ, ADB, and SSH, including connection timeout, heartbeat/subscription, control takeover/release, native remote controller switching, and real-device verification.
---

# Legged Driver App Debug

## 适用范围

当用户提到新遥控 App 与 `legged_driver` 真机联调、连接超时、接管/释放异常、原生遥控器切换后 App 连不上、ZMQ 心跳/订阅问题、RK3588 服务状态、ADB 真机验证时使用本 Skill。

本 Skill 只覆盖 Android App 到 `legged_driver` 的联调链路。摇杆 UniRC 原始帧、串口桥、App UI 设计问题只在它们影响连接、控制权或安全输出时纳入排查。

## 核心原则

- 先定位断点，再改代码；不要把“重启 App/服务能恢复”直接当成修复。
- 分层排查顺序固定为：Android 设备和网络、TCP 端口、`legged_driver` 服务、ZMQ 心跳和订阅、协议 CRC、控制权、UI 状态。
- 真机日志的时间可能不一致；Android、RK3588、开发机时间戳只能按事件顺序对齐，不要只按绝对时间判断。
- 涉及运动输出时默认保持摇杆归零，不主动发送移动命令；接管测试和运动测试分开做。
- 结论要写回 `docs/rk3588_legged_driver_deployment.md` 或相关文档，避免只留在聊天记录里。

## 快速状态采集

先确认 ADB 设备、网络、端口和服务端状态：

```bash
adb devices -l
adb -s d shell ip addr show wlan0
adb -s d shell ping -c 1 -W 2 192.168.234.1
timeout 3 bash -lc '</dev/tcp/192.168.234.1/33445' && echo ZMQ_PORT_OPEN || echo ZMQ_PORT_CLOSED
ssh -o ConnectTimeout=5 robot@192.168.234.1 'systemctl is-active legged-driver.service; ss -ltnp | grep 33445 || true; journalctl -u legged-driver.service -n 80 --no-pager'
```

采集 App 当前 UI 和日志：

```bash
adb -s d shell uiautomator dump /sdcard/window.xml >/dev/null
adb -s d shell cat /sdcard/window.xml | rg -n '连接|断开|驱动|机器|接管|释放|连接超时|发送'
adb -s d logcat -d -v time | rg -i 'Legged|helywin|ZMQ|Controller|crc|timeout|超时|heartbeat|subscription|订阅|心跳|socket|I/O|context|Interrupted'
```

需要复现时，先清空日志，再点击一次，不要混用旧日志：

```bash
adb -s d logcat -c
adb -s d shell input tap 1318 151
sleep 4
adb -s d logcat -d -v time | rg -i 'Legged|helywin|ZMQ|Controller|timeout|超时|heartbeat|subscription|订阅|心跳|socket|已收到服务端|连接状态|I/O|context|Interrupted'
```

## 连接问题判读

按下面模式判断故障层：

| 证据 | 优先判断 |
| --- | --- |
| Android 不在 `192.168.234.x` 或 ping 不通 `192.168.234.1` | Wi-Fi/热点/路由层问题 |
| TCP 端口不开，`systemctl` 非 active | RK3588 服务或 systemd 配置问题 |
| TCP `ESTAB`，但服务端没有新 heartbeat | App ZMQ 首包发送、I/O 线程或资源释放问题 |
| 服务端收到 heartbeat，但 App 2.5 秒超时 | 服务端没有推送快照、订阅未成功或 App 接收/校验失败 |
| 服务端报 CRC 失败 | proto/Wire 编码或 CRC 计算不一致，优先查 repeated packed 字段 |
| 强停 App 后立刻恢复 | App 进程内 ZMQ 生命周期或 UI 状态残留问题 |
| 连接成功但 UI 显示占用 | 不要直接相信 `RobotState.control_source`，它不是本 App 的控制权 |

## ZMQ 生命周期检查

排查或修改 `NewZmqClient` 时确认这些约束：

- 所有 ZMQ socket 读写必须在同一个 I/O 线程内完成。
- 创建 socket 后立即发送 heartbeat 和默认订阅；不能等待服务端先回复 heartbeat。
- 非阻塞发送返回 `false` 必须输出日志，不能静默等待连接超时。
- 断开或连接超时要停止循环、清空发送队列、关闭 socket 和 context。
- 正常断开优先让 I/O 线程自然退出；只有超时未退出才强制中断。
- JeroMQ 在中断标记存在时关闭 context 可能失败；关闭 context 前要避免带着中断状态进入关闭流程。
- 同一 App 进程内必须验证 `连接 -> 断开 -> 再连接`，不能只验证强停 App 后能连。

## 控制权验证

真实 RK3588 联调时，`RobotState.control_source = CTRL_SOURCE_SDK` 表示 `legged_driver` 底层 SDK 通道正在工作，不代表当前 Android ZMQ 客户端已经接管或被其他客户端占用。

控制权状态以这些事件为准：

| 场景 | 期望 |
| --- | --- |
| 点击连接 | 只连接和订阅，不自动接管，不发送运动命令 |
| 连接成功 | UI 显示 `可接管`、`接管`、`驱动 已连接  机器 在线` |
| 点击接管 | 服务端收到 `TAKE_CONTROL`，App 收到 ACK 成功 |
| 接管成功 | App 发送 `SET_APP_MODE MANUAL` 和当前速度档 |
| 点击释放 | 服务端收到 `RELEASE_CONTROL` |
| 释放完成 | UI 回到 `可接管`，App 请求 `SET_APP_MODE AUTO` |
| 释放 ACK 缺失 | 仅在释放中可用 `CTRL_SOURCE_UNKNOWN/OTHER` 作为兜底 |

## 修复后验证

本地验证至少执行：

```bash
git diff --check
openspec validate rewrite-kotlin-remote-app --strict
./gradlew :app:testDebugUnitTest --tests com.helywin.leggedjoystick.zmq.NewZmqClientTest --tests com.helywin.leggedjoystick.controller.ControllerStateTest --tests com.helywin.leggedjoystick.proto.MessageUtilsTest :app:assembleDebug
```

真机验证至少执行：

1. 安装最新 `LeggedJoystick_*.apk`。
2. 点击连接，确认 App 收到服务端 heartbeat、connection_state、app_mode、robot_state、motion_data、odometry。
3. 点击断开，确认日志出现 `I/O 线程结束并已释放 ZMQ 资源`，且没有 `关闭 context 失败`。
4. 不强停 App，直接再次点击连接，确认再次进入 `CONNECTED`。
5. 查看 RK3588 日志，确认服务端收到新的 heartbeat，客户端数量为 1。
6. 如只验证连接，不点击接管，不发送动作或运动命令。

## 记录和提交

每次真机联调后更新文档，至少记录：

- APK 文件名和安装时间。
- Android 设备 IP、RK3588 服务状态、ZMQ endpoint。
- 复现步骤、关键 Android 日志、关键服务端日志。
- 根因判断和排除项，例如“不是原生遥控器永久占用”“不是端口不可达”。
- 修复后验证结果，特别是同进程重连、接管、释放和零速度状态。

提交前按项目规则执行相关测试。提交信息使用中文 Conventional Commits。
