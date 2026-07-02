<p align="center">
  <img src="docs/assets/haoleme_icon_light.png" width="96" alt="好了么">
</p>

<h1 align="center">好了么</h1>

<p align="center">
  在手机上查看电脑和服务器里的命令运行状态。
</p>

<p align="center">
  <a href="README_EN.md">English</a>
  ·
  <a href="https://github.com/HaolemeApp/Haoleme/releases/latest">下载 App</a>
  ·
  <a href="https://pypi.org/project/haoleme/">PyPI</a>
</p>

<p align="center">
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-AGPL--3.0--or--later-blue" alt="License: AGPL-3.0-or-later">
  </a>
</p>

## 这是什么

好了么是一个命令运行监控工具。

在电脑或服务器上用 `hao` 启动命令，手机 App 就能看到运行状态、终端输出、设备在线状态和运行结束通知。它适合训练任务、远程脚本、批处理、爬虫、长时间 SSH 任务，以及任何“不想一直盯着终端”的场景。

## 下载

- Android App：[GitHub Releases](https://github.com/HaolemeApp/Haoleme/releases/latest)
- 命令行工具：[PyPI](https://pypi.org/project/haoleme/)

## 快速开始

安装命令行工具：

```bash
pip install -U haoleme
```

在电脑或服务器上配对：

```bash
hao login
```

打开 App，扫码或输入 6 位配对码。

以后直接在命令前加 `hao`：

```bash
hao python train.py
hao bash script.sh
hao echo hello
```

命令运行后，App 会自动显示状态和控制台输出。

## 功能

- 运行状态：running / succeeded / failed
- 控制台输出和搜索
- 运行结束通知
- 多设备切换和在线状态
- 设备重命名
- 项目分组
- GPU / CPU 监控
- 二维码和 6 位配对码
- 端到端加密传输敏感运行内容

## 安全

公开源码不包含官方签名密钥、生产服务器私密配置、个人收款码或访问令牌。

App 和 CLI 默认连接官方服务；你也可以基于源码自行部署。请不要把自己的密钥、token、数据库、签名文件或服务器密码提交到公开仓库。

## 开源协议

本项目使用 [AGPL-3.0-or-later](LICENSE) 许可证。

欢迎提交 Issue 和建议。项目仍在快速迭代，公测阶段建议保持 App 和 CLI 为最新版。
