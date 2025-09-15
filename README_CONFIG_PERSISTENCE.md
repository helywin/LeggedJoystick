/*********************************************************************************
 * FileName: README_CONFIG_PERSISTENCE.md
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 配置持久化功能说明文档
 * Others:
 *********************************************************************************/

# 配置持久化功能说明

## 功能概述

本应用现已支持配置持久化功能，所有用户设置将自动保存到文件中，重启应用后会自动加载之前的配置。

## 实现的功能

### 1. 应用设置自动保存
- **ZMQ IP地址**: 默认 127.0.0.1，可在设置界面修改并自动保存
- **ZMQ端口**: 默认 33445，可在设置界面修改并自动保存  
- **狂暴模式状态**: 记住用户是否开启了狂暴模式

### 2. 机器人状态自动保存
- **机器人模式**: 记住最后设置的模式(PASSIVE/LIE_DOWN/STAND)
- **控制模式**: 记住最后设置的控制模式(MANUAL/AUTO)

### 3. 文件存储机制
- 使用Android SharedPreferences进行本地存储
- 存储文件名: `legged_joystick_settings`
- 存储位置: 应用私有数据目录，卸载应用时自动清除

## 使用方法

### 自动功能（无需用户操作）
1. **应用启动**: 自动加载之前保存的所有配置
2. **模式切换**: 每次改变机器人模式或控制模式时自动保存
3. **应用关闭**: 所有配置已实时保存，无需手动保存

### 手动功能
1. **设置界面**: 进入设置 → 修改IP/端口 → 点击"保存"按钮
2. **首次使用**: 所有配置将使用默认值，修改后自动保存

## 技术实现

### 核心类说明

#### 1. SettingsManager
```kotlin
// 保存应用设置
settingsManager.saveSettings(settings)

// 加载应用设置  
val settings = settingsManager.loadSettings()

// 保存机器人模式
settingsManager.saveRobotMode(RobotMode.STAND)

// 加载机器人模式
val robotMode = settingsManager.loadRobotMode()
```

#### 2. RobotController集成
```kotlin
// 初始化时自动加载配置
private fun loadSettings() {
    val settings = settingsManager.loadSettings()
    settingsState.updateSettings(settings)
}

// 保存设置（在设置界面调用）
fun saveAppSettings(settings: AppSettings) {
    settingsManager.saveSettings(settings)
    settingsState.updateSettings(settings)
}
```

#### 3. 自动保存触发点
- 机器人模式切换时：`setRobotMode()` 方法中自动调用 `saveRobotMode()`
- 控制模式切换时：`setControlMode()` 方法中自动调用 `saveControlMode()`  
- 应用设置修改时：设置界面点击保存按钮时调用 `saveAppSettings()`

## 存储的配置项

### SharedPreferences键值对照表
| 键名 | 类型 | 默认值 | 说明 |
|-----|------|-------|------|
| `zmq_ip` | String | "127.0.0.1" | ZMQ服务器IP地址 |
| `zmq_port` | Int | 33445 | ZMQ服务器端口 |
| `rage_mode_enabled` | Boolean | false | 狂暴模式是否启用 |
| `robot_mode` | String | "stand_up" | 机器人模式 |
| `control_mode` | String | "remote_controller" | 控制模式 |

## 错误处理

1. **加载失败**: 如果配置文件损坏，将使用默认配置并输出错误日志
2. **保存失败**: 如果保存失败，将输出错误日志但不影响应用正常运行
3. **首次启动**: 通过 `isFirstLaunch()` 方法检测是否为首次启动

## 使用Timber日志

所有配置操作都会记录详细的日志信息：
```
D/SettingsManager: 设置已保存: AppSettings(zmqIp=192.168.1.100, zmqPort=33445, isRageModeEnabled=false)
D/SettingsManager: 设置已加载: AppSettings(zmqIp=192.168.1.100, zmqPort=33445, isRageModeEnabled=false)  
D/SettingsManager: 机器人模式已保存: 站立模式
D/SettingsManager: 控制模式已保存: 手动模式
```

## 优势

1. **用户体验**: 无需每次启动都重新配置
2. **数据安全**: 使用Android标准的SharedPreferences，数据安全可靠
3. **性能优化**: 实时保存，避免数据丢失  
4. **易于维护**: 清晰的架构设计，便于后续功能扩展