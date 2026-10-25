# Samsung TV Home + TV Airplay

为支持 Android 应用的三星电视提供简洁首页与中文 AirPlay 接收器。一个仓库维护两个独立应用，可按需安装、分别更新。

> **实验版本**：现有功能曾在国行三星 S95D 的 Android 10 容器环境中验证；本次通用化版本的首次自动识别仍待电视干净安装实测。其他机型、固件的兼容性尚未验证。

## 适用环境

电视必须已经能够安装并运行 Android APK。本项目不安装 Android，也不是可直接安装到普通纯 Tizen 电视上的网页应用。

## 两个应用

| 应用 | 功能 | 安装方式 |
| --- | --- | --- |
| 简洁首页 | 时间、日历、壁纸、应用管理、HDMI 状态与切换、遥控器 Home 返回 | 需要首页时安装首页 APK |
| TV Airplay | 中文投屏接收器，默认名称 TV Airplay | 需要投屏时另装 AirPlay APK |

同时使用首页与投屏，需要分别安装两个 APK。首页不会静默安装或启动接收器。当前首先提供源码，预编译安装包将在完成验收后另行发布。

## 首次使用

1. 打开简洁首页，自动尝试识别本机电视；正常流程无需填写 IP 或电脑地址。
2. 发现电视后选择“允许连接”。地址和证书指纹可在“连接详情”查看，批准后保存。后续连接校验证书，变化或过期时停止连接。
3. 按系统提示授予使用所需权限。在“应用管理”中添加、隐藏、排序 Android 应用；仅存在于 Tizen 系统的应用不在此列表中。
4. 如需投屏，单独打开 TV Airplay 并完成必要授权；启动前先停止其他占用相同端口的接收器。

识别仅尝试回环地址和电视自身网卡地址，不扫描其他局域网设备。失败时可重试；设置中的“电视连接”保留手动地址输入供高级排查。

与旧版并存测试时，请先关闭旧首页的 Home 返回功能，停止旧投屏接收器，避免服务冲突；不必卸载旧版。

## 隐私

- 不内置私人电视地址、证书信任或电脑接收地址。
- 日志保存在本机。“导出脱敏诊断”由用户主动选择保存位置；系统缺少文件保存界面时可复制脱敏文本，不会自动上传。
- 个人配置、设备采集、日志、签名私钥及构建缓存不纳入源码发布。
- 对外分享诊断前仍请自行检查内容。详见 [隐私说明](docs/PRIVACY.md)。

## 项目结构

```text
launcher/   简洁首页，生成首页 APK
airplay/    TV Airplay，生成独立接收器 APK
shared/     共用的诊断脱敏代码
scripts/    依赖准备、隐私检查与源码归档
tests/     发布内容检查测试
docs/      验证、隐私、签名和依赖说明
```

## 构建

需要 JDK 17、Android SDK Platform 37 / Build Tools 37，以及 Python 3。通过 ANDROID_HOME 或不提交的 local.properties 设置 SDK 路径。

```sh
python scripts/prepare-native.py
./gradlew :launcher:testDebugUnitTest :launcher:assembleDebug :airplay:assembleDebug
```

Windows 使用 gradlew.bat。准备脚本从上游下载经 SHA-256 校验的 APK 并提取原生库，详见 [原生依赖来源](docs/NATIVE-SOURCES.md)。发布构建默认不签名；提供环境变量后使用独立发布密钥，详见 [发布签名](docs/RELEASE-SIGNING.md)。密钥和口令均不在仓库内。

## 验证与后续工作

构建、Java 回归、发布内容检查及浏览器 UI 检查已通过。首次自动配置、重启后的连接、HDMI/Home、诊断导出和投屏仍需对通用化版本进行电视验收，详见 [验证记录](docs/VERIFICATION.md)。

```sh
python -m unittest discover -s tests -v
python scripts/privacy_check.py
python scripts/build_source_archive.py
```

## 上游与许可

首页界面参考 [NelsonEU/arn0-tv](https://github.com/NelsonEU/arn0-tv)，保留 [MIT 许可](launcher/LICENSE)。接收器基于 [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server)，保留 [GPL-3.0 许可](airplay/LICENSE)及上游声明。字体和图形来源见 [资源说明](ASSET-SOURCES.md)。

Samsung、Apple、AirPlay、Bilibili 等商标归各自权利人所有；本项目为社区项目，与相关厂商无隶属关系。
