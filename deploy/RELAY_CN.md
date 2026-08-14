<p align="center">
  <a href="RELAY.md"><img src="https://img.shields.io/badge/English-Primary-2563EB?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="English private Relay guide"></a>
  <a href="RELAY_CN.md"><img src="https://img.shields.io/badge/简体中文-中文文档-E85D75?style=flat-square&amp;logo=googletranslate&amp;logoColor=white" alt="简体中文私有 Relay 指南"></a>
</p>

# 好了么私有 Relay

同一个好了么 Android App 和 `hao` CLI 可以连接由用户自行运营的 Relay。运行内容始终保持端到端加密：手机创建账户密钥，并在配对过程中直接使用 CLI 的临时 RSA 公钥封装该密钥。

## 本地局域网模式（无需域名或证书）

在可信任的家庭或办公局域网中，可以在本机启动 Relay：

```bash
pip install -U haoleme
hao login
```

依次选择 **Private Relay** 和 **Start a LAN Relay on this computer**。命令会在后台启动 Relay，并显示 6 位配对码和二维码；配对完成后会返回终端。它还会显示局域网内其他电脑可以使用的地址，例如：

```bash
hao login 192.168.1.20:8000
```

CLI 会把私有 `IP:port` 自动展开为 `http://IP:port`，二维码则会让普通 Android App 切换到该服务地址。手机和被监控的电脑必须位于能够访问此 Relay 的同一局域网中。如有需要，请在本机防火墙中允许 TCP 8000 入站连接。

如需手动管理 Relay，仍可使用 `haoleme-relay --lan --port 8000`。添加 `--no-pair` 可让 Relay 在前台运行且不配对当前主机。

明文 HTTP 仅允许用于 localhost 和私有局域网 IP。运行内容仍然端到端加密，但任何能够监听该局域网的人都可能看到凭据和连接元数据。公网、共享网络、不受信任网络或远程访问必须使用下面的 HTTPS 模式。切勿将局域网 Relay 端口转发到公网。

## 公网 HTTPS 模式

### 1. 将域名指向服务器

创建类似 `hao.example.com` 的 A/AAAA 记录，并开放 TCP 80 和 443 入站端口。被监控的电脑和手机都不需要公网 IP。

### 2. 启动 Relay

在安装了 Docker Compose 的服务器上，从仓库根目录运行：

```bash
HAOLEME_RELAY_DOMAIN=hao.example.com \
  docker compose -f deploy/relay-compose.yml up -d --build
```

Caddy 会自动申请并续期 HTTPS 证书。使用以下命令验证部署：

```bash
curl https://hao.example.com/health
```

Caddy 会在 443 端口同时提供 HTTPS API 和加密的 `wss://` 实时更新。Relay
内部的 8000（HTTP）与 8001（WebSocket）端口只留在 Compose 网络中，无需在
公网防火墙开放。

Relay 数据保存在 `relay_data` Docker 卷中，请定期备份。

### 3. 配对电脑

在需要监控的电脑上安装 CLI，然后运行一条命令：

```bash
pip install -U haoleme
hao login https://hao.example.com
```

使用普通好了么 App 扫描显示的二维码。二维码会为 App 选择这个 Relay，并按 Relay 隔离凭据和加密密钥。私有 Relay 生成的二维码绝不会回退到好了么公共服务。

等价的显式写法是：

```bash
hao login --relay https://hao.example.com
```

生产环境必须使用真实的 HTTPS 域名。即使运行内容本身采用端到端加密，明文 HTTP 仍会暴露 Bearer 凭据。
