# Haoleme Cloud Deployment

This folder contains production-oriented templates for a small VPS.

## HTTPS

You need a domain for a normal public HTTPS certificate. Point an A record to the server, install Caddy, then copy `Caddyfile.example` to `/etc/caddy/Caddyfile` and replace `haoleme.example.com`.

The cloud server should listen on `127.0.0.1:8000`; Caddy exposes HTTPS on 443.

For public Android testing, prefer the included Caddy template as-is: it requests an RSA certificate and disables HTTP/3. This avoids TLS handshake resets seen on some vendor Android TLS stacks. After changing `/etc/caddy/Caddyfile`, run:

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

If Caddy keeps serving an old ECDSA certificate, remove only this domain's managed certificate cache and reload again:

```bash
sudo find /var/lib/caddy -path '*haoleme.example.com*' -type f -delete
sudo systemctl reload caddy
```

## Service

```bash
sudo useradd --system --home /opt/haoleme-cloud-data --shell /usr/sbin/nologin haoleme
sudo mkdir -p /opt/haoleme-cloud-data/logs /opt/haoleme-cloud-data/backups
sudo chown -R haoleme:haoleme /opt/haoleme-cloud-data
sudo pip install -U haoleme
sudo cp deploy/haoleme-cloud.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now haoleme-cloud
```

`HAOLEME_REQUIRE_E2EE=1` makes the server reject plaintext command runs. Users should pair with `hao login` from the mobile app so the CLI receives the app's encryption key.

## Daily Backups

```bash
sudo cp deploy/haoleme-cloud-backup.service /etc/systemd/system/
sudo cp deploy/haoleme-cloud-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now haoleme-cloud-backup.timer
```

Manual backup:

```bash
sudo -u haoleme haoleme-cloud backup
```

Backups are verified with SQLite `quick_check` and get a `.sha256` checksum file next to the `.db` file.

## Monitoring

```bash
sudo cp deploy/haoleme-cloud-monitor.service /etc/systemd/system/
sudo cp deploy/haoleme-cloud-monitor.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now haoleme-cloud-monitor.timer
```

Manual monitor:

```bash
sudo -u haoleme haoleme-cloud monitor
```

The monitor checks database health, disk free space, permission boundaries, and the newest backup age/checksum. Set `HAOLEME_ALERT_WEBHOOK_URL=https://...` in the monitor service if you want failures POSTed to an external alert endpoint.

## Standby Sync

For a second server, use the incremental standby templates instead of copying the
complete SQLite file every minute. Keep the host and SSH key outside the repo:

```bash
sudo install -m 755 deploy/haoleme-sync-standby /usr/local/sbin/
sudo cp deploy/haoleme-sync-standby.{service,timer} /etc/systemd/system/
sudo sh -c 'printf "%s\n" "HAOLEME_STANDBY_HOST=root@standby.example.com" > /etc/haoleme-standby-sync.env'
sudo chmod 600 /etc/haoleme-standby-sync.env
sudo systemctl daemon-reload
sudo systemctl enable --now haoleme-sync-standby.timer
```

The source database is snapshotted through SQLite, rsync transfers changed blocks
to a persistent standby file, and the verified snapshot is atomically installed.
The included unit limits backup CPU, I/O priority, and memory usage.

## Health And Security Checks

```bash
haoleme-cloud health --db /opt/haoleme-cloud-data/haoleme-cloud.db
haoleme-cloud audit-permissions --db /opt/haoleme-cloud-data/haoleme-cloud.db
haoleme-cloud monitor --db /opt/haoleme-cloud-data/haoleme-cloud.db --backup-dir /opt/haoleme-cloud-data/backups
```
