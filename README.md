# ASR SDK Demo 说明文档

## 目录
1. [Demo 功能概览](#1-demo-功能概览)
2. [集成指南](#2-集成指南)
3. [调用流程说明](#3-调用流程说明)
4. [关键 API 说明](#4-关键-api-说明)
5. [常见问题与注意事项](#5-常见问题与注意事项)
6. [错误码对照说明](#6-错误码对照说明)

## 1. Demo 功能概览

Demo App 主要由三个 Activity 组成，分别演示了 SDK 集成的三个关键步骤：

1.  **SDK 初始化 (`SdkInitActivity`)**:
    *   设置 License（由分音塔提供） 和设备 SN。
    *   配置模型下载方式及存储路径。
    *   执行 SDK 初始化。

2.  **模型管理 (`ModelManagerActivity`)**:
    *   展示可用模型列表。
    *   下载、暂停、恢复、删除模型。
    *   查看模型本地状态（未下载、已安装等）。

3.  **语音识别 (`RecognizeActivity`)**:
    *   加载指定模型。
    *   进行实时麦克风语音识别。
    *   进行音频文件流识别。
    *   展示实时识别结果（部分结果和最终结果）。

## 2. 集成指南

### 2.1 引入 SDK (AAR)

将 `asr_module-release-v1.1.aar` 放入应用模块的 `libs` 目录中。

在应用模块的 `build.gradle` (或 `build.gradle.kts`) 中添加本地库仓库支持：

```kotlin
// build.gradle.kts
repositories {
    google()
    mavenCentral()
    flatDir {
        dirs("libs")
    }
}
```

### 2.2 配置依赖

在 `dependencies` 块中引入 AAR 及其所需的第三方依赖库。SDK 内部使用了 Room、OkHttp 等库，需要宿主应用显式依赖（如果未通过 Maven 自动传递）。

```kotlin
dependencies {
    // 1. 引入 ASR SDK AAR
    implementation(name: 'asr_module-release-v1.1', ext: 'aar')

    // 2. 必须的基础依赖 (SDK 内部依赖)
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar") // 核心引擎依赖
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")       // 网络请求
    implementation("com.liulishuo.okdownload:okdownload:1.0.7")// 断点续传

    // 3. Room 数据库依赖 (用于模型管理)
    val roomVersion = "2.7.0" // 或其他兼容版本
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // 如果宿主也是 Kotlin 项目，可能需要 kapt 或 ksp 处理 room 注解
    // kapt("androidx.room:room-compiler:$roomVersion")

    // 4. 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

### 2.3 NDK 配置

SDK 包含原生动态库 (`.so`)，目前支持 `armeabi-v7a` 和 `arm64-v8a` 架构。建议在 `build.gradle` 中进行过滤，以避免由其他依赖引入不支持的架构导致崩溃。

```kotlin
android {
    // ...
    defaultConfig {
        // ...
        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a")
        }
    }
}
```

### 2.4 配置权限

在 `AndroidManifest.xml` 中添加从必要的权限：

```xml
<manifest ...>
    <!-- 必须：联网权限 (用于 License 验证和模型下载) -->
    <uses-permission android:name="android.permission.INTERNET" />
    
    <!-- 必须：录音权限 (用于麦克风识别) -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    
    <!-- 可选：外部存储读写 (如果模型存储在外部存储，Android 10+ 建议使用 Context.getExternalFilesDir(null)) -->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
</manifest>
```

注意：`RECORD_AUDIO` 和存储权限属于运行时权限，需要在代码中动态申请（参考 Demo 的 `SdkInitActivity`）。

## 3. 调用流程说明

### 步骤 1: SDK 初始化

在应用启动时（`SdkInitActivity`），需要进行一次全局初始化。

```java
// 1. 配置参数
File modelRoot = new File(getExternalFilesDir(null), "sdk_models");

/**
 * 模型来源策略配置 (ModelSource)：
 * - BUILTIN: 仅使用 SDK 内置提供的模型列表。
 * - REMOTE_ON_INIT: SDK 初始化时尝试拉取远程模型列表。
 * 
 * 注意：如果业务方希望自己完全维护模型下载地址和列表，可以选择 BUILTIN，
 * 然后跳过 ModelRegistry，直接构造 ModelDownloadRequest 并调用 AsrSdk.downloadModel(request) 即可。
 */
AsrSdkConfig config = new AsrSdkConfig(modelRoot, AsrSdkConfig.ModelSource.BUILTIN);
AsrSdk.INSTANCE.setConfig(config);

// 2. 初始化 (需传入 Context, License, DeviceSN)
AsrSdk.INSTANCE.initialize(this, "YOUR_LICENSE", "DEVICE_SN", result -> {
    if (result.getOk()) {
        // 初始化成功
    } else {
        // 初始化失败: result.getErrorMessage()
    }
    return Unit.INSTANCE;
});
```

### 步骤 2: 模型管理与下载

在进行识别前，必须确保所需的模型文件已下载到本地（`ModelManagerActivity`）。

```java
// 获取模型列表
List<ModelRegistry.ModelDescriptor> models = ModelRegistry.INSTANCE.list();

// 检查模型状态
LocalModelStore.INSTANCE.getWithLocalStateAsync(context, "model-id", record -> {
    if (record != null && record.getMergedStatus() == ModelEntity.STATUS_INSTALLED) {
        // 已安装
    }
    return Unit.INSTANCE;
});

// 下载模型
ModelDownloadRequest req = new ModelDownloadRequest("model-id", 101, "Name", "Url", saveDir, false);
DownloadHandle handle = AsrSdk.INSTANCE.downloadModel(req, new ModelDownloadCallback() {
    @Override
    public void onProgress(DownloadProgress progress) {
        // 更新进度
    }
    @Override
    public void onState(DownloadState state) {
        // 处理状态: Downloading, Success, Failed 等
    }
});
```

### 步骤 3: 启动识别

模型准备好后，可以创建引擎并开始识别（`RecognizeActivity`）。

```java
// 1. 创建引擎实例
AsrEngine engine = AsrSdk.INSTANCE.newEngine();

// 2. 加载模型 (异步)
AsrSdk.INSTANCE.initModel(engine, "model-id", "Model Name", result -> {
    if (result instanceof ModelInitResult.Ready) {
        // 模型已加载，可以开始识别
    } else if (result instanceof ModelInitResult.NeedDownload) {
        // 模型未下载
    }
    return Unit.INSTANCE;
});

// 3. 实现回调接口
AsrListener listener = new AsrListener() {
    @Override
    public void onPartialResult(String hypothesis) { /* 实时临时结果 */ }
    @Override
    public void onResult(String hypothesis) { /* 句级结果 */ }
    @Override
    public void onFinalResult(String hypothesis) { /* 最终结果 */ }
    @Override
    public void onError(AsrError error) { /* 错误处理 */ }
    @Override
    public void onTimeout() { /* 超时 */ }
};

// 4.a 启动麦克风识别
boolean started = engine.startMic(listener, true); // true 表示启用词级时间戳

// 4.b 或者启动文件流识别
FileInputStream fis = new FileInputStream(audioFile);
// skipWavHeaderBytes: 如果是 wav 文件通常跳过 44 字节
engine.startStream(fis, listener, 44, null, true);

// 5. 停止或释放
engine.stopMic(); // 停止录音
engine.shutdown(); // 销毁引擎释放资源
```

## 4. 关键 API 说明

### `AsrSdk` (单例)
*   `setConfig(AsrSdkConfig)`: 设置 SDK 全局配置，如模型下载路径。
*   `initialize(Context, license, sn, callback)`: 初始化 SDK，验证 License。
*   `newEngine()`: 创建一个新的识别引擎实例。
*   `downloadModel(ModelDownloadRequest, callback)`: 启动模型下载任务。
    *   *高级用法*：如果自定义模型源，可直接构造 `ModelDownloadRequest` 传入下载 URL 和目标路径。
*   `initModel(...)`: 方便方法，检查模型状态并加载到引擎中。

### `AsrEngine`
*   `startMic(listener, ...)`: 开启麦克风识别线程。
*   `stopMic()`: 停止麦克风录音。
*   `pauseMic(boolean)`: 暂停/恢复识别过程。
*   `startStream(inputStream, ...)`: 对输入流进行识别。
*   `stopStream()`: 停止流式识别。
*   `shutdown()`: 释放引擎所有资源，必须调用。

### `AsrListener`
*   识别结果的回调接口，主要包含 `onPartialResult` (流式部分结果) 和 `onResult` (稳定结果)。返回的数据通常是 JSON 格式字符串。

### `ModelRegistry` & `LocalModelStore`
*   `ModelRegistry.INSTANCE.list()`: 获取所有可用模型信息的列表。
*   `LocalModelStore`: 用于查询本地数据库中模型的下载和安装状态。

## 5. 常见问题与注意事项

- 权限：需要 `RECORD_AUDIO`。Android 6.0+ 需要在运行时请求权限。`WRITE_EXTERNAL_STORAGE` 在新 Android 版本上可能无需或受限制，示例中仅在清单声明。
- 初始化失败：检查 license 与 SN 是否正确；若使用 `REMOTE_ON_INIT` 模式，初始化时会尝试获取远端模型列表并写入数据库，确保设备能访问网络或选择 `BUILTIN` 策略并手动下载。
- 模型存储空间：下载模型可能比较大，请确保外部存储有足够空间。
- WAV 文件识别：示例代码会跳过 44 字节头（典型 PCM WAV），其他格式（编码、采样率）需确保与模型/引擎兼容。
- AAR 库：`app/libs/asr_module-release-v1.1.aar` 包含 SDK 的实现。

## 6. 错误码对照说明

SDK 使用 `AsrError` 类抛出错误，其中 `code` 字段对应 `AsrErrorCode` 枚举。

| 错误码 (Value) | 枚举名称 | 说明 | 建议处理方式 |
| :--- | :--- | :--- | :--- |
| **10xx** | **模型/初始化相关** | | |
| 1001 | `MODEL_NOT_INITIALIZED` | 模型未初始化 | 请确保在 startMic/startStream 前调用了 initModel 且返回 Ready。 |
| 1002 | `MODEL_NOT_PREPARED` | 模型未准备好 | 模型文件可能缺失或损坏，请重新下载或初始化。 |
| 1003 | `MODEL_DIR_NOT_FOUND` | 模型目录不存在 | 指向的模型路径无效。 |
| **11xx** | **运行状态冲突** | | |
| 1101 | `MIC_ALREADY_RUNNING` | 麦克风识别已在运行 | 调用 startMic 前请先 stopMic。 |
| 1102 | `STREAM_ALREADY_RUNNING`| 输入流识别已在运行 | 调用 startStream 前请先 stopStream。 |
| **12xx** | **音频设备相关** | | |
| 1201 | `MIC_START_FAILED` | 启动麦克风失败 | 检查 RECORD_AUDIO 权限，或麦克风是否被其他应用独占。 |
| 1202 | `AUDIO_RECORD_FAILED` | 录音过程中出错 | 硬件读取失败，建议重试或检查设备状态。 |
| **13xx** | **输入流相关** | | |
| 1301 | `INPUT_STREAM_TOO_SHORT`| 输入流数据过短 | 校验输入文件或流的有效性。 |
| 1302 | `STREAM_START_FAILED` | 启动流识别失败 | 流读取异常。 |
| **14xx** | **资源读取** | | |
| 1401 | `ASSET_OPEN_FAILED` | 读取 Assets 失败 | 检查 APK 内部资源文件。 |
| **15xx** | **内部/系统** | | |
| 1501 | `INTERNAL_IO` | 内部 I/O 错误 | 文件读写异常，检查存储空间和权限。 |
| 1502 | `SHUTDOWN_FAILED` | 释放资源失败 | 通常不影响主要流程，只在日志体现。 |
| **1999** | **兜底** | | |
| 1999 | `UNKNOWN` | 未知错误 | 查看 `message` 和 `cause` 获取详细堆栈信息。 |


---

## 附录：主要文件位置索引

- 应用清单: `app/src/main/AndroidManifest.xml`
- 初始化界面: `app/src/main/java/com/aibabel/asrdemo/SdkInitActivity.java`
- 模型管理: `app/src/main/java/com/aibabel/asrdemo/ModelManagerActivity.java`
- 识别界面: `app/src/main/java/com/aibabel/asrdemo/RecognizeActivity.java`
- 结果解析: `app/src/main/java/com/aibabel/asrdemo/AsrResultParser.java`
- 偏好存储: `app/src/main/java/com/aibabel/asrdemo/DemoPrefs.java`
- 资源字符串: `app/src/main/res/values/strings.xml`
- 布局: `app/src/main/res/layout/sdk_init.xml`, `recognize.xml`, `model_manager.xml`
- SDK AAR: `app/libs/asr_module-release-v1.1.aar`


---

# 结束