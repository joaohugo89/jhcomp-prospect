# 12 — Operação em produção

Como colocar o JH.COMP Prospect no ar em uma VPS Linux, mantê-lo, fazer
backup, restaurar e monitorar. As decisões por trás deste roteiro estão na
D27 (`docs/11-decisions.md`).

Tudo aqui foi ensaiado localmente com o `docker-compose.prod.yml` (Caddy com
certificado próprio para `localhost`): deploy, criação do primeiro
administrador, login por HTTPS, testes E2E, backup, verificação e restauração.

## 1. Arquitetura de produção

```
Internet ──443/80──▶ Caddy (TLS, HSTS) ──▶ nginx (SPA, CSP, proxy /api/v1) ──▶ API Spring (perfil prod) ──▶ PostgreSQL
```

- Só o Caddy publica porta. Banco, API e nginx ficam na rede interna do
  Docker, sem porta no host.
- O Caddy emite e renova o certificado (Let's Encrypt) sozinho.
- O perfil `prod` fixa o cookie `Secure`, desliga a documentação interativa
  da API (`/api/v1/docs`, `/api/v1/openapi`) e aceita `X-Forwarded-For` só de
  proxy interno, para que o limite de login (D20) e a auditoria vejam o IP
  real do cliente.

## 2. Requisitos do servidor

- Linux com Docker Engine e o plugin `docker compose` (v2).
- 2 GB de RAM e 20 GB de disco são suficientes para o MVP.
- Domínio com registro A (e AAAA, se houver IPv6) apontando para o servidor
  **antes** do primeiro start; sem isso o Caddy não obtém o certificado.
- Firewall liberando só 22 (SSH), 80 e 443. Exemplo com `ufw`:
  `ufw default deny incoming && ufw allow 22 && ufw allow 80 && ufw allow 443 && ufw enable`.
  O Docker publica portas por fora do `ufw`; por isso nenhum serviço além do
  Caddy publica porta no compose de produção.
- SSH só por chave e atualizações automáticas de segurança do sistema
  (`unattended-upgrades` no Debian/Ubuntu).

## 3. Primeiro deploy

```bash
sudo git clone <repositório> /opt/jhcomp-prospect
cd /opt/jhcomp-prospect
cp .env.prod.example .env && chmod 600 .env
# Preencher APP_DOMAIN, ACME_EMAIL e POSTGRES_PASSWORD (openssl rand -base64 32).
# Guardar POSTGRES_PASSWORD também fora do servidor (gerenciador de senhas).

docker compose up -d --build        # COMPOSE_FILE no .env aponta para o compose de produção
docker compose ps                   # postgres, backend e frontend "healthy"; caddy "running"
docker compose logs backend | grep "Successfully applied"   # migrations aplicadas

scripts/create-admin.sh             # uma única vez: e-mail, organização e senha (mín. 12)
```

Abra `https://APP_DOMAIN`, entre com o administrador criado e confira o
cadeado do navegador.

O `create-admin.sh` sobe a API no perfil `bootstrap`, cria organização e
administrador e encerra. Ele recusa rodar de novo depois que existe uma
organização. Os demais membros são cadastrados pela própria aplicação, em
**Membros** (D28): o sistema gera uma senha temporária, mostrada uma única vez
ao administrador, que a entrega à pessoa por um canal seguro; no primeiro
acesso ela precisa trocá-la. Se alguém esquecer a senha, o administrador usa
**Redefinir senha** na mesma tela.

## 4. Atualizar para uma nova versão

```bash
cd /opt/jhcomp-prospect
scripts/backup.sh                   # sempre antes de atualizar
git pull
docker compose up -d --build
docker compose ps && docker compose logs --tail=50 backend
```

As migrations Flyway rodam sozinhas na subida da API. Elas só avançam: não
existe "desfazer migration".

**Voltar uma versão:** se a versão nova não aplicou migration, basta
`git checkout <versão anterior> && docker compose up -d --build`. Se aplicou,
volte o código **e** restaure o backup feito antes da atualização (§6).
Código antigo com schema novo não é suportado; o Hibernate valida o schema e
recusa subir.

## 5. Backup

`scripts/backup.sh` gera `jhcomp-prospect-AAAAMMDDTHHMMSSZ.dump` (formato
custom do `pg_dump`) em `BACKUP_DIR`. O script confere que o dump é legível,
grava o SHA-256 ao lado e apaga dumps mais antigos que
`BACKUP_RETENTION_DAYS`. Os arquivos ficam legíveis só pelo dono (`umask 077`),
porque contêm dados de contato.

Cron do root (`crontab -e`):

```
15 3 * * *   /opt/jhcomp-prospect/scripts/backup.sh >> /var/log/jhcomp-backup.log 2>&1
*/5 * * * *  /opt/jhcomp-prospect/scripts/healthcheck.sh > /dev/null
```

**Cópia fora do servidor é obrigatória:** um backup no mesmo disco some junto
com o servidor. Copie `BACKUP_DIR` para outro lugar (outro provedor,
armazenamento de objetos), por exemplo com `rclone sync` ou `rsync` no mesmo
cron, depois do backup. O dump não é criptografado pelo script: se o destino
não for confiável, criptografe antes de enviar (`age`, `gpg`).

Também mantenha fora do servidor: o `.env` (sem ele não se sabe a senha do
banco) e, se quiser evitar reemitir certificado, o volume `caddy_data`.

## 6. Verificar e restaurar

**Verificar** um backup sem tocar no banco em uso (sobe um PostgreSQL
descartável, restaura, mostra a versão do schema e as contagens):

```bash
scripts/verify-backup.sh /var/backups/jhcomp-prospect/jhcomp-prospect-<data>.dump
scripts/verify-backup.sh <dump> --compare   # logo após o backup: compara com o banco em uso
```

Rode a verificação **todo mês**. Backup nunca restaurado não é backup.

**Restaurar** no banco em uso. Isso apaga os dados atuais:

```bash
scripts/restore.sh <dump> --yes
```

O script confere o SHA-256 e a legibilidade do dump, faz um backup de
segurança do estado atual, para a API, recria o banco, restaura e sobe a API
de novo, esperando ela ficar saudável. Se o banco atual estiver corrompido a
ponto de o backup de segurança falhar, use `--no-safety-backup`
conscientemente.

Restauração em servidor novo: faça o §3 até `docker compose up -d --build`,
pule o `create-admin.sh` e rode `scripts/restore.sh <dump> --yes`.

## 7. Monitoramento

- **Monitor externo** (UptimeRobot, Better Stack, Healthchecks.io ou similar)
  em `https://APP_DOMAIN/api/v1/health`, a cada 5 minutos, alertando por
  e-mail ou celular. O endpoint confere o banco: responde `503` com
  `"status": "DOWN"` se a conexão falhar. É o único alerta que funciona se o
  servidor cair inteiro.
- **`scripts/healthcheck.sh`** no cron (§5): containers saudáveis, health
  público, backup com menos de 26 h e disco abaixo de 85%. Com
  `ALERT_WEBHOOK_URL` no `.env`, envia o alerta por POST; sem isso, o cron
  manda a saída por e-mail se o servidor tiver MTA.
- **Containers** reiniciam sozinhos (`restart: unless-stopped`) e têm health
  check próprio.
- **Logs:** `docker compose logs -f backend` (ou `caddy`, `frontend`). Rotação
  em 5 arquivos de 10 MB por serviço. Cada linha da API traz o `requestId`,
  que também volta ao navegador no cabeçalho `X-Request-Id`: é o que liga um
  erro relatado pelo operador ao log.

## 8. Checklist de segurança do deploy

- [ ] `.env` com `chmod 600`, fora do Git, com cópia da senha do banco em
      local seguro.
- [ ] `docker compose ps` mostra porta publicada só no `caddy`.
- [ ] `https://APP_DOMAIN` com certificado válido; `http://` redireciona.
- [ ] `https://APP_DOMAIN/api/v1/docs` responde 404.
- [ ] Firewall só com 22, 80 e 443; SSH só por chave.
- [ ] Backup no cron, cópia fora do servidor e uma verificação feita.
- [ ] Monitor externo configurado e alerta testado (pare a API com
      `docker compose stop backend`, confira o alerta, `docker compose start backend`).
