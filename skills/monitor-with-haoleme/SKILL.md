---
name: monitor-with-haoleme
description: Selectively monitor important long-running or resource-intensive commands with Haoleme by prefixing them with `hao`, so status, output, and completion notifications sync to the mobile app. Use while running training, fine-tuning, full evaluations, benchmarks, simulations, large builds, data pipelines, batch jobs, crawlers, deployments, migrations, or other consequential commands likely to take minutes. Do not trigger for dependency or environment installation, quick smoke tests, formatting or linting, simple probes, ordinary file or Git commands, or commands that expose secrets.
---

<p align="center">
  <a href="SKILL.md"><img src="https://img.shields.io/badge/English-Primary-2563EB?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="English skill guide"></a>
  <a href="SKILL_CN.md"><img src="https://img.shields.io/badge/简体中文-中文文档-E85D75?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="简体中文 Skill 指南"></a>
</p>

# Monitor With Haoleme

Use `hao` only for commands worth following from the Haoleme mobile app. Keep routine development commands local.

## Decide Before Running

Apply exclusions first. Never monitor a command when any of these conditions holds:

- It installs or configures an environment, such as `pip install`, `uv sync`, `conda install`, `npm install`, `apt`, `brew`, or login/setup commands.
- It is a quick probe, smoke test, tiny prediction test, formatter, linter, type check, health check, file operation, search, or ordinary Git command that is expected to finish quickly.
- It is interactive infrastructure such as a shell, REPL, editor, password prompt, or authentication flow.
- Its command line or expected output exposes passwords, API keys, tokens, private keys, full environment dumps, credentials, or other secrets.
- It is already prefixed with `hao`.
- The user explicitly says not to monitor or sync it.

Monitor the command when at least one strong signal applies:

- The user explicitly asks to monitor it, receive a notification, or follow it in Haoleme.
- It trains or fine-tunes a model, runs a full evaluation or benchmark, performs a simulation, or processes a large dataset.
- It is an expensive GPU, CPU, memory, or remote-server job whose failure or completion matters.
- It is a consequential batch job, crawl, deployment, migration, large build, or long-running script.
- It is expected to run for about two minutes or longer and produces a result the user will care about.

When duration is uncertain, monitor work that is expensive or consequential. Do not monitor work merely because it invokes Python, a test runner, or a build tool.

## Prepare Haoleme

Before the first command selected for monitoring in an environment:

1. Check availability with `command -v hao` on POSIX systems or `Get-Command hao` in PowerShell.
2. If `hao` is missing, do not silently replace or delay the user's task. Explain that the user can run `pip install -U haoleme`, then retry.
3. If monitoring fails because the device is not paired, ask the user to run `hao login`. Do not wrap either installation or login with `hao`.
4. Use `hao doctor` only to diagnose an actual Haoleme failure, not before every command.

## Run The Command

Prefix the original executable and arguments with `hao`. Preserve the working directory, arguments, quoting, and environment assignments.

```bash
hao python train.py --epochs 100
hao CUDA_VISIBLE_DEVICES=0 python train.py
hao bash scripts/full-evaluation.sh
hao make -j8 release
```

For a compound shell program that depends on pipes, redirects, variable expansion, or multiple commands, monitor one explicit shell invocation:

```bash
hao bash -lc 'python evaluate.py 2>&1 | tee evaluation.log'
```

Prefer the execution tool's working-directory option over embedding `cd` in the command. Do not edit a script solely to add Haoleme.

Keep `hao` in the foreground so it can record the real exit status. If the user needs `tmux`, `screen`, a scheduler, or another supervisor, place the complete `hao ...` command inside that supervisor.

## Handle Mixed Workflows

Monitor only the important final or full-scale step. For example:

```bash
pip install -r requirements.txt        # local only
python train.py --epochs 1 --smoke     # local only
hao python train.py --epochs 100        # monitor
```

If a workflow launches several independent important experiments, prefix each experiment separately so every run has its own status and output. Do not wrap the entire setup pipeline in one `hao bash -lc` command.

## Report The Result

After execution, report the normal command result and exit status. Mention Haoleme only when it adds useful context, such as confirming that the run is visible in the app or explaining why monitoring was skipped.

Never claim that a command synced successfully unless `hao` actually started the run. If Haoleme fails before the underlying command starts, surface the error and let the user choose whether to retry with monitoring or run locally.
