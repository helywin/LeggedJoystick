## 1. 规格与构建身份

- [x] 1.1 定义两个 flavor、独立 applicationId 和功能边界
- [x] 1.2 增加 flavor、应用名与 APK 命名配置

## 2. 协议与产品策略

- [x] 2.1 同步 driver 新协议并增加产品身份和准入状态
- [x] 2.2 实现集中式 `RemoteProductPolicy`
- [x] 2.3 让 general 版只创建驱动连接，让 SAR 版保留驱动与 NX 双连接

## 3. 模式与界面

- [x] 3.1 general 主屏增加 AUTO/MANUAL 切换并完成权威状态闭环
- [x] 3.2 general 隐藏并停用所有建图导航业务
- [x] 3.3 SAR 隐藏模式切换并删除直接 `SET_APP_MODE` 路径

## 4. 验证

- [x] 4.1 增加产品策略、准入、应用身份和 UI 架构测试
- [x] 4.2 分别运行 general 与 SAR 单元测试和 APK 构建
- [x] 4.3 运行 OpenSpec 严格校验和协议 CRC 测试
- [x] 4.4 记录未做遥控器真机安装与机器狗联调的边界
