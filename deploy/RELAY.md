<p align="center">
  <a href="RELAY.md"><img src="https://img.shields.io/badge/English-Primary-2563EB?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="English private Relay guide"></a>
  <a href="RELAY_CN.md"><img src="https://img.shields.io/badge/简体中文-中文文档-E85D75?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="简体中文私有 Relay 指南"></a>
</p>

# Haoleme Private Relay

The same Haoleme Android app and `hao` CLI can use an operator-owned relay. Run
contents remain end-to-end encrypted: the phone creates the account key and
wraps it directly to the CLI's temporary RSA public key during pairing.

## Local LAN mode (no domain or certificate)

For a trusted home or office LAN, start the relay on the local machine:

```bash
pip install -U haoleme
hao login
```

Choose **Private Relay**, then **Start a LAN Relay on this computer**. The command
starts the Relay in the background and prints a 6-digit pair code and QR code.
After pairing it returns to the shell. It also prints the LAN address that other
computers can use, for example:

```bash
hao login 192.168.1.20:8000
```

The CLI expands a private `IP:port` to `http://IP:port`, and the QR switches the
normal Android app to that endpoint. The phone and monitored computers must be
able to reach the relay on the same LAN. Allow inbound TCP 8000 in the machine's
firewall if necessary.

For manual Relay management, `haoleme-relay --lan --port 8000` remains
available. Add `--no-pair` to run it in the foreground without pairing the host.

Plain HTTP is accepted only for localhost and private LAN IP ranges. Run content
is still end-to-end encrypted, but HTTP exposes credentials and connection
metadata to anyone able to observe that LAN. Use the HTTPS mode below for public,
shared, untrusted, or remotely accessed networks. Never forward the LAN port to
the public Internet.

## Public HTTPS mode

### 1. Point a domain at the server

Create an A/AAAA record such as `hao.example.com`, then open inbound TCP ports
80 and 443. Neither monitored computers nor phones need public IP addresses.

### 2. Start the relay

From the repository root on a machine with Docker Compose:

```bash
HAOLEME_RELAY_DOMAIN=hao.example.com \
  docker compose -f deploy/relay-compose.yml up -d --build
```

Caddy obtains and renews HTTPS automatically. Verify the deployment:

```bash
curl https://hao.example.com/health
```

Caddy serves both HTTPS API traffic and secure `wss://` realtime updates on
port 443. The Relay's internal ports 8000 (HTTP) and 8001 (WebSocket) stay
inside the Compose network and do not need public firewall rules.

Relay data is held in the `relay_data` Docker volume. Back it up regularly.

### 3. Pair a computer

Install the CLI on the computer you want to monitor, then run one command:

```bash
pip install -U haoleme
hao login https://hao.example.com
```

Scan the displayed QR code in the normal Haoleme app. The QR selects this relay
for the app, and credentials plus encryption keys are isolated per relay. A QR
from a private relay never falls back to the public Haoleme service.

The equivalent explicit form is:

```bash
hao login --relay https://hao.example.com
```

Use a real HTTPS domain in production. Plain HTTP exposes bearer credentials
even though run contents themselves are end-to-end encrypted.
