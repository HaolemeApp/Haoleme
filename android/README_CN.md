<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/English-Primary-2563EB?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="English Android guide"></a>
  <a href="README_CN.md"><img src="https://img.shields.io/badge/简体中文-中文文档-E85D75?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="简体中文 Android 指南"></a>
</p>

# Android App

这里是好了么 Android App 的完整可构建工程。

## 环境要求

- JDK 17
- Android SDK 35

## 构建与测试

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。

## 自托管地址

默认构建连接好了么官方 HTTPS 服务。自托管构建可以在命令行覆盖服务器和更新清单：

```bash
./gradlew assembleDebug \
  -PHAOLEME_DEFAULT_SERVER_URL=https://haoleme.example.com \
  -PHAOLEME_UPDATE_URLS=https://haoleme.example.com/downloads/update.json
```

## Release 签名

官方签名材料不在仓库中。Release 构建从以下环境变量读取签名配置：

```bash
export HAOLEME_ANDROID_KEYSTORE=/private/path/release.jks
export HAOLEME_ANDROID_KEYSTORE_PASSWORD='...'
export HAOLEME_ANDROID_KEY_ALIAS='...'
export HAOLEME_ANDROID_KEY_PASSWORD='...'
./gradlew assembleRelease
```

如果没有配置这些变量，Release 任务会使用本机 debug 签名，仅适合开发测试。

请勿提交 keystore、密码、`local.properties`、`.env` 或 `google-services.json`。
