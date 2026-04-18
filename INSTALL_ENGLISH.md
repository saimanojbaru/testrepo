# King Remind - English Installation Guide

> Original repo: https://github.com/coder-kingyifan/king-remind
> This guide translates the Chinese installation instructions to English.

## What is King Remind?

King Remind is a desktop reminder assistant built with Electron and Vue 3. It supports:
- AI-powered reminders via natural language ("remind me to have a meeting tomorrow at 3 PM")
- Multi-channel notifications: Desktop, Email, Telegram, WeChat, Discord, Webhook
- Recurring reminders (by minute/hour/day/month/year), lunar calendar support
- Docker deployment for server/headless environments

---

## Requirements

- Node.js >= 22
- npm

---

## Option 1: Desktop App (Development Mode)

```bash
# 1. Clone the repository
git clone https://github.com/coder-kingyifan/king-remind.git
cd king-remind

# 2. Install dependencies
npm install

# 3. Start the app (with graphical interface)
npm run dev

# Windows only — if you see garbled/broken characters in the console, use this instead:
npm run dev:win

# Start in headless mode (terminal REPL, no GUI)
npm run dev:headless
```

---

## Option 2: Build & Package (Windows Installer)

```bash
npm run build       # Compile frontend and main process
npm run pack        # Package as Windows installer + portable .exe
npm run pack:dir    # Package as an uninstalled directory (no installer)
```

Output files will appear in the `dist/` folder:
- `king-remind-{version}-setup.exe` — NSIS installer
- `king-remind-{version}-portable.exe` — Portable version (no install needed)

---

## Option 3: Docker Deployment (Server / Headless)

```bash
# 1. Clone the repository
git clone https://github.com/coder-kingyifan/king-remind.git
cd king-remind

# 2. Copy the example environment file
cp .env.example .env

# 3. Edit .env to configure your notification channels (see Configuration section below)

# 4. Start the container
docker compose up -d --build

# 5. View logs
docker compose logs -f
```

### Useful Docker Commands

```bash
docker compose exec king-remind king-repl    # Open interactive REPL inside container
docker compose run --rm king-remind          # Run in interactive mode
docker compose logs -f                        # Stream real-time logs
docker compose restart                        # Restart the service
```

---

## Configuration (.env file)

Copy `.env.example` to `.env` and fill in the values for the notification channels you want to use.

### System Settings

| Variable | Description | Default |
|---|---|---|
| `KING_API_PORT` | API server port | `33333` |
| `KING_API_TOKEN` | Optional Bearer token for API auth | _(none)_ |
| `KING_CONFIG_OVERRIDE` | Allow env vars to override database settings | `false` |

### Email (SMTP)

| Variable | Description |
|---|---|
| `KING_EMAIL_ENABLED` | Set to `true` to enable email notifications |
| `KING_EMAIL_SMTP_HOST` | SMTP server host (e.g. `smtp.gmail.com`) |
| `KING_EMAIL_SMTP_PORT` | SMTP port (e.g. `465` for SSL) |
| `KING_EMAIL_SMTP_USER` | Your email address |
| `KING_EMAIL_SMTP_PASS` | Your email **app password** (not your login password) |
| `KING_EMAIL_FROM` | Sender address |
| `KING_EMAIL_TO` | Recipient address |

> Common SMTP hosts: Gmail (`smtp.gmail.com:465`), Outlook (`smtp.office365.com:587`)

### Telegram

| Variable | Description |
|---|---|
| `KING_TELEGRAM_BOT_TOKEN` | Get from [@BotFather](https://t.me/BotFather) on Telegram |
| `KING_TELEGRAM_CHAT_ID` | Get from [@userinfobot](https://t.me/userinfobot) |
| `KING_TELEGRAM_PROXY_URL` | HTTP proxy URL (required if Telegram is blocked in your region) |

### Enterprise WeChat (WeCom)

| Variable | Description |
|---|---|
| `KING_WECHAT_WORK_CORP_ID` | Corp ID from WeCom admin console |
| `KING_WECHAT_WORK_AGENT_SECRET` | Agent Secret |
| `KING_WECHAT_WORK_AGENT_ID` | Agent ID |

### Custom Webhook

Supports GET/POST/PUT/PATCH requests. You can use template variables in the URL or body:
- `{{title}}` — Reminder title
- `{{body}}` — Reminder message body
- `{{reminder_id}}` — Reminder ID

---

## Notes

- **Database encryption**: The local database (`remind.db.enc`) is encrypted with AES-256-GCM. The encryption key is stored separately in `remind.key`. Keep this file safe.
- **License**: CC BY-NC-SA 4.0 — Free for non-commercial use. Attribution required. Derivative works must use the same license.
