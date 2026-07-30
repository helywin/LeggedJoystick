package com.skydroid.rcsdkdemo.other;


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;

import com.skydroid.rcsdk.common.error.SkyException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 工具类
 * Created by ljb on 2024.06.13.
 */
public class AppUtils {
    public static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("HH:mm:ss.SSS:", Locale.US);

    public static String getTimeStamp() {
        return timestampFormatter.format(new Date());
    }

    public static String getSkyExceptionInfo(String cmd, SkyException e, String version) {
        return cmd + (e == null ? "成功" : ("失败：" + e.getMessage())) + "--" + version;
    }

    public static void showC10pCameraControlDialog(Context context, DialogInterface.OnClickListener listener) {
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);
        final String[] list = {"相机版本", "一键向下", "一键居中", "一键向上", "拍照", "开始录像", "停止录像", "时间设置", "右(航向)", "左(航向)", "上(俯仰)", "下(俯仰)"};
        arrayAdapter.addAll(list);
        new AlertDialog.Builder(context)
                .setAdapter(arrayAdapter, listener)
                .create().show();
    }

    public static void showLEDCameraControlDialog(Context context, DialogInterface.OnClickListener listener) {
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);
        final String[] list = {"LED打开", "LED关闭",};
        arrayAdapter.addAll(list);
        new AlertDialog.Builder(context)
                .setAdapter(arrayAdapter, listener)
                .create().show();
    }

}
