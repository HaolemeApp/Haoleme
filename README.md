<p align="center">
  <img src="android/app/src/main/res/drawable-nodpi/haoleme_icon_light.png" width="96" alt="好了么 icon">
</p>

<h1 align="center">好了么</h1>

<p align="center">
  让手机看见电脑和服务器里的命令运行状态。
</p>

<p align="center">
  <a href="README_EN.md">English</a>
  ·
  <a href="https://github.com/HaolemeApp/Haoleme/releases/latest">GitHub</a>
  ·
  <a href="https://pypi.org/project/haoleme/">PyPI</a>
</p>

<p align="center">
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-AGPL--3.0--or--later-blue" alt="License: AGPL-3.0-or-later">
  </a>
</p>

## 介绍

好了么是一个命令运行监控工具。

在电脑或服务器上用 `hao` 启动命令，手机 App 就能看到它的状态、退出码和终端输出。命令结束后，手机会收到通知。

适合跑训练、脚本、爬虫、批处理、远程 SSH 任务的人。

## 下载

Android：

- [GitHub Releases 最新版 APK](https://github.com/HaolemeApp/Haoleme/releases/latest)

## 使用

在电脑或服务器上安装：

```bash
pip install -U haoleme
```

打开手机 App，然后在电脑上登录：

```bash
hao login
```

用 App 扫二维码，或输入终端里的 6 位配对码。

之后这样运行命令：

```bash
hao python train.py
```

或者：

```bash
hao bash run.sh
```

（旧的 `hao run ...` 写法已移除，请直接使用 `hao <command>`。）

手机上会自动显示运行状态和控制台输出。

## 功能

- 查看 running / succeeded / failed
- 查看终端输出
- 命令结束通知
- 多设备切换
- 设备重命名
- 项目分组
- 运行记录搜索和删除
- 二维码 / 6 位配对码
- 端到端加密传输敏感运行内容

## 更新

App 会在后台检查更新。发现新版本时，右上角会显示版本号，点击后可以下载并安装。

## 开源

本项目使用 [AGPL-3.0-or-later](LICENSE) 许可证。

欢迎提 Issue 和建议。

项目还在快速迭代，公测阶段请优先使用最新版。
