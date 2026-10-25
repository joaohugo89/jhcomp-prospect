# 02 — Requisitos funcionais

## 1. Convenções

Prioridades:
- **P0:** indispensável para o MVP.
- **P1:** importante, pode entrar após o fluxo principal.
- **P2:** evolução futura.

Cada requisito deve ser implementado com validação de entrada, autorização e testes relevantes.

## 2. Identidade e organização

### RF-001 — Autenticação (P0)
O sistema deve permitir autenticação de usuários por credenciais.
**Aceite:** credenciais válidas permitem acesso; inválidas são rejeitadas sem revelar se o e-mail existe.

### RF-002 — Organização (P0)
Cada usuário autenticado deve operar dentro de uma organização ativa.
**Aceite:** a organização é resolvida no contexto de segurança do backend; o cliente não pode selecionar arbitrariamente outra organização.

### RF-003 — Membros e papéis (P1)
O sistema deve suportar membros com papéis administrativos e operacionais.
**Aceite:** operações administrativas são bloqueadas para operadores sem permissão.

## 3. Empresas e leads

### RF-010 — Cadastro de empresa (P0)
Permitir criar, consultar, atualizar e arquivar empresas.
Campos iniciais: nome, nome comercial, segmento, cidade, estado, site, redes sociais, telefone comercial, endereço comercial quando necessário e observações.
**Aceite:** campos obrigatórios são validados e os dados ficam associados à organização.

### RF-011 — Cadastro de lead (P0)
Permitir criar uma oportunidade comercial associada a uma empresa.
**Aceite:** um lead deve possuir status, origem e data de criação.

### RF-012 — Listagem e filtros (P0)
Permitir pesquisar e filtrar por nome, segmento, cidade, status, prioridade, score e origem.
**Aceite:** listagem paginada, ordenação permitida e filtros combináveis.

### RF-013 — Histórico (P0)
Exibir alterações e interações relevantes associadas ao lead.
**Aceite:** o histórico é cronológico e não pode ser alterado por usuários sem permissão.

### RF-014 — Deduplicação (P0)
Detectar possíveis empresas duplicadas usando identificadores disponíveis.
**Aceite:** o sistema sugere correspondências e permite revisão antes de mesclar; não descarta registros silenciosamente.

### RF-015 — Importação CSV (P0)
Permitir importar CSV com mapeamento de colunas, validação e relatório.
**Aceite:** mostrar total lido, importado, rejeitado e possível duplicidade; erros por linha devem ser consultáveis.

## 4. Descoberta e fontes

### RF-020 — Pesquisa manual (P0)
Permitir registrar empresas encontradas pelo operador.
**Aceite:** origem, URL ou referência da fonte, data de coleta e responsável são registrados quando disponíveis.

### RF-021 — Pesquisa por fonte integrada (P1)
Permitir executar pesquisas em uma fonte externa cuja API e termos tenham sido avaliados.
**Aceite:** integração isolada por adaptador; falhas e limites do fornecedor são apresentados claramente.

### RF-022 — Proveniência (P0)
Cada dado importado ou enriquecido deve registrar sua origem e momento de coleta.
**Aceite:** o usuário consegue identificar de onde veio um dado e quando foi atualizado.

### RF-023 — Atualização de dados (P1)
Permitir atualizar dados por nova consulta ou revisão manual.
**Aceite:** preservar histórico mínimo de alterações relevantes e não sobrescrever silenciosamente informação confirmada por outra fonte.

## 5. Análise e qualificação

### RF-030 — Solicitar análise (P0)
Permitir solicitar análise de presença digital de uma empresa.
**Aceite:** análise possui status (pendente, em processamento, concluída ou falha) e data.

### RF-031 — Achados com evidência (P0)
Cada achado deve conter descrição, estado de verificação, evidência/referência, confiança e data.
**Aceite:** achados não verificados não podem ser exibidos como fatos confirmados.

### RF-032 — Score explicável (P0)
Calcular score determinístico de 0 a 100 com base em critérios configurados.
**Aceite:** exibir critérios e pontos atribuídos; ausência de informação não equivale automaticamente a uma deficiência.

### RF-033 — Revisão humana (P0)
Permitir corrigir, confirmar ou rejeitar achados.
**Aceite:** revisão registra usuário e data.

## 6. CRM

### RF-040 — Pipeline (P0)
Exibir leads em quadro Kanban.
**Aceite:** mover lead entre etapas respeitando transições válidas e permissões.

### RF-041 — Interações (P0)
Registrar contato, resposta, reunião, proposta, pedido de não contato e outras interações.
**Aceite:** cada interação registra canal, data, resultado e observações pertinentes.

### RF-042 — Tarefas (P1)
Criar tarefas vinculadas a leads.
**Aceite:** tarefa possui título, prazo, status e responsável opcional.

### RF-043 — Ganho/perdido (P0)
Permitir encerrar oportunidade como ganha ou perdida.
**Aceite:** negócio perdido pode registrar motivo; negócio ganho pode registrar valor e serviço contratado, se conhecidos.

## 7. Outreach

### RF-050 — Gerar mensagem (P0)
Gerar rascunho contextualizado usando dados e achados aprovados.
**Aceite:** mensagem é salva como rascunho; IA não pode enviar diretamente.

### RF-051 — Revisar mensagem (P0)
Permitir editar, aprovar ou descartar rascunho.
**Aceite:** somente mensagem aprovada pode ser marcada como pronta para uso externo.

### RF-052 — Registrar envio (P0)
Permitir registrar envio realizado manualmente ou por integração habilitada.
**Aceite:** registrar canal, data, responsável e resultado; não afirmar envio se ele não foi confirmado.

### RF-053 — Não contato (P0)
Permitir registrar pedido de não contato e bloquear novas abordagens comerciais não permitidas.
**Aceite:** o bloqueio é aplicado antes da geração/aprovação/envio de novas mensagens.

### RF-054 — Integração de canal (P2)
Integrar canais por APIs oficiais e autorizadas.
**Aceite:** respeitar requisitos do fornecedor, políticas de canal e controles de privacidade.

## 8. Website Studio

### RF-060 — Templates (P1)
Listar templates disponíveis por segmento.
**Aceite:** cada template possui identificador, versão, descrição e campos configuráveis.

### RF-061 — Criar prévia (P1)
Criar uma prévia baseada em template e dados aprovados.
**Aceite:** prévia é identificada como demonstração e não é publicada como site oficial sem autorização.

### RF-062 — Projeto contratado (P2)
Criar projeto de website após contratação.
**Aceite:** projeto registra escopo, template, requisitos, revisões e status.

## 9. Dashboard

### RF-070 — Resumo (P0)
Exibir contagens de leads por etapa, atividades recentes e negócios.
**Aceite:** indicadores respeitam o escopo da organização e filtros de período.

### RF-071 — Conversão (P1)
Exibir taxas por origem e segmento quando houver amostra suficiente.
**Aceite:** denominadores e período são claros; não apresentar percentuais enganosos com poucos dados.

## 10. Requisitos não funcionais relacionados

- **RNF-001:** todas as operações comerciais devem respeitar isolamento por organização.
- **RNF-002:** listagens devem ser paginadas.
- **RNF-003:** erros devem possuir formato consistente e não expor stack traces.
- **RNF-004:** operações externas demoradas devem ser assíncronas.
- **RNF-005:** integrações externas devem ter timeout e tratamento de falhas.
- **RNF-006:** segredos não podem ser armazenados no código-fonte.
- **RNF-007:** logs devem evitar conteúdo pessoal e credenciais.
- **RNF-008:** ações críticas devem gerar trilha de auditoria.
- **RNF-009:** datas devem ser persistidas de forma consistente (preferencialmente UTC) e exibidas no fuso configurado.
- **RNF-010:** o frontend deve ser responsivo e acessível por teclado nas operações principais.
