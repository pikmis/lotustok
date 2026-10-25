#!/usr/bin/env bash
# Puts the LotusTok server on a fresh VPS: Ubuntu 22.04 / 24.04 or Debian 12.
#
#   sudo bash install.sh DOMAIN EMAIL [TELEGRAM_OWNER_ID] [BOT_TOKEN]
#
#   DOMAIN             the name that already points at this machine (an A record)
#   EMAIL              where Let's Encrypt writes if the certificate is in trouble
#   TELEGRAM_OWNER_ID  your numeric Telegram id: panel sign-in alerts and the
#                      bot's owner commands come here. 0 or empty: no alerts
#   BOT_TOKEN          from @BotFather. Empty: the bot is not started
#
# Run it from this folder, copied to the VPS whole. Run it again to update the server: the
# database, the panel password, the bot token and the plugins are left alone.

set -euo pipefail

DOMAIN=${1:-}
EMAIL=${2:-}
OWNER=${3:-0}
BOT_TOKEN=${4:-}

if [ -z "$DOMAIN" ] || [ -z "$EMAIL" ]; then
    sed -n '2,13p' "$0"
    exit 1
fi
if [ "$(id -u)" != 0 ]; then
    echo "Run it as root: sudo $0 $*" >&2
    exit 1
fi

HERE=$(cd "$(dirname "$0")" && pwd)
APP=/opt/lotustok
WEB=/var/www/lotustok

# the server's files are either beside this script (this folder as it is) or
# in a server/ folder next to a deploy/ one (the repository)
if [ -f "$HERE/badges.py" ]; then
    SRC="$HERE"
elif [ -f "$(dirname "$HERE")/server/badges.py" ]; then
    SRC="$(dirname "$HERE")/server"
else
    echo "badges.py is not beside install.sh, and there is no server/ next to this folder." >&2
    exit 1
fi

echo "==> Packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y python3 nginx certbot python3-certbot-nginx rsync openssl curl

# the service imports cgi, which Python removed in 3.13
python3 - <<'PY'
import sys
if not ((3, 8) <= sys.version_info[:2] < (3, 13)):
    sys.exit("Python %d.%d here, and the server needs 3.8 to 3.12 (it uses the cgi module). "
             "Use Ubuntu 22.04/24.04 or Debian 12." % sys.version_info[:2])
PY

echo "==> User and folders"
id lotustok >/dev/null 2>&1 || useradd --system --home "$APP" --shell /usr/sbin/nologin lotustok
mkdir -p "$APP/plugins" "$WEB/updates" "$WEB/original"

echo "==> The server"
FIRST=0
[ -f "$APP/badges.db" ] || FIRST=1
rsync -a \
    --exclude 'badges.db*' --exclude 'admin.txt*' --exclude 'bot.txt' \
    --exclude 'plugins/' --exclude '__pycache__' --exclude '*README.md' \
    --exclude 'install.sh' --exclude '*.service' --exclude 'nginx-lotustok.conf' \
    "$SRC/" "$APP/"
[ -f "$APP/badges.json" ] || cp "$HERE/badges.json" "$APP/badges.json"

cat > /etc/lotustok.env <<EOF
LOTUSTOK_SITE=https://$DOMAIN
LOTUSTOK_OWNER=${OWNER:-0}
PYTHONUNBUFFERED=1
EOF
chown root:lotustok /etc/lotustok.env
chmod 640 /etc/lotustok.env

PASSWORD=""
if [ "$FIRST" = 1 ] && [ ! -f "$APP/admin.txt" ]; then
    PASSWORD=$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-20)
    printf '{"user": "admin", "password": "%s", "secret": "%s"}\n' \
        "$PASSWORD" "$(openssl rand -hex 32)" > "$APP/admin.txt"
    chmod 600 "$APP/admin.txt"
fi

if [ -n "$BOT_TOKEN" ]; then
    printf '%s\n' "$BOT_TOKEN" > "$APP/bot.txt"
    chmod 600 "$APP/bot.txt"
fi
chown -R lotustok:lotustok "$APP"

if [ "$FIRST" = 1 ]; then
    echo "==> The first badges"
    runuser -u lotustok -- env LOTUSTOK_SITE="https://$DOMAIN" python3 "$APP/seed.py"
    # the password is in the database now, hashed; the plain copy goes
    rm -f "$APP/admin.txt" "$APP/admin.txt.old"
fi

echo "==> Services"
cp "$HERE/lotustok-badges.service" /etc/systemd/system/
cp "$HERE/lotustok-bot.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable lotustok-badges >/dev/null
systemctl restart lotustok-badges
if [ -f "$APP/bot.txt" ]; then
    systemctl enable lotustok-bot >/dev/null
    systemctl restart lotustok-bot
fi

echo "==> nginx"
sed "s/__DOMAIN__/$DOMAIN/g" "$HERE/nginx-lotustok.conf" > /etc/nginx/sites-available/lotustok
ln -sf /etc/nginx/sites-available/lotustok /etc/nginx/sites-enabled/lotustok
nginx -t
systemctl reload nginx

echo "==> https"
certbot --nginx -d "$DOMAIN" -m "$EMAIL" --agree-tos --no-eff-email --non-interactive --redirect \
    || echo "certbot did not finish: is the A record for $DOMAIN pointing here, and port 80 open? Run it again once it is."

echo "==> Check"
sleep 2
curl -fsS http://127.0.0.1:8080/health && echo
curl -fsS "https://$DOMAIN/api/badges" -o /dev/null && echo "https://$DOMAIN/api/badges answers" \
    || echo "https://$DOMAIN/api/badges does not answer yet"

cat <<EOF

Done.
  The mod talks to:   https://$DOMAIN/api
  The panel:          https://$DOMAIN/admin
EOF
if [ -n "$PASSWORD" ]; then
    cat <<EOF
  Panel name:         admin
  Panel password:     $PASSWORD      <- written down nowhere else; change it in the panel
EOF
fi
echo "  Updates go in:      $WEB/updates/   (version.json and the apk)"
