# 12 — Production operations

How to put JH.COMP Prospect online on a Linux VPS, keep it running, back it
up, restore it and monitor it. The reasoning behind this runbook is in D27
(`docs/11-decisions.md`).

Everything here was rehearsed locally with `docker-compose.prod.yml` (Caddy
with a self-signed certificate for `localhost`): deployment, creating the first
administrator, logging in over HTTPS, the end-to-end tests, backup,
verification and restore.

## 1. Production architecture

```
Internet ──443/80──▶ Caddy (TLS, HSTS) ──▶ nginx (SPA, CSP, proxy /api/v1) ──▶ Spring API (prod profile) ──▶ PostgreSQL
```

- Only Caddy publishes a port. The database, the API and nginx stay on
  Docker's internal network with no host port.
- Caddy issues and renews the certificate (Let's Encrypt) by itself.
- The `prod` profile fixes the `Secure` cookie, disables the interactive API
  documentation (`/api/v1/docs`, `/api/v1/openapi`) and accepts
  `X-Forwarded-For` only from an internal proxy, so that the login limiter
  (D20) and the audit trail see the client's real IP.

## 2. Server requirements

- Linux with Docker Engine and the `docker compose` plugin (v2).
- 2 GB of RAM and 20 GB of disk are enough for the MVP.
- A domain with an A record (and AAAA if IPv6 exists) pointing at the server
  **before** the first start; without it Caddy cannot obtain a certificate.
- A firewall allowing only 22 (SSH), 80 and 443:
  `ufw default deny incoming && ufw allow 22 && ufw allow 80 && ufw allow 443 && ufw enable`.
  Docker publishes ports around `ufw`, which is why nothing but Caddy publishes
  a port in the production compose file.
- SSH by key only, and automatic security updates (`unattended-upgrades` on
  Debian/Ubuntu).

## 3. First deployment

```bash
sudo git clone <repository> /opt/jhcomp-prospect
cd /opt/jhcomp-prospect
cp .env.prod.example .env && chmod 600 .env
# Fill in APP_DOMAIN, ACME_EMAIL and POSTGRES_PASSWORD (openssl rand -base64 32).
# Keep POSTGRES_PASSWORD off the server too, in a password manager.

docker compose up -d --build        # COMPOSE_FILE in .env points at the production file
docker compose ps                   # postgres, backend, frontend "healthy"; caddy "running"
docker compose logs backend | grep "Successfully applied"   # migrations applied

scripts/create-admin.sh             # once only: e-mail, organisation, password (min. 12)
```

Open `https://APP_DOMAIN`, sign in as the administrator you created and check
the padlock.

`create-admin.sh` starts the API under the `bootstrap` profile, creates the
organisation and the administrator, and exits. It refuses to run once an
organisation exists. Further members are registered inside the application,
under **Membros** (D28): the system generates a temporary password, shown to
the administrator once, who passes it on through a secure channel; the person
must change it on first access. If someone forgets their password, the
administrator uses **Redefinir senha** on the same screen.

## 4. Updating to a new version

```bash
cd /opt/jhcomp-prospect
scripts/backup.sh                   # always, before updating
git pull
docker compose up -d --build
docker compose ps && docker compose logs --tail=50 backend
```

Flyway migrations run on API startup. They only go forward: there is no
"undo migration".

**Rolling back:** if the new version applied no migration, `git checkout
<previous version> && docker compose up -d --build` is enough. If it did, roll
back the code **and** restore the backup taken before the update (§6). Old code
against a new schema is not supported — Hibernate validates the schema and
refuses to start.

## 5. Backup

`scripts/backup.sh` writes `jhcomp-prospect-YYYYMMDDTHHMMSSZ.dump` (`pg_dump`
custom format) into `BACKUP_DIR`. The script verifies the dump is readable,
stores the SHA-256 beside it and deletes dumps older than
`BACKUP_RETENTION_DAYS`. Files are readable only by their owner (`umask 077`),
because they contain contact data.

Root's crontab (`crontab -e`):

```
15 3 * * *   /opt/jhcomp-prospect/scripts/backup.sh >> /var/log/jhcomp-backup.log 2>&1
*/5 * * * *  /opt/jhcomp-prospect/scripts/healthcheck.sh > /dev/null
```

**A copy off the server is mandatory:** a backup on the same disk disappears
with the server. Copy `BACKUP_DIR` elsewhere — another provider, object
storage — with `rclone sync` or `rsync` in the same cron job, after the backup.
The script does not encrypt the dump: if the destination is not trusted,
encrypt before sending (`age`, `gpg`).

Also keep off the server: the `.env` (without it nobody knows the database
password) and, to avoid reissuing the certificate, the `caddy_data` volume.

## 6. Verifying and restoring

**Verify** a backup without touching the live database — it starts a
disposable PostgreSQL, restores into it, and prints the schema version and the
row counts:

```bash
scripts/verify-backup.sh /var/backups/jhcomp-prospect/jhcomp-prospect-<date>.dump
scripts/verify-backup.sh <dump> --compare   # right after a backup: compares with the live database
```

Run the verification **every month. A backup that has never been restored is
not a backup.**

**Restore** into the live database. This erases the current data:

```bash
scripts/restore.sh <dump> --yes
```

The script checks the SHA-256 and the readability of the dump, takes a safety
backup of the current state, stops the API, recreates the database, restores
and brings the API back up, waiting for it to become healthy. If the current
database is corrupted to the point where the safety backup fails, use
`--no-safety-backup` deliberately.

Restoring onto a new server: follow §3 up to `docker compose up -d --build`,
skip `create-admin.sh`, and run `scripts/restore.sh <dump> --yes`.

## 7. Monitoring

- **External monitor** (UptimeRobot, Better Stack, Healthchecks.io or
  similar) on `https://APP_DOMAIN/api/v1/health`, every 5 minutes, alerting by
  e-mail or phone. The endpoint checks the database and answers `503` with
  `"status": "DOWN"` if the connection fails. It is the only alert that still
  works when the whole server goes down.
- **`scripts/healthcheck.sh`** on cron (§5): healthy containers, public health
  endpoint, a backup less than 26 h old, and disk below 85%. With
  `ALERT_WEBHOOK_URL` in `.env` it POSTs the alert; without it, cron mails the
  output if the server has an MTA.
- **Containers** restart on their own (`restart: unless-stopped`) and have
  their own health checks.
- **Logs:** `docker compose logs -f backend` (or `caddy`, `frontend`).
  Rotation at 5 files of 10 MB per service. Every API line carries the
  `requestId`, which also returns to the browser in the `X-Request-Id` header:
  that is what links an error the operator reports to the log that recorded it.

## 8. Deployment security checklist

- [ ] `.env` with `chmod 600`, outside Git, with the database password copied
      somewhere safe.
- [ ] `docker compose ps` shows a published port only on `caddy`.
- [ ] `https://APP_DOMAIN` has a valid certificate; `http://` redirects.
- [ ] `https://APP_DOMAIN/api/v1/docs` answers 404.
- [ ] Firewall allows only 22, 80 and 443; SSH by key only.
- [ ] Backup in cron, a copy off the server, and one verification done.
- [ ] External monitor configured and its alert **tested** — stop the API with
      `docker compose stop backend`, confirm the alert arrives, then
      `docker compose start backend`.
