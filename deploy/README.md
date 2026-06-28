# Haoleme Cloud Deployment

This folder contains production-oriented templates for a small VPS.

## HTTPS

You need a domain for a normal public HTTPS certificate. Point an A record to the server, install Caddy, then copy `Caddyfile.example` to `/etc/caddy/Caddyfile` and replace `api.haoleme.cloud`.

The cloud server should listen on `127.0.0.1:8000`; Caddy exposes HTTPS on 443.

For public Android testing, prefer the included Caddy template as-is: it requests an RSA certificate and disables HTTP/3. This avoids TLS handshake resets seen on some vendor Android TLS stacks. After changing `/etc/caddy/Caddyfile`, run:

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

If Caddy keeps serving an old ECDSA certificate, remove only this domain's managed certificate cache and reload again:

```bash
sudo find /var/lib/caddy -path '*api.haoleme.cloud*' -type f -delete
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

## Upload App / CLI Releases

From the repo root, after bumping versions in `src/haoleme/__init__.py` and/or
`android/app/build.gradle`:

```bash
export HAOLEME_UPLOAD_PASSWORD='your-server-password'
chmod +x deploy/upload-release.sh

# Python wheel + update.json (+ reinstall cloud package)
./deploy/upload-release.sh --python

# Android APK + update.json
./deploy/upload-release.sh --android

# Both, and create a GitHub release for the APK
./deploy/upload-release.sh --github
```

Users can then run `hao update` to install the latest CLI from
`https://api.haoleme.cloud/downloads/update.json`, and the Android app can check the
same manifest for APK updates.

## Health And Security Checks

```bash
haoleme-cloud health --db /opt/haoleme-cloud-data/haoleme-cloud.db
haoleme-cloud audit-permissions --db /opt/haoleme-cloud-data/haoleme-cloud.db
haoleme-cloud monitor --db /opt/haoleme-cloud-data/haoleme-cloud.db --backup-dir /opt/haoleme-cloud-data/backups
```
