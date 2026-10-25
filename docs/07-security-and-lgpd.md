# 07 — Segurança, privacidade e LGPD

> Este documento é uma especificação técnica de controles. Não substitui avaliação jurídica. A base legal e os procedimentos de tratamento devem ser validados conforme o contexto real da operação.

## 1. Princípios

- Finalidade específica e legítima.
- Adequação e necessidade.
- Transparência.
- Qualidade e atualização dos dados.
- Segurança e prevenção.
- Não discriminação.
- Prestação de contas.

Dados comerciais de uma empresa podem incluir dados pessoais quando identificam pessoas naturais, como profissionais autônomos, sócios ou contatos individuais. Não assumir que todo dado disponível publicamente está livre de obrigações legais.

## 2. Inventário de dados

Para cada categoria, documentar:
- dado coletado;
- finalidade;
- origem;
- titular ou categoria de titular;
- base legal avaliada;
- acesso interno;
- fornecedor envolvido;
- prazo de retenção;
- procedimento de correção/exclusão.

Coletar somente o necessário para avaliar e conduzir a oportunidade comercial.

## 3. Base legal e prospecção

Não presumir que legítimo interesse autoriza qualquer prospecção.

Quando essa hipótese for avaliada, documentar:
- finalidade legítima;
- necessidade do dado;
- expectativa razoável do titular;
- impacto sobre direitos e liberdades;
- medidas de mitigação;
- transparência e mecanismo de oposição.

Se outra base legal for aplicável, registrar a justificativa correspondente. Consentimento não deve ser solicitado artificialmente nem presumido por disponibilidade de contato.

Mensagens devem ser relevantes, identificáveis e respeitar pedidos de não contato. Verificar regras específicas de cada canal.

## 4. Segurança de autenticação

- Hash de senha com algoritmo apropriado, como Argon2id ou BCrypt configurado adequadamente.
- Nunca armazenar senha em texto puro.
- Rate limiting e proteção contra tentativa automatizada.
- Respostas genéricas em falhas de login.
- Política de recuperação segura.
- Revogação/expiração de sessões.
- Cookies seguros se essa estratégia for adotada.
- MFA pode ser adicionado em evolução.

## 5. Autorização e isolamento

- Contexto de organização derivado da identidade autenticada.
- Verificar organização em toda leitura e escrita de recurso privado.
- Verificar ownership de relações entre Company, Lead, Deal e WebsiteProject.
- Não confiar em `organization_id` recebido do frontend.
- Testes automatizados de acesso cruzado.
- Papéis com menor privilégio necessário.
- Ações administrativas auditadas.

## 6. Proteção de dados

- HTTPS em produção.
- Criptografia em repouso quando suportada pela infraestrutura.
- Segredos em secret manager ou variáveis protegidas.
- Não versionar `.env`, tokens ou chaves.
- Não registrar conteúdo de mensagens ou dados pessoais sem necessidade.
- Restringir acesso a exportações.
- Proteger arquivos importados e limitar tamanho/tipo.
- Validar URLs para reduzir riscos de SSRF em verificações de sites.
- Bloquear acesso a IPs privados, loopback e endereços de metadata em qualquer fetcher de URL.
- Aplicar timeout, limite de redirecionamentos e tamanho de resposta.

## 7. IA e fornecedores

- Enviar ao provedor somente dados necessários.
- Revisar termos de retenção e uso dos dados do fornecedor.
- Não enviar credenciais, dados sensíveis ou informações desnecessárias.
- Tratar resposta do modelo como não confiável.
- Validar JSON/schema de saída.
- Limitar tamanho e custo.
- Registrar provedor/modelo e versão do prompt quando necessário.
- Não permitir que o modelo invoque ferramentas de envio ou publicação no MVP.
- Separar instruções do sistema dos dados externos para reduzir prompt injection.
- Conteúdo de páginas externas deve ser tratado como dado não confiável, nunca como instrução para o sistema.

## 8. Fontes externas

- Usar APIs oficiais ou fontes com permissão de acesso.
- Verificar termos, licenças, atribuição e regras de armazenamento.
- Respeitar limites e quotas.
- Não contornar login, paywall, CAPTCHA, bloqueio ou controles técnicos.
- Não fazer scraping de interfaces quando proibido.
- Registrar fonte e data de coleta.
- Não reutilizar dados para finalidade incompatível.

## 9. Retenção e direitos dos titulares

Definir política documentada para:
- revisão e atualização;
- retenção;
- exclusão ou anonimização;
- acesso e correção;
- oposição/descadastramento;
- atendimento de solicitações;
- preservação mínima necessária para respeitar bloqueios de contato.

Prazos específicos devem ser definidos após análise jurídica e operacional. Não manter dados indefinidamente por conveniência.

## 10. Lista de não contato

A lista deve:
- armazenar identificador mínimo necessário;
- restringir acesso;
- registrar motivo e data;
- ser consultada antes de qualquer abordagem;
- impedir que uma nova importação reative contato bloqueado;
- possuir trilha de auditoria.

## 11. Auditoria e incidentes

Auditar:
- login e eventos relevantes de segurança;
- criação e alteração de permissões;
- exportações;
- alteração de bloqueios;
- aprovação e registro de contatos;
- publicação de projetos;
- operações administrativas.

Plano de incidente:
1. identificar e conter;
2. preservar evidências;
3. avaliar escopo e impacto;
4. corrigir causa;
5. avaliar obrigações de comunicação;
6. documentar e revisar controles.

## 12. Checklist de lançamento

- [ ] Inventário de dados e finalidades.
- [ ] Bases legais avaliadas para cada fluxo.
- [ ] Aviso de privacidade adequado.
- [ ] Política de retenção definida.
- [ ] Processo de não contato testado.
- [ ] Isolamento multi-tenant testado.
- [ ] Segredos fora do repositório.
- [ ] Backups e restauração testados.
- [ ] Fetcher de URLs protegido contra SSRF.
- [ ] Fornecedores e termos revisados.
- [ ] Logs revisados para evitar exposição.
- [ ] Procedimento de incidentes documentado.
