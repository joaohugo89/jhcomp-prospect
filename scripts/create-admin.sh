#!/usr/bin/env bash
# Cria o primeiro administrador de producao (D19, D27). Uma unica vez.
#
#   scripts/create-admin.sh
#
# Pergunta e-mail, nome da organizacao e senha (sem eco). A senha vai para o
# container por variavel de ambiente herdada (-e NOME, sem valor), e nao
# aparece na linha de comando nem no historico do shell. O backend sobe no
# perfil "bootstrap", sem porta publicada, cria organizacao e administrador e
# termina. Com o banco ja inicializado, recusa e sai com erro.
set -euo pipefail

cd "$(dirname "$0")/.."

read -r -p "E-mail do administrador: " BOOTSTRAP_ADMIN_EMAIL
read -r -p "Nome da organizacao [JH.COMP]: " BOOTSTRAP_ORGANIZATION_NAME
BOOTSTRAP_ORGANIZATION_NAME="${BOOTSTRAP_ORGANIZATION_NAME:-JH.COMP}"
read -r -s -p "Senha (minimo 12 caracteres): " BOOTSTRAP_ADMIN_PASSWORD
echo
read -r -s -p "Repita a senha: " confirmation
echo
if [[ "$BOOTSTRAP_ADMIN_PASSWORD" != "$confirmation" ]]; then
  echo "As senhas nao conferem." >&2
  exit 2
fi
export BOOTSTRAP_ADMIN_EMAIL BOOTSTRAP_ORGANIZATION_NAME BOOTSTRAP_ADMIN_PASSWORD

docker compose run --rm \
  -e SPRING_PROFILES_ACTIVE=prod,bootstrap \
  -e BOOTSTRAP_ADMIN_EMAIL \
  -e BOOTSTRAP_ORGANIZATION_NAME \
  -e BOOTSTRAP_ADMIN_PASSWORD \
  backend

echo "Administrador criado. Entre pela aplicacao com o e-mail informado."
