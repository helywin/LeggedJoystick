package com.skydroid.rcsdkdemo.other;

import android.text.TextUtils;

/**
 * HomeActivity要显示的接收信息
 * Created by ljb on 2024.06.13.
 */
public class ReceiveInfo {
    private String strDataTransmissionValue = "";
    private String strSignalValue = "";
    private String strH16ChannelsValue = "";
    private String strGetControlMode = "";
    private String strSetControlMode = "";
    private String strChannels = "";

    private String strCameraVersion = "";
    private String strTakePicture = "";
    private String strRecordVideo = "";
    private String strCameraTime = "";
    private String strLEDTime = "";

    private String strOtherValue = "";

    public void cleatInfo() {
        strDataTransmissionValue = "";
        strSignalValue = "";
        strH16ChannelsValue = "";
        strGetControlMode = "";
        strSetControlMode = "";
        strChannels = "";

        strCameraVersion = "";
        strTakePicture = "";
        strRecordVideo = "";
        strCameraTime = "";
        strLEDTime = "";

        strOtherValue = "";
    }

    public String updateInfo(EnumInfoKey key, Object obj) {
        if (obj == null) {
            return null;
        }
        switch (key) {
            case DataTransmission:
                strDataTransmissionValue = AppUtils.getTimeStamp() + obj;
                break;
            case Signal:
                strSignalValue = AppUtils.getTimeStamp() + obj;
                break;
            case H16Channels:
                strH16ChannelsValue = AppUtils.getTimeStamp() + obj;
                break;
            case GetControlMode:
                strGetControlMode = AppUtils.getTimeStamp() + obj;
                break;
            case SetControlMode:
                strSetControlMode = AppUtils.getTimeStamp() + obj;
                break;
            case Channels:
                strChannels = AppUtils.getTimeStamp() + obj;
                break;
            case CameraVersion:
                strCameraVersion = AppUtils.getTimeStamp() + obj;
                break;
            case TakePicture:
                strTakePicture = AppUtils.getTimeStamp() + obj;
                break;
            case RecordVideo:
                strRecordVideo = AppUtils.getTimeStamp() + obj;
                break;
            case CameraTime:
                strCameraTime = AppUtils.getTimeStamp() + obj;
                break;
            case LED:
                strLEDTime = AppUtils.getTimeStamp() + obj;
                break;
            case Other:
                strOtherValue = AppUtils.getTimeStamp() + obj;
                break;
        }
        StringBuffer sb = new StringBuffer();
        if (!TextUtils.isEmpty(strDataTransmissionValue)) {
            sb.append(strDataTransmissionValue).append("\n");
        }
        if (!TextUtils.isEmpty(strSignalValue)) {
            sb.append(strSignalValue).append("\n");
        }
        if (!TextUtils.isEmpty(strH16ChannelsValue)) {
            sb.append(strH16ChannelsValue).append("\n");
        }
        if (!TextUtils.isEmpty(strGetControlMode)) {
            sb.append(strGetControlMode).append("\n");
        }
        if (!TextUtils.isEmpty(strSetControlMode)) {
            sb.append(strSetControlMode).append("\n");
        }
        if (!TextUtils.isEmpty(strChannels)) {
            sb.append(strChannels).append("\n");
        }
        if (!TextUtils.isEmpty(strCameraVersion)) {
            sb.append(strCameraVersion).append("\n");
        }
        if (!TextUtils.isEmpty(strTakePicture)) {
            sb.append(strTakePicture).append("\n");
        }
        if (!TextUtils.isEmpty(strRecordVideo)) {
            sb.append(strRecordVideo).append("\n");
        }
        if (!TextUtils.isEmpty(strCameraTime)) {
            sb.append(strCameraTime).append("\n");
        }
        if (!TextUtils.isEmpty(strLEDTime)) {
            sb.append(strLEDTime).append("\n");
        }
        if (!TextUtils.isEmpty(strOtherValue)) {
            sb.append(strOtherValue).append("\n");
        }
        return sb.toString();
    }
}
