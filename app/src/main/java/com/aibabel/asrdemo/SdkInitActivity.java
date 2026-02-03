package com.aibabel.asrdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.aibabel.asr.sdk.AsrSdk;
import com.aibabel.asr.sdk.AsrSdkConfig;

import java.io.File;

import kotlin.Unit;

/**
 * 入口 1：SDK 初始化（license + SN + setConfig）。
 */
public class SdkInitActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sdk_init);

        EditText etSn = findViewById(R.id.et_sn);
        EditText etLicense = findViewById(R.id.et_license);
        TextView tvStatus = findViewById(R.id.tv_status);

        Button btnInit = findViewById(R.id.btn_init);
        Button btnOpenModels = findViewById(R.id.btn_open_models);
        RadioGroup rgModelSource = findViewById(R.id.rg_model_source);

        btnOpenModels.setEnabled(false);

        // preload from prefs
        String lastSn = DemoPrefs.getSn(this);
        String lastLic = DemoPrefs.getLicense(this);
        if (lastSn != null) etSn.setText(lastSn);
        if (lastLic != null) etLicense.setText(lastLic);

        int perm = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (perm != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        btnInit.setOnClickListener(v -> {
            String sn = etSn.getText() == null ? "" : etSn.getText().toString().trim();
            String lic = etLicense.getText() == null ? "" : etLicense.getText().toString().trim();
            if (sn.isEmpty() || lic.isEmpty()) {
                Toast.makeText(this, "请先填写设备SN与license", Toast.LENGTH_SHORT).show();
                return;
            }
            //1.配置模型存储路径
            File modelRoot = new File(getExternalFilesDir(null), "sdk_models");
            //2.配置模型来源
            /**
             * 模型来源策略：
             * - BUILTIN：仅使用 SDK 内置 ModelRegistry 提供的模型列表和下载地址
             * - REMOTE_ON_INIT：SDK 初始化时拉取远程模型列表并写入数据库（并通过 ModelRegistry 提供给调用者）
             *
             * 如果业务方要自己维护下载地址/列表，可以继续选择 BUILTIN，并直接调用 AsrSdk.downloadModel(request)。
             */
            AsrSdkConfig.ModelSource source = AsrSdkConfig.ModelSource.BUILTIN;
            if (rgModelSource.getCheckedRadioButtonId() == R.id.rb_remote) {
                source = AsrSdkConfig.ModelSource.REMOTE_ON_INIT;
            }

            try {
                AsrSdk.INSTANCE.setConfig(new AsrSdkConfig(modelRoot, source));
            } catch (Throwable ignored) {
            }

            tvStatus.setText("SDK 初始化中...");
            //3.初始化SDK
            AsrSdk.INSTANCE.initialize(this, lic, sn, licenseResult -> {
                runOnUiThread(() -> {
                    if (licenseResult.getOk()) {
                        DemoPrefs.saveDevice(this, sn, lic);
                        tvStatus.setText("SDK 初始化成功");
                        btnOpenModels.setEnabled(true);
                    } else {
                        String msg = licenseResult.getErrorMessage() != null ? licenseResult.getErrorMessage() : "unknown";
                        tvStatus.setText("SDK 初始化失败: " + msg);
                        btnOpenModels.setEnabled(false);
                    }
                });
                return Unit.INSTANCE;
            });
        });

        btnOpenModels.setOnClickListener(v -> {
            startActivity(new Intent(this, ModelManagerActivity.class));
        });
    }
}
