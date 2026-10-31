#!/usr/bin/env bash
# Backup do banco (D27).
#
# Gera um dump no formato custom do pg_dump, confere que ele pode ser lido
# (pg_restore --list), grava o SHA-256 ao lado e apaga os dumps mais antigos
# que a retencao. Feito para rodar por cron no servidor:
#
#   15 3 * * *  /opt/jhcomp-prospect/scripts/backup.sh >> /var/log/jhcomp-backup.log 2>&1
#
# Usa o "docker compose" do diretorio do projeto; no servidor, COMPOSE_FILE no
# .env aponta para docker-compose.prod.yml. Copie os dumps para FORA do
# servidor: backup que mora no mesmo disco some junto com ele.
set -euo pipefail

cd "$(dirname "$0")/.."

env_value() {
  grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2- || true
}

BACKUP_DIR="${BACKUP_DIR:-$(env_value BACKUP_DIR)}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-$(env_value BACKUP_RETENTION_DAYS)}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

# Dump contem dados pessoais de contato: so o dono do arquivo le.
umask 077
mkdir -p "$BACKUP_DIR"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$BACKUP_DIR/jhcomp-prospect-$stamp.dump"
partial="$target.partial"
trap 'rm -f "$partial"' EXIT

# Variaveis expandidas dentro do container, com as credenciais dele.
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > "$partial"

# Dump truncado (disco cheio, conexao caida) so seria descoberto na hora de
# restaurar, que e o pior momento.
docker compose exec -T postgres pg_restore --list < "$partial" > /dev/null

mv "$partial" "$target"
(cd "$BACKUP_DIR" && sha256sum "$(basename "$target")" > "$(basename "$target").sha256")

find "$BACKUP_DIR" -maxdepth 1 -type f -name 'jhcomp-prospect-*.dump*' -mtime "+$RETENTION_DAYS" -delete

echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) backup ok: $target ($(du -h "$target" | cut -f1))"
