<p align="center">
  <a href="CONTRIBUTING.md"><img src="https://img.shields.io/badge/English-Primary-2563EB?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="English contributing guide"></a>
  <a href="CONTRIBUTING_CN.md"><img src="https://img.shields.io/badge/简体中文-中文文档-E85D75?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="简体中文贡献指南"></a>
</p>

# Contributing

Bug fixes, compatibility improvements, documentation, and tests are welcome.

## Development Checks

Python CLI and cloud service:

```bash
python -m unittest discover -s tests -p 'test_*.py'
```

Android app:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

## Contribution Guidelines

- Keep each change focused, and add tests for behavioral changes.
- Do not commit keys, passwords, tokens, real server addresses, user data, or local build configuration.
- Do not add production databases, logs, APK files, signing files, or payment QR codes to the repository.
- Report security issues privately according to [SECURITY.md](SECURITY.md). Do not disclose vulnerability details in public.

Before opening a pull request, confirm that the Python tests and Android unit tests pass.
