# 到账语音工坊（首版 P0）

纯本地、离线的“收款风格”语音播报工具：手动输入金额，立即播报、定点播报或间隔循环播报，可导出 WAV。
每条播报前带本应用专属提示音，不使用任何支付平台的官方原声。

## 两个版本

| 版本 | 语音来源 | 安装包 | 适合 |
| --- | --- | --- | --- |
| 标准版（lite） | 手机系统语音引擎 | 约 10 MB | 想要小体积 |
| 音色版（full） | 内置 Kokoro v1.1-zh，100 个中文音色（女声 55 / 男声 45），完全离线 | 约 200 MB | 想要更自然、可选音色多 |

音色版仍可切换到系统语音。Kokoro 模型为 Apache 2.0 许可，由 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 在手机上运行。
所有内置音色都用“收款一千零五块九毛五”做过合成 + 语音识别自动校验，列表里“✓”表示读法准确，“★”为推荐音色。

## 编译

1. 用 Android Studio（Ladybug 2024.2 或更新）打开本目录，等待 Gradle 同步
2. 左下角 Build Variants 选 `liteDebug`（标准版）或 `fullDebug`（音色版），连接手机运行
3. 音色版首次编译前，先在项目根目录运行一次 `bash scripts/fetch-kokoro.sh` 下载模型（约 140 MB，放在 `app/src/full/assets/kokoro`，不进 Git）
4. 命令行：`./gradlew assembleLiteDebug` / `./gradlew assembleFullDebug`
5. 分发正式包前，在 `app/build.gradle.kts` 的 `release` 中配置你自己的签名证书，之后所有版本保持同一证书

环境：JDK 17、compileSdk 35、minSdk 26（Android 8.0）、targetSdk 35。

## 用 GitHub 自动打包（不用装 Android Studio）

1. 在 GitHub 新建一个仓库（建议设为 Private），把本目录全部文件上传或推送到 `main` 分支
2. 打开仓库的 **Actions** 页，“打包 APK”会自动运行，约 5–10 分钟
3. 运行完成后点进去，在页面底部 **Artifacts** 下载 `标准版-debug-apk` 或 `音色版-debug-apk`，解压得到 APK，传到手机安装（工作流会自动下载 Kokoro 模型）

调试版即可直接安装使用。想要正式版（体积更小、可长期覆盖升级），在仓库 Settings → Secrets and variables → Actions 添加四个密钥：

| 名称 | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 签名文件 `.jks` 的 base64 文本（`base64 -w0 release.jks`） |
| `KEYSTORE_PASSWORD` | 签名文件密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

生成签名文件：`keytool -genkeypair -v -keystore release.jks -alias daozhang -keyalg RSA -keysize 2048 -validity 36500`。签名文件和密码务必自己保管，丢了以后无法覆盖升级。

推送 `v1.0.0` 这类标签时，APK 会自动发布到仓库的 Releases 页面。

> 本工程在无 Android SDK 的环境中编写，金额转换算法已通过单元测试，其余代码未经实际编译。
> 首次同步若有个别依赖版本或 API 报错，按 Android Studio 提示修正即可。

## 已实现

| 模块 | 内容 |
| --- | --- |
| 金额读法 | 0.01–99999.99；口语（十九块九 / 十九块零五分 / 五毛五）与标准（十九点九元）两种；“两”与“整”可选；单元测试覆盖零的处理和边界 |
| 播报 | 系统 TTS 合成 → 专属提示音（3 种可选）→ 软件增益（0–6 dB，防削波）→ AudioTrack；串行队列，上限 20 条；每次连播 1–3 遍 |
| 计划 | 定点（日期 + 时分秒，秒以 10 秒为步进）；间隔循环（预设或自定义 ≥30 秒，次数 0–999，0 为无限）；静默时段到点跳过不补播 |
| 调度 | AlarmManager 精确闹钟，未授权自动降级；前台服务只在有运行中任务时存在，通知栏可“暂停全部”；开机/升级后重排闹钟，过期定点任务标为“已错过” |
| 管理 | 任务列表、暂停/继续/删除、一键清空；今日播报次数；日志保留 30 天 |
| 设置 | 全局总开关、读法、音色、语速、音调、增益、闹钟/媒体音频流、权限中心（通知、精确闹钟、电池优化、各厂商自启动跳转与文字指引） |
| 导出 | WAV 保存到“音乐/到账语音工坊”，文件名以“模拟播报”开头 |

## 与规划的差异

- 语音用 TTS 合成（音色版为内置 Kokoro，标准版为系统语音），而不是预录片段拼接。
- 导出只有 WAV，MP3 需要额外的编码库，放到下一版。
- 统计图表、随机金额循环属于 P1，未做。

## 目录

```
app/src/main/java/com/daozhang/yuyin/
├── core/AmountSpeller.java   金额 → 中文读法（纯 Java，可单测）
├── audio/                    TTS 合成、PCM 处理与提示音、播放队列与导出
├── data/                     Room 数据表、全局设置
├── sched/                    闹钟调度、触发执行、前台服务、广播接收器
└── ui/                       Compose 界面：播报 / 任务 / 设置
```

运行单元测试：`./gradlew testLiteDebugUnitTest`

音色版相关代码：`app/src/full/`（Kokoro 引擎与音色表），标准版占位实现在 `app/src/lite/`。

## 真机验证清单

- 小米、华为/荣耀、OPPO、vivo 各一台，开启权限中心全部项目后，跑 24 小时 5 分钟循环任务，检查日志无漏播
- 息屏、锁屏、划掉最近任务三种情况下定点任务准时触发（误差 ≤ 5 秒）
- 重启手机后循环任务继续，过期定点任务显示“已错过”
