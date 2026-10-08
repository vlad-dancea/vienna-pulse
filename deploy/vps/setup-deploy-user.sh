#!/bin/bash
# One-time VPS setup for GitHub Actions deploys. Run as root.
# Usage: setup-deploy-user.sh "<deploy public key>"
set -euo pipefail
pubkey=${1:?public key required}

apt-get install -y -qq jq curl sudo >/dev/null
install -m 755 "$(dirname "$0")/pulse-deploy" /usr/local/sbin/pulse-deploy
mkdir -p /opt/vienna-pulse

id deploy >/dev/null 2>&1 || useradd --system --create-home --home-dir /var/lib/deploy --shell /bin/bash deploy
usermod -p '*' deploy   # no password, but not "locked" (sshd refuses locked accounts)

install -d -m 700 -o deploy -g deploy /var/lib/deploy/.ssh
echo "restrict,command=\"sudo -n /usr/local/sbin/pulse-deploy\" $pubkey" > /var/lib/deploy/.ssh/authorized_keys
chown deploy:deploy /var/lib/deploy/.ssh/authorized_keys
chmod 600 /var/lib/deploy/.ssh/authorized_keys

cat > /etc/sudoers.d/pulse-deploy <<'SUDO'
Defaults!/usr/local/sbin/pulse-deploy env_keep += "SSH_ORIGINAL_COMMAND"
deploy ALL=(root) NOPASSWD: /usr/local/sbin/pulse-deploy ""
SUDO
chmod 440 /etc/sudoers.d/pulse-deploy
visudo -cf /etc/sudoers.d/pulse-deploy
echo "deploy user ready"
