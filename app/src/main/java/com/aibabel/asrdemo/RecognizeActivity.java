package com.aibabel.asrdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.aibabel.asr.AsrEngine;
import com.aibabel.asr.AsrListener;
import com.aibabel.asr.sdk.AsrSdk;
import com.aibabel.asr.sdk.ModelRegistry;
import com.aibabel.asr.sdk.model.ModelInitResult;

import java.io.File;
import java.io.FileInputStream;

import kotlin.Unit;

/**
 * 入口 3：识别页面（初始化模型 + 麦克风/文件识别 + 输出）。
 */
public class RecognizeActivity extends Activity implements AsrListener {

    private static final int REQ_PICK_AUDIO = 1001;

    private AsrEngine engine;
    private boolean isModelReady = false;
    private boolean isMicRunning = false;

    private TextView tvStatus;
    private TextView tvPartial;
    private TextView tvResult;

    private String currentModelId;
    private String selectedAudioPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recognize);

        tvStatus = findViewById(R.id.tv_status);
        tvPartial = findViewById(R.id.tv_partial);
        tvResult = findViewById(R.id.tv_result);

        Button btnInitModel = findViewById(R.id.btn_init_model);
        Button btnMic = findViewById(R.id.btn_mic);
        ToggleButton togglePause = findViewById(R.id.toggle_pause);
        Button btnPickFile = findViewById(R.id.btn_pick_file);
        Button btnStartFile = findViewById(R.id.btn_start_file);
        Button btnStopFile = findViewById(R.id.btn_stop_file);

        btnMic.setEnabled(false);
        togglePause.setEnabled(false);
        btnStartFile.setEnabled(false);
        btnStopFile.setEnabled(false);

        int perm = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (perm != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        currentModelId = getIntent().getStringExtra("modelId");
        if (currentModelId == null) {
            currentModelId = DemoPrefs.getSelectedModelId(this);
        }
        if (currentModelId == null) {
            currentModelId = "ca";
        }

        try {
            engine = AsrSdk.INSTANCE.newEngine();
        } catch (Exception e) {
            tvStatus.setText("请先初始化 SDK");
            engine = null;
        }

        btnInitModel.setOnClickListener(v -> {
            if (engine == null) {
                Toast.makeText(this, "请先初始化 SDK", Toast.LENGTH_SHORT).show();
                return;
            }
            ModelRegistry.ModelDescriptor desc = ModelRegistry.INSTANCE.get(currentModelId);
            if (desc == null) {
                Toast.makeText(this, "模型未注册: " + currentModelId, Toast.LENGTH_SHORT).show();
                return;
            }

            tvStatus.setText("初始化模型...");
            AsrSdk.INSTANCE.initModel(engine, desc.getModelId(), desc.getName(), res -> {
                runOnUiThread(() -> {
                    if (res instanceof ModelInitResult.Ready) {
                        isModelReady = true;
                        tvStatus.setText("模型就绪");
                        btnMic.setEnabled(true);
                        btnPickFile.setEnabled(true);
                        btnStartFile.setEnabled(selectedAudioPath != null);
                    } else if (res instanceof ModelInitResult.NeedDownload) {
                        isModelReady = false;
                        tvStatus.setText(((ModelInitResult.NeedDownload) res).getMessage());
                    } else if (res instanceof ModelInitResult.Error) {
                        isModelReady = false;
                        Throwable err = ((ModelInitResult.Error) res).getError();
                        tvStatus.setText("初始化失败: " + (err != null ? err.getMessage() : "unknown"));
                    }
                });
                return Unit.INSTANCE;
            });
        });

        togglePause.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (engine != null) {
                engine.pauseMic(isChecked);
            }
            tvStatus.setText(isChecked ? "已暂停" : "识别中");
        });

        btnMic.setOnClickListener(v -> {
            if (engine == null || !isModelReady) return;

            if (isMicRunning) {
                engine.stopMic();
                isMicRunning = false;
                togglePause.setChecked(false);
                togglePause.setEnabled(false);
                btnMic.setText(R.string.btn_start_mic);
                tvStatus.setText("已停止");
                return;
            }

            // mic and file are mutually exclusive
            try {
                engine.stopStream();
            } catch (Exception ignored) {
            }

            tvPartial.setText("");
            tvStatus.setText("麦克风识别中...");

            engine.stopMic();
            boolean started = engine.startMic(this, true);
            isMicRunning = started;
            togglePause.setEnabled(started);
            togglePause.setChecked(false);
            btnMic.setText(started ? R.string.btn_stop_mic : R.string.btn_start_mic);
        });

        btnPickFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_PICK_AUDIO);
        });

        btnStartFile.setOnClickListener(v -> {
            if (engine == null || !isModelReady) return;
            if (selectedAudioPath == null) {
                Toast.makeText(this, "请先选择音频文件", Toast.LENGTH_SHORT).show();
                return;
            }

            // stop mic
            try {
                if (isMicRunning) {
                    engine.stopMic();
                    isMicRunning = false;
                    togglePause.setChecked(false);
                    togglePause.setEnabled(false);
                    btnMic.setText(R.string.btn_start_mic);
                }
            } catch (Exception ignored) {
            }

            if (engine.isStreamRunning()) {
                Toast.makeText(this, "文件识别已在进行中", Toast.LENGTH_SHORT).show();
                return;
            }

            FileInputStream input;
            try {
                input = new FileInputStream(selectedAudioPath);
            } catch (Exception e) {
                tvStatus.setText("打开文件失败: " + e.getMessage());
                return;
            }

            tvPartial.setText("");
            tvStatus.setText("文件识别中...");

            long skip = selectedAudioPath.toLowerCase().endsWith(".wav") ? 44L : 0L;
            boolean started = engine.startStream(input, this, skip, null, true);
            btnStopFile.setEnabled(started);
        });

        btnStopFile.setOnClickListener(v -> {
            if (engine == null) return;
            try {
                engine.stopStream();
            } catch (Exception ignored) {
            }
            tvStatus.setText("已停止文件识别");
            btnStopFile.setEnabled(false);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (engine != null) {
                engine.stopMic();
                engine.stopStream();
                engine.shutdown();
            }
        } catch (Exception ignored) {
        }
        engine = null;
    }

    @Deprecated
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_AUDIO || resultCode != RESULT_OK) return;

        Uri uri = data != null ? data.getData() : null;
        if (uri == null) return;

        // copy to private file
        File out = new File(getExternalFilesDir(null), "picked_audio");
        out.mkdirs();
        File outFile = new File(out, "audio_" + System.currentTimeMillis());

        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.OutputStream os = new java.io.FileOutputStream(outFile)) {
            if (in == null) return;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        } catch (Exception ignored) {
            Toast.makeText(this, "拷贝文件失败", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedAudioPath = outFile.getAbsolutePath();
        Toast.makeText(this, "已选择文件", Toast.LENGTH_SHORT).show();
        Button btnStartFile = findViewById(R.id.btn_start_file);
        btnStartFile.setEnabled(true);
    }

    @Override
    public void onPartialResult(@org.jetbrains.annotations.NotNull String hypothesis) {
        runOnUiThread(() -> tvPartial.setText(AsrResultParser.parsePartial(hypothesis)));
    }

    @Override
    public void onResult(@org.jetbrains.annotations.NotNull String hypothesis) {
        runOnUiThread(() -> {
            String text = AsrResultParser.parseText(hypothesis);
            if (!text.isEmpty()) {
                tvResult.append(text + "\n");
            }
        });
    }

    @Override
    public void onFinalResult(@org.jetbrains.annotations.NotNull String hypothesis) {
        runOnUiThread(() -> {
            String text = AsrResultParser.parseText(hypothesis);
            if (!text.isEmpty()) {
                tvResult.append(text + "\n");
            }
            tvPartial.setText("");
        });
    }

    @Override
    public void onError(@org.jetbrains.annotations.NotNull Exception e) {
        runOnUiThread(() -> tvStatus.setText("识别错误: " + (e.getMessage() != null ? e.getMessage() : "unknown")));
    }

    @Override
    public void onTimeout() {
        runOnUiThread(() -> tvStatus.setText("识别超时"));
    }
}
