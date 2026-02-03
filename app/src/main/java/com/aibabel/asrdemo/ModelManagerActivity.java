package com.aibabel.asrdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.aibabel.asr.sdk.AsrSdk;
import com.aibabel.asr.sdk.DownloadHandle;
import com.aibabel.asr.sdk.DownloadProgress;
import com.aibabel.asr.sdk.DownloadState;
import com.aibabel.asr.sdk.LocalModelStore;
import com.aibabel.asr.sdk.ModelDownloadCallback;
import com.aibabel.asr.sdk.ModelDownloadRequest;
import com.aibabel.asr.sdk.ModelRegistry;
import com.aibabel.asr.sdk.db.ModelEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 入口 2：模型管理（选择/状态/下载/暂停/继续/删除/进入识别）。
 */
public class ModelManagerActivity extends Activity {

    private DownloadHandle currentDownloadHandle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.model_manager);

        Spinner spModel = findViewById(R.id.sp_model);
        TextView tvModelState = findViewById(R.id.tv_model_state);
        TextView tvStatus = findViewById(R.id.tv_status);

        Button btnRefresh = findViewById(R.id.btn_refresh);
        Button btnDownload = findViewById(R.id.btn_download);
        Button btnPause = findViewById(R.id.btn_pause_download);
        Button btnResume = findViewById(R.id.btn_resume_download);
        Button btnDelete = findViewById(R.id.btn_delete);
        Button btnOpenRecognize = findViewById(R.id.btn_open_recognize);

        btnPause.setEnabled(false);
        btnResume.setEnabled(false);

        List<ModelRegistry.ModelDescriptor> models = ModelRegistry.INSTANCE.list();
        List<String> items = new ArrayList<>();
        for (ModelRegistry.ModelDescriptor m : models) {
            items.add(m.getModelId() + " - " + m.getName());
        }
        spModel.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));

        // restore selection
        String last = DemoPrefs.getSelectedModelId(this);
        int idx = 0;
        if (last != null) {
            for (int i = 0; i < models.size(); i++) {
                if (last.equals(models.get(i).getModelId())) {
                    idx = i;
                    break;
                }
            }
        }
        spModel.setSelection(idx);

        Runnable refreshState = () -> {
            ModelRegistry.ModelDescriptor desc = getSelected(models, spModel.getSelectedItemPosition());
            if (desc == null) {
                tvModelState.setText("未选择模型");
                return;
            }
            DemoPrefs.setSelectedModelId(this, desc.getModelId());

            LocalModelStore.INSTANCE.getWithLocalStateAsync(this, desc.getModelId(), rec -> {
                Integer status = rec != null ? rec.getMergedStatus() : null;
                runOnUiThread(() -> tvModelState.setText(renderModelState(desc, status)));
                return kotlin.Unit.INSTANCE;
            });
        };

        btnRefresh.setOnClickListener(v -> refreshState.run());

        btnDownload.setOnClickListener(v -> {
            ModelRegistry.ModelDescriptor desc = getSelected(models, spModel.getSelectedItemPosition());
            if (desc == null) return;

            File root = AsrSdk.INSTANCE.getConfig() != null ? AsrSdk.INSTANCE.getConfig().getModelRootDir() : null;
            if (root == null) {
                Toast.makeText(this, "请先初始化 SDK", Toast.LENGTH_SHORT).show();
                return;
            }

            LocalModelStore.INSTANCE.getWithLocalStateAsync(this, desc.getModelId(), rec -> {
                boolean installed = rec != null && rec.getMergedStatus() == ModelEntity.STATUS_INSTALLED;
                runOnUiThread(() -> {
                    if (installed) {
                        Toast.makeText(this, "当前模型已下载，无需重复下载", Toast.LENGTH_SHORT).show();
                        refreshState.run();
                        return;
                    }

                    tvStatus.setText("开始下载 " + desc.getName() + "...");
                    currentDownloadHandle = AsrSdk.INSTANCE.downloadModel(
                            new ModelDownloadRequest(desc.getModelId(), desc.getId(), desc.getName(), desc.getUrl(), root, false),
                            new ModelDownloadCallback() {
                                @Override
                                public void onState(@org.jetbrains.annotations.NotNull DownloadState state) {
                                    runOnUiThread(() -> {
                                        tvStatus.setText(state.toString());
                                        if (state instanceof DownloadState.Paused) {
                                            btnPause.setEnabled(false);
                                            btnResume.setEnabled(true);
                                        } else if (state instanceof DownloadState.Downloading || state instanceof DownloadState.Resumed || state instanceof DownloadState.Started) {
                                            btnPause.setEnabled(true);
                                            btnResume.setEnabled(false);
                                        } else if (state instanceof DownloadState.Success || state instanceof DownloadState.Failed || state instanceof DownloadState.Canceled) {
                                            btnPause.setEnabled(false);
                                            btnResume.setEnabled(false);
                                            currentDownloadHandle = null;
                                        }
                                        refreshState.run();
                                    });
                                }

                                @Override
                                public void onProgress(@org.jetbrains.annotations.NotNull DownloadProgress progress) {
                                    runOnUiThread(() -> {
                                        String p = progress.getPercent() != null ? (progress.getPercent() + "%") : (progress.getDone() + "/" + progress.getTotal());
                                        tvStatus.setText(progress.getPhase().name() + ": " + p);
                                    });
                                }
                            }
                    );

                    btnPause.setEnabled(true);
                    btnResume.setEnabled(false);
                });

                return kotlin.Unit.INSTANCE;
            });
        });

        btnPause.setOnClickListener(v -> {
            if (currentDownloadHandle != null) {
                currentDownloadHandle.pause();
            }
        });

        btnResume.setOnClickListener(v -> {
            if (currentDownloadHandle != null) {
                currentDownloadHandle.resume();
            }
        });

        btnDelete.setOnClickListener(v -> {
            ModelRegistry.ModelDescriptor desc = getSelected(models, spModel.getSelectedItemPosition());
            if (desc == null) return;

            currentDownloadHandle = null;
            btnPause.setEnabled(false);
            btnResume.setEnabled(false);

            LocalModelStore.INSTANCE.deleteModelAsync(this, desc.getModelId(), ok -> {
                runOnUiThread(() -> {
                    tvStatus.setText(ok ? ("已删除: " + desc.getModelId()) : ("删除失败: " + desc.getModelId()));
                    refreshState.run();
                });
                return kotlin.Unit.INSTANCE;
            });
        });

        btnOpenRecognize.setOnClickListener(v -> {
            ModelRegistry.ModelDescriptor desc = getSelected(models, spModel.getSelectedItemPosition());
            if (desc == null) return;
            DemoPrefs.setSelectedModelId(this, desc.getModelId());
            Intent it = new Intent(this, RecognizeActivity.class);
            it.putExtra("modelId", desc.getModelId());
            startActivity(it);
        });

        refreshState.run();
    }

    @Nullable
    private static ModelRegistry.ModelDescriptor getSelected(List<ModelRegistry.ModelDescriptor> models, int index) {
        if (models == null || models.isEmpty()) return null;
        if (index < 0 || index >= models.size()) return models.get(0);
        return models.get(index);
    }

    private static String renderModelState(ModelRegistry.ModelDescriptor desc, @Nullable Integer status) {
        String s;
        if (status == null) {
            s = "未知";
        } else if (status == ModelEntity.STATUS_INSTALLED) {
            s = "已下载";
        } else if (status == ModelEntity.STATUS_DOWNLOADING) {
            s = "下载中";
        } else if (status == ModelEntity.STATUS_PAUSED) {
            s = "已暂停";
        } else if (status == ModelEntity.STATUS_FAILED) {
            s = "失败";
        } else {
            s = "未下载";
        }
        return desc.getModelId() + "（" + desc.getName() + "）\n状态: " + s;
    }
}
