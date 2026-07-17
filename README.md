<p align="center">
  <strong>
    <a href="README.md">English</a>
    &nbsp;|&nbsp;
    <a href="README_CN.md">简体中文</a>
  </strong>
</p>

<p align="center">
  <img src="android/app/src/main/res/drawable-nodpi/haoleme_icon_light.png" width="96" alt="Haoleme">
</p>

<h1 align="center">Haoleme</h1>

<p align="center">
  Monitor commands on your computer or server from your phone.
</p>

<p align="center">
  <a href="https://haolemeapp.github.io/">Website</a>
  ·
  <a href="https://github.com/HaolemeApp/Haoleme/releases/latest">Download App</a>
  ·
  <a href="https://pypi.org/project/haoleme/">PyPI</a>
</p>

<p align="center">
  <a href="https://github.com/HaolemeApp/Haoleme/releases/latest">
    <img src="https://img.shields.io/github/v/release/HaolemeApp/Haoleme?label=version&logo=github" alt="Latest version">
  </a>
  <a href="https://github.com/HaolemeApp/Haoleme/issues">
    <img src="https://img.shields.io/github/issues-search/HaolemeApp/Haoleme?query=is%3Aissue&label=issues&logo=github" alt="Total GitHub Issues">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-AGPL--3.0--or--later-blue" alt="License: AGPL-3.0-or-later">
  </a>
</p>

## What Is It

Haoleme is a command monitoring tool.

Start a command with `hao`, then watch its status, console output, device online state, and finish notification in the mobile app. It is useful for training jobs, remote scripts, batch tasks, crawlers, long SSH sessions, and anything you do not want to babysit in a terminal.

## Download

- Website: [haolemeapp.github.io](https://haolemeapp.github.io/)
- Android app: [GitHub Releases](https://github.com/HaolemeApp/Haoleme/releases/latest)
- CLI: [PyPI](https://pypi.org/project/haoleme/)

## Quick Start

Install the CLI:

```bash
pip install -U haoleme
```

Pair your computer or server:

```bash
hao login
```

Open the app, then scan the QR code or enter the 6-digit pairing code.

Run commands by prefixing them with `hao`:

```bash
hao python train.py
hao bash script.sh
hao echo hello
```

The app will show status and console output automatically.

## Features

- running / succeeded / failed status
- console output and search
- finish notifications
- multiple devices and online status
- device rename
- project grouping
- GPU / CPU monitoring
- QR code and 6-digit pairing
- end-to-end encryption for sensitive run content

## Security

The public source tree does not include official signing keys, private production deployment config, personal donation QR codes, or access tokens.

The app and CLI connect to the official service by default. You can also self-host from source. Do not commit your own keys, tokens, databases, signing files, or server passwords to a public repository.

## License

Haoleme is licensed under [AGPL-3.0-or-later](LICENSE).
