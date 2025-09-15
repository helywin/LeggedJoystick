/*********************************************************************************
 * FileName: HighLevelZmqClient.cpp
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-08
 * Description: HighLevel ZMQ服务的客户端实现
 * Others:
*********************************************************************************/

#include "HighLevelZmqClient.hpp"
#include <chrono>
#include "utilities/Logger.hpp"

HighLevelZmqClient::HighLevelZmqClient(ClientType client_type, const std::string &tcp_endpoint):
    context_(1),
    socket_(context_, ZMQ_DEALER),  // 改为DEALER以配合服务端的ROUTER
    tcp_endpoint_(tcp_endpoint),
    connected_(false),
    client_type_(client_type)
{
    // 为DEALER socket设置一个唯一的字符串identity
    std::string client_type_str = (client_type_ == ClientType::REMOTE_CONTROLLER) ? "rc" : "nav";
    std::string identity = client_type_str + "_" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count());
    socket_.setsockopt(ZMQ_IDENTITY, identity.c_str(), identity.size());
}

HighLevelZmqClient::~HighLevelZmqClient()
{
    disconnect();
}

bool HighLevelZmqClient::connect()
{
    if (connected_) {
        return true;
    }

    try {
        socket_.connect(tcp_endpoint_);
        connected_ = true;

        // 发送注册信息，告知服务端客户端类型
        json register_request;
        register_request["command"] = "register";
        register_request["params"]["client_type"] = (client_type_ == ClientType::REMOTE_CONTROLLER) ? "remote_controller" : "navigation";
        
        json response = sendRequest(register_request);
        if (!response.value("success", false)) {
            LOGE("[HighLevelZmqClient] 客户端注册失败: {}", response.value("message", "unknown error"));
            disconnect();
            return false;
        }

        LOGI("[HighLevelZmqClient] 连接到服务端: {}", tcp_endpoint_);
        return true;
    } catch (const zmq::error_t &e) {
        LOGE("[HighLevelZmqClient] 连接失败: {}", e.what());
        connected_ = false;
        return false;
    }
}

void HighLevelZmqClient::disconnect()
{
    if (connected_) {
        connected_ = false;
        LOGI("[HighLevelZmqClient] 已断开连接");
    }
}

bool HighLevelZmqClient::sendHeartbeat()
{
    json request;
    request["command"] = "heartbeat";

    json response = sendRequest(request);
    return response.value("success", false);
}

bool HighLevelZmqClient::setMode(const std::string& mode)
{
    if (client_type_ != ClientType::REMOTE_CONTROLLER) {
        LOGE("[HighLevelZmqClient] 只有遥控器客户端可以设置模式");
        return false;
    }

    json request;
    request["command"] = "setMode";
    request["params"]["mode"] = mode;

    json response = sendRequest(request);
    return response.value("success", false);
}

std::string HighLevelZmqClient::getCurrentMode()
{
    json request;
    request["command"] = "getCurrentMode";

    json response = sendRequest(request);
    return response.value("mode", "auto");
}

json HighLevelZmqClient::sendRequest(const json &request)
{
    if (!connected_) {
        json error_response;
        error_response["success"] = false;
        error_response["message"] = "Not connected to service";
        return error_response;
    }

    try {
        // 发送请求
        std::string request_str = request.dump();
        zmq::message_t req_msg(request_str.size());
        memcpy(req_msg.data(), request_str.c_str(), request_str.size());
        socket_.send(req_msg);

        // 接收响应
        zmq::message_t reply_msg;
        if (socket_.recv(&reply_msg)) {
            std::string reply_str(static_cast<char *>(reply_msg.data()), reply_msg.size());
            return json::parse(reply_str);
        }
    } catch (const std::exception &e) {
        LOGE("[HighLevelZmqClient] 请求失败: {}", e.what());
    }

    json error_response;
    error_response["success"] = false;
    error_response["message"] = "Request failed";
    return error_response;
}

// HighLevel接口封装实现 - 基础控制
bool HighLevelZmqClient::initRobot(const std::string &local_ip, int local_port, const std::string &dog_ip)
{
    json request;
    request["command"] = "initRobot";
    request["params"]["local_ip"] = local_ip;
    request["params"]["local_port"] = local_port;
    request["params"]["dog_ip"] = dog_ip;

    json response = sendRequest(request);
    return response.value("success", false);
}

bool HighLevelZmqClient::deinitRobot()
{
    json request;
    request["command"] = "deinitRobot";

    json response = sendRequest(request);
    return response.value("success", false);
}

bool HighLevelZmqClient::checkConnect()
{
    json request;
    request["command"] = "checkConnect";

    json response = sendRequest(request);
    return response.value("connected", false);
}

uint32_t HighLevelZmqClient::standUp()
{
    json request;
    request["command"] = "standUp";

    json response = sendRequest(request);
    return response.value("result", 0);
}

uint32_t HighLevelZmqClient::lieDown()
{
    json request;
    request["command"] = "lieDown";

    json response = sendRequest(request);
    return response.value("result", 0);
}

uint32_t HighLevelZmqClient::passive()
{
    json request;
    request["command"] = "passive";

    json response = sendRequest(request);
    return response.value("result", 0);
}

uint32_t HighLevelZmqClient::move(float vx, float vy, float yaw_rate)
{
    json request;
    request["command"] = "move";
    request["params"]["vx"] = vx;
    request["params"]["vy"] = vy;
    request["params"]["yaw_rate"] = yaw_rate;

    json response = sendRequest(request);
    return response.value("result", 0);
}

uint32_t HighLevelZmqClient::jump()
{
    json request;
    request["command"] = "jump";

    json response = sendRequest(request);
    return response.value("result", 0);
}

uint32_t HighLevelZmqClient::frontJump()
{
    json request;
    request["command"] = "frontJump";

    json response = sendRequest(request);
    return response.value("result", 0);
}

uint32_t HighLevelZmqClient::backflip()
{
    json request;
    request["command"] = "backflip";

    json response = sendRequest(request);
    return response.value("result", 0);
}

uint32_t HighLevelZmqClient::attitudeControl(float roll_vel, float pitch_vel, float yaw_vel, float height_vel)
{
    json request;
    request["command"] = "attitudeControl";
    request["params"]["roll_vel"] = roll_vel;
    request["params"]["pitch_vel"] = pitch_vel;
    request["params"]["yaw_vel"] = yaw_vel;
    request["params"]["height_vel"] = height_vel;

    json response = sendRequest(request);
    return response.value("result", 0);
}

// 新增高级动作
uint32_t HighLevelZmqClient::shakeHand()
{
    json request;
    request["command"] = "shakeHand";

    json response = sendRequest(request);
    return response.value("result", 0);
}

uint32_t HighLevelZmqClient::twoLegStand(float vx, float yaw_rate)
{
    json request;
    request["command"] = "twoLegStand";
    request["params"]["vx"] = vx;
    request["params"]["yaw_rate"] = yaw_rate;

    json response = sendRequest(request);
    return response.value("result", 0);
}

void HighLevelZmqClient::cancelTwoLegStand()
{
    json request;
    request["command"] = "cancelTwoLegStand";

    sendRequest(request);
}

// 新SDK传感器数据查询 - 返回完整vector
std::vector<float> HighLevelZmqClient::getQuaternion()
{
    json request;
    request["command"] = "getQuaternion";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 1.0f}; // 默认四元数
}

std::vector<float> HighLevelZmqClient::getRPY()
{
    json request;
    request["command"] = "getRPY";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f}; // 默认RPY
}

std::vector<float> HighLevelZmqClient::getBodyAcc()
{
    json request;
    request["command"] = "getBodyAcc";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f}; // 默认加速度
}

std::vector<float> HighLevelZmqClient::getBodyGyro()
{
    json request;
    request["command"] = "getBodyGyro";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f}; // 默认角速度
}

std::vector<float> HighLevelZmqClient::getPosition()
{
    json request;
    request["command"] = "getPosition";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f}; // 默认位置
}

std::vector<float> HighLevelZmqClient::getWorldVelocity()
{
    json request;
    request["command"] = "getWorldVelocity";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f}; // 默认世界速度
}

std::vector<float> HighLevelZmqClient::getBodyVelocity()
{
    json request;
    request["command"] = "getBodyVelocity";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f}; // 默认机身速度
}

// 关节数据查询
std::vector<float> HighLevelZmqClient::getLegAbadJoint()
{
    json request;
    request["command"] = "getLegAbadJoint";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

std::vector<float> HighLevelZmqClient::getLegHipJoint()
{
    json request;
    request["command"] = "getLegHipJoint";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

std::vector<float> HighLevelZmqClient::getLegKneeJoint()
{
    json request;
    request["command"] = "getLegKneeJoint";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

std::vector<float> HighLevelZmqClient::getLegAbadJointVel()
{
    json request;
    request["command"] = "getLegAbadJointVel";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

std::vector<float> HighLevelZmqClient::getLegHipJointVel()
{
    json request;
    request["command"] = "getLegHipJointVel";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

std::vector<float> HighLevelZmqClient::getLegKneeJointVel()
{
    json request;
    request["command"] = "getLegKneeJointVel";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

std::vector<float> HighLevelZmqClient::getLegAbadJointTorque()
{
    json request;
    request["command"] = "getLegAbadJointTorque";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

std::vector<float> HighLevelZmqClient::getLegHipJointTorque()
{
    json request;
    request["command"] = "getLegHipJointTorque";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

std::vector<float> HighLevelZmqClient::getLegKneeJointTorque()
{
    json request;
    request["command"] = "getLegKneeJointTorque";

    json response = sendRequest(request);
    if (response.contains("values")) {
        return response["values"].get<std::vector<float>>();
    }
    return std::vector<float>{0.0f, 0.0f, 0.0f, 0.0f}; // 4条腿默认值
}

// 状态查询
uint32_t HighLevelZmqClient::getCurrentCtrlmode()
{
    json request;
    request["command"] = "getCurrentCtrlmode";

    json response = sendRequest(request);
    return response.value("value", 0);
}


uint32_t HighLevelZmqClient::getBatteryPower()
{
    json request;
    request["command"] = "getBatteryPower";

    json response = sendRequest(request);
    return response.value("value", 0);
}
