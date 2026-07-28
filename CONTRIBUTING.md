# 参与贡献

欢迎提交 Bug 修复、兼容性改进、文档和测试。

## 开发检查

Python CLI 与云端：

```bash
python -m unittest discover -s tests -p 'test_*.py'
```

Android App：

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

## 提交原则

- 保持改动范围清晰，并为行为修改补充测试。
- 不要提交密钥、密码、Token、真实服务器地址、用户数据或本机构建配置。
- 不要把生产数据库、日志、APK、签名文件和收款二维码放进仓库。
- 安全问题请按 [SECURITY.md](SECURITY.md) 私密报告，不要公开披露漏洞细节。

提交 Pull Request 前，请确认 Python 测试和 Android 单元测试均通过。
