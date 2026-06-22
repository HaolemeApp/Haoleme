<p align="center">
  <img src="android/app/src/main/res/drawable-nodpi/haoleme_icon_light.png" width="96" alt="Haoleme icon">
</p>

<h1 align="center">Haoleme</h1>

<p align="center">
  Monitor command runs on your phone.
</p>

<p align="center">
  <a href="README.md">中文</a>
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

## What It Does

Haoleme lets you monitor Linux/macOS commands from your phone.

Run a command with `hao`, then check its status, exit code, and console output in the mobile app. You also get a notification when the command finishes.

## Download

Android:

- [Latest APK on GitHub Releases](https://github.com/HaolemeApp/Haoleme/releases/latest)

## Quick Start

Install the command line tool:

```bash
pip install -U haoleme
```

Open the mobile app, then pair your machine:

```bash
hao login
```

Scan the QR code, or enter the 6-digit pairing code.

Run commands:

```bash
hao python train.py
```

or:

```bash
hao bash run.sh
```

The old `hao run python train.py` form still works.

The app will show live status and console output automatically.

## Features

- running / succeeded / failed status
- console output
- finish notifications
- multiple devices
- device rename
- project grouping
- run search and deletion
- QR code / 6-digit pairing
- end-to-end encryption for sensitive run content

## Updates

The app checks for updates in the background. When a new version is available, tap the version label in the top-right corner to download and install it.

## License

Haoleme is licensed under [AGPL-3.0-or-later](LICENSE).
