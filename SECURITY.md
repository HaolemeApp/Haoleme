<p align="center">
  <a href="SECURITY.md"><img src="https://img.shields.io/badge/English-Primary-2563EB?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="English security policy"></a>
  <a href="SECURITY_CN.md"><img src="https://img.shields.io/badge/简体中文-中文文档-E85D75?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="简体中文安全策略"></a>
</p>

# Security Policy

## Reporting a Security Issue

Use the repository's **Security > Report a vulnerability** feature on GitHub to report vulnerabilities privately. Do not post tokens, passwords, pairing codes, databases, logs, or other personal data in a public issue.

## Sensitive Information

This repository does not store:

- Real server IP addresses, SSH passwords, or private keys
- Access tokens for PyPI, GitHub, Cloudflare, or other platforms
- Official Android signing files or their passwords
- Production databases, logs, backups, or private deployment configuration
- Personal payment QR codes or user run data

Inject these values through environment variables, permission-restricted local files, or the deployment platform's secret-management system.

## Pre-commit Review

Before committing, verify that `git diff --cached` contains no keys, tokens, passwords, real server addresses, user data, or local absolute paths. If a credential has entered Git history, deleting the current file is not enough: revoke the credential immediately and clean the repository history.

## Supported Versions

Security fixes are prioritized for the latest Android app and `haoleme` CLI releases. Upgrade to the latest version before reproducing and reporting an issue.
