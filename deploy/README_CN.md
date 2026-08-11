<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/English-Primary-2563EB?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="English deployment guide"></a>
  <a href="README_CN.md"><img src="https://img.shields.io/badge/简体中文-中文文档-E85D75?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="简体中文部署指南"></a>
</p>

# 好了么云端部署

新建私有化或自托管实例时，请使用 [`RELAY_CN.md`](RELAY_CN.md) 中更精简的 Docker + Caddy 流程。该方案继续使用同一个 App 和 CLI，并通过配对二维码切换服务地址。

此目录包含适用于小型 VPS 的生产环境部署模板。

## HTTPS

申请常规公网 HTTPS 证书需要域名。将域名的 A 记录指向服务器，安装 Caddy，然后把 `Caddyfile.example` 复制到 `/etc/caddy/Caddyfile`，并替换其中的 `haoleme.example.com`。

云端服务应监听 `127.0.0.1:8000`，由 Caddy 在 443 端口提供 HTTPS。

进行公开 Android 测试时，建议直接使用附带的 Caddy 模板：它会申请 RSA 证书并禁用 HTTP/3，从而避免部分 Android 厂商 TLS 栈出现握手重置。修改 `/etc/caddy/Caddyfile` 后运行：

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

如果 Caddy 仍在提供旧的 ECDSA 证书，只删除该域名的托管证书缓存，然后重新加载：

```bash
sudo find /var/lib/caddy -path '*haoleme.example.com*' -type f -delete
sudo systemctl reload caddy
```

## 系统服务

```bash
sudo useradd --system --home /opt/haoleme-cloud-data --shell /usr/sbin/nologin haoleme
sudo mkdir -p /opt/haoleme-cloud-data/logs /opt/haoleme-cloud-data/backups
sudo chown -R haoleme:haoleme /opt/haoleme-cloud-data
sudo pip install -U haoleme
sudo cp deploy/haoleme-cloud.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now haoleme-cloud
```

`HAOLEME_REQUIRE_E2EE=1` 会让服务器拒绝明文命令运行数据。用户应通过手机 App 使用 `hao login` 配对，使 CLI 获得 App 的加密密钥。

## 每日备份

```bash
sudo cp deploy/haoleme-cloud-backup.service /etc/systemd/system/
sudo cp deploy/haoleme-cloud-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now haoleme-cloud-backup.timer
```

手动备份：

```bash
sudo -u haoleme haoleme-cloud backup
```

备份会通过 SQLite `quick_check` 验证，并在 `.db` 文件旁生成 `.sha256` 校验文件。

## 监控

```bash
sudo cp deploy/haoleme-cloud-monitor.service /etc/systemd/system/
sudo cp deploy/haoleme-cloud-monitor.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now haoleme-cloud-monitor.timer
```

手动监控：

```bash
sudo -u haoleme haoleme-cloud monitor
```

监控程序会检查数据库健康状态、磁盘剩余空间、权限边界，以及最新备份的时间和校验和。如果希望将故障 POST 到外部告警端点，请在监控服务中设置 `HAOLEME_ALERT_WEBHOOK_URL=https://...`。

## 备用服务器同步

配置第二台服务器时，应使用增量备用服务器模板，而不是每分钟复制完整 SQLite 文件。主机地址和 SSH 密钥必须保存在仓库之外：

```bash
sudo install -m 755 deploy/haoleme-sync-standby /usr/local/sbin/
sudo cp deploy/haoleme-sync-standby.{service,timer} /etc/systemd/system/
sudo sh -c 'printf "%s\n" "HAOLEME_STANDBY_HOST=root@standby.example.com" > /etc/haoleme-standby-sync.env'
sudo chmod 600 /etc/haoleme-standby-sync.env
sudo systemctl daemon-reload
sudo systemctl enable --now haoleme-sync-standby.timer
```

源数据库通过 SQLite 生成快照，rsync 将变化的数据块传输到持久化备用文件，验证后的快照再以原子方式安装。附带的 systemd 单元会限制备份任务的 CPU、I/O 优先级和内存用量。

## 健康与安全检查

```bash
haoleme-cloud health --db /opt/haoleme-cloud-data/haoleme-cloud.db
haoleme-cloud audit-permissions --db /opt/haoleme-cloud-data/haoleme-cloud.db
haoleme-cloud monitor --db /opt/haoleme-cloud-data/haoleme-cloud.db --backup-dir /opt/haoleme-cloud-data/backups
```
