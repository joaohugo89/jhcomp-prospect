-- V11 — Gestao de membros (RF-003, D28).
--
-- password_change_required: senha temporaria, gerada pelo administrador, que
-- precisa ser trocada no primeiro acesso; enquanto valer, a API so atende
-- /auth/me, /auth/logout e /auth/password.
--
-- credentials_changed_at: instante da ultima troca ou redefinicao de senha.
-- Sessao autenticada antes dele e encerrada na requisicao seguinte (D21), o
-- que faz a troca de senha derrubar sessoes abertas em outros navegadores.
ALTER TABLE app_user ADD COLUMN password_change_required boolean NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN credentials_changed_at timestamptz;
