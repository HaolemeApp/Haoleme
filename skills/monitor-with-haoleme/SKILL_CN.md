---
name: monitor-with-haoleme
description: 选择性地使用 Haoleme 监控重要、长时间运行或资源密集型命令。通过在命令前添加 `hao`，将状态、输出和完成通知同步到手机 App。适用于训练、微调、完整评测、基准测试、模拟、大型构建、数据管道、批处理、爬虫、部署、迁移及其他预计运行数分钟的重要任务。不用于依赖或环境安装、快速冒烟测试、格式化、Lint、简单探测、普通文件或 Git 命令，以及可能暴露敏感信息的命令。
---

<p align="center">
  <a href="SKILL.md"><img src="https://img.shields.io/badge/English-Primary-2563EB?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="English skill guide"></a>
  <a href="SKILL_CN.md"><img src="https://img.shields.io/badge/简体中文-中文文档-E85D75?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="简体中文 Skill 指南"></a>
</p>

# 使用好了么监控任务

只对值得在好了么手机 App 中关注的命令使用 `hao`，普通开发命令应保留在本地运行。

## 运行前判断

首先应用排除规则。遇到以下任一情况时，绝不能监控该命令：

- 安装或配置环境，例如 `pip install`、`uv sync`、`conda install`、`npm install`、`apt`、`brew`，以及登录或初始化命令。
- 预计很快结束的快速探测、冒烟测试、小型预测测试、格式化、Lint、类型检查、健康检查、文件操作、搜索或普通 Git 命令。
- Shell、REPL、编辑器、密码提示或身份验证流程等交互式基础设施。
- 命令行或预期输出会暴露密码、API Key、Token、私钥、完整环境变量、凭据或其他秘密。
- 命令已经包含 `hao` 前缀。
- 用户明确要求不要监控或同步。

至少存在以下一个强信号时，应监控该命令：

- 用户明确要求监控、接收通知或在好了么中跟踪任务。
- 命令用于训练或微调模型、运行完整评测或基准测试、执行模拟，或者处理大型数据集。
- 这是昂贵的 GPU、CPU、内存或远程服务器任务，其成功、失败或完成状态很重要。
- 这是重要的批处理、爬取、部署、迁移、大型构建或长时间脚本。
- 预计运行约两分钟或更久，并会产生用户关心的结果。

当运行时长不确定时，应监控昂贵或后果重要的工作。不能仅仅因为命令调用了 Python、测试运行器或构建工具就进行监控。

## 准备好了么

在一个环境中首次选择监控命令前：

1. POSIX 系统使用 `command -v hao`，PowerShell 使用 `Get-Command hao` 检查命令是否可用。
2. 如果缺少 `hao`，不要静默替换或延迟用户任务。说明用户可以运行 `pip install -U haoleme`，然后重试。
3. 如果因为设备尚未配对而监控失败，请用户运行 `hao login`。安装与登录命令都不能使用 `hao` 包裹。
4. 仅在实际排查好了么故障时使用 `hao doctor`，不要在每条命令前运行。

## 运行命令

在原始可执行文件和参数前添加 `hao`，并保持工作目录、参数、引号和环境变量赋值不变。

```bash
hao python train.py --epochs 100
hao CUDA_VISIBLE_DEVICES=0 python train.py
hao bash scripts/full-evaluation.sh
hao make -j8 release
```

如果复合 Shell 程序依赖管道、重定向、变量展开或多条命令，应监控一次明确的 Shell 调用：

```bash
hao bash -lc 'python evaluate.py 2>&1 | tee evaluation.log'
```

优先使用执行工具的工作目录参数，不要把 `cd` 嵌入命令中。不要仅仅为了添加好了么而修改脚本。

让 `hao` 保持前台运行，以便记录真实退出状态。如果用户需要 `tmux`、`screen`、调度器或其他监督程序，应把完整的 `hao ...` 命令放入该监督程序。

## 处理混合工作流

只监控重要的最终步骤或完整规模步骤。例如：

```bash
pip install -r requirements.txt        # 仅本地运行
python train.py --epochs 1 --smoke     # 仅本地运行
hao python train.py --epochs 100        # 监控
```

如果一个工作流启动多个相互独立的重要实验，应分别为每个实验添加 `hao`，使每次运行都有独立状态和输出。不要用一个 `hao bash -lc` 包裹整个初始化流水线。

## 报告结果

执行结束后，报告正常命令结果和退出状态。仅当好了么能提供有用信息时才提及它，例如确认任务已显示在 App 中，或解释为什么跳过监控。

除非 `hao` 确实已启动运行，否则不能声称命令已成功同步。如果好了么在底层命令启动前失败，应明确显示错误，让用户决定是重试监控还是仅在本地运行。
