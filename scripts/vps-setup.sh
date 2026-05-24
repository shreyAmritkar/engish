#!/usr/bin/env bash
##############################################################################
# scripts/vps-setup.sh
#
# Run ONCE on a fresh Ubuntu 22.04 / 24.04 VPS to prepare it for
# GitHub Actions deployments.
#
# Usage:
#   chmod +x scripts/vps-setup.sh
#   sudo ./scripts/vps-setup.sh
##############################################################################
set -euo pipefail

APP_DIR="/opt/style-communicator"
DEPLOY_USER="${DEPLOY_USER:-ubuntu}"

echo "==> Installing Docker"
apt-get update -q
apt-get install -y -q ca-certificates curl gnupg lsb-release

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -q
apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin

usermod -aG docker "$DEPLOY_USER"
systemctl enable --now docker
echo "   Docker $(docker --version)"

echo "==> Installing Nginx"
apt-get install -y -q nginx
systemctl enable --now nginx
echo "   Nginx $(nginx -v 2>&1)"

echo "==> Installing Certbot (Let's Encrypt)"
apt-get install -y -q snapd
snap install --classic certbot
ln -sf /snap/bin/certbot /usr/bin/certbot

echo "==> Creating app directory"
mkdir -p "$APP_DIR"
chown "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR"
echo "   Place your production .env at: $APP_DIR/.env"

echo "==> Configuring UFW firewall"
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable
echo "   UFW status:"
ufw status

echo ""
echo "✅  VPS setup complete."
echo ""
echo "Next steps:"
echo "  1. Copy nginx.conf to /etc/nginx/sites-available/style-communicator"
echo "     and update 'server_name' to your actual domain."
echo "  2. sudo ln -s /etc/nginx/sites-available/style-communicator \\"
echo "                /etc/nginx/sites-enabled/"
echo "  3. sudo nginx -t && sudo systemctl reload nginx"
echo "  4. sudo certbot --nginx -d api.yourdomain.com"
echo "  5. Create $APP_DIR/.env with your production secrets."
echo "  6. Add GitHub Secrets: DEPLOY_HOST, DEPLOY_USER, DEPLOY_SSH_KEY, DEPLOY_ENV"