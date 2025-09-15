/*********************************************************************************
 * FileName: HighLevelZmqClient.hpp
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-08
 * Description: HighLevel ZMQ服务的客户端封装
 * Others:
*********************************************************************************/

#pragma once

#include <zmq.hpp>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <chrono>
#include <nlohmann/json.hpp>
#include "utilities/Logger.hpp"

using json = nlohmann::json;

enum class ClientType {
    REMOTE_CONTROLLER,  // 遥控器
    NAVIGATION         // 导航程序
};

class HighLevelZmqClient
{
private:
    static constexpr const char *DEFAULT_TCP_ENDPOINT = "tcp://127.0.0.1:33445";

    zmq::context_t context_;
    zmq::socket_t socket_;
    std::string tcp_endpoint_;
    bool connected_;
    ClientType client_type_;

    json sendRequest(const json &request);

public:
    explicit HighLevelZmqClient(ClientType client_type, const std::string &tcp_endpoint = DEFAULT_TCP_ENDPOINT);
    ~HighLevelZmqClient();

    bool connect();
    void disconnect();
    bool isConnected() const { return connected_; }
    ClientType getClientType() const { return client_type_; }

    // 心跳和连接管理
    bool sendHeartbeat();

    // 模式管理 - 只有遥控器客户端可以调用
    bool setMode(const std::string& mode);  // "auto" 或 "manual"
    std::string getCurrentMode();

    // HighLevel接口封装 - 基础控制
    bool initRobot(const std::string &local_ip, int local_port, const std::string &dog_ip = "192.168.234.1");
    bool deinitRobot();
    bool checkConnect();
    uint32_t standUp();
    uint32_t lieDown();
    uint32_t passive();
    uint32_t move(float vx, float vy, float yaw_rate);
    uint32_t jump();
    uint32_t frontJump();
    uint32_t backflip();
    uint32_t attitudeControl(float roll_vel, float pitch_vel, float yaw_vel, float height_vel);

    // 新增高级动作
    uint32_t shakeHand();
    uint32_t twoLegStand(float vx = 0.0f, float yaw_rate = 0.0f);
    void cancelTwoLegStand();

    // 新SDK传感器数据查询 - 返回完整vector
    std::vector<float> getQuaternion();
    std::vector<float> getRPY();
    std::vector<float> getBodyAcc();
    std::vector<float> getBodyGyro();
    std::vector<float> getPosition();
    std::vector<float> getWorldVelocity();
    std::vector<float> getBodyVelocity();

    // 关节数据查询
    std::vector<float> getLegAbadJoint();
    std::vector<float> getLegHipJoint();
    std::vector<float> getLegKneeJoint();
    std::vector<float> getLegAbadJointVel();
    std::vector<float> getLegHipJointVel();
    std::vector<float> getLegKneeJointVel();
    std::vector<float> getLegAbadJointTorque();
    std::vector<float> getLegHipJointTorque();
    std::vector<float> getLegKneeJointTorque();

    // 状态查询
    uint32_t getCurrentCtrlmode();
    uint32_t getBatteryPower();
};
