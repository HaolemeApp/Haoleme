# 好了么 Haoleme

一个可以在手机上查看电脑/服务器命令运行状态的 Android App。

如果你经常跑训练、脚本、服务、批处理任务，好了么可以帮你把终端里的运行状态同步到手机上。命令跑完、失败、成功，都可以在 App 里看到，不用一直盯着 SSH 或电脑屏幕。

## 能做什么

- 在手机上查看命令运行状态：running / succeeded / failed
- 查看终端输出日志，方便远程确认程序有没有卡住
- 命令结束后在 Android 上收到通知
- 支持多台设备：我的 Mac、服务器 A、实验机 B
- App 里可以切换不同设备，也可以看 All 汇总
- 支持扫码或 6 位配对码登录
- 不需要自己配置公网 IP、ngrok、Cloudflare
- 删除历史记录后，各设备视图和 All 会保持一致

## 下载 App

[下载 Haoleme.apk](https://github.com/HaolemeApp/Haoleme/releases/latest)

当前版本：`0.6.68`

首次安装时，Android 可能会提示“未知来源应用”，允许安装即可。

## 电脑/服务器端安装

电脑或服务器上安装命令行工具：

```bash
pip install -U haoleme
```

登录并和手机配对：

```bash
hao login
```

然后在 App 里扫码，或者输入终端显示的 6 位配对码。

## 使用方式

以后运行命令时，在前面加 `hao`：

```bash
hao python train.py
```

或者：

```bash
hao bash run.sh
```

运行状态、退出码、终端输出都会同步到手机 App。

旧写法 `hao run ...` 也仍然可用。

## 适合谁

- 经常 SSH 到服务器跑任务的人
- 跑机器学习训练、数据处理、爬虫、批处理脚本的人
- 想在手机上看程序是否结束的人
- 不想折腾公网 IP、端口转发、ngrok 的人

## 一句话介绍

好了么 = 给终端命令加一个手机监控面板。
