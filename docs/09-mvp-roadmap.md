# 09 — Roadmap do MVP

## 1. Estratégia

Construir em incrementos utilizáveis. O sistema deve começar a apoiar a operação comercial antes da conclusão de módulos avançados.

Estimativa inicial: 6 sprints, aproximadamente 6–8 semanas de trabalho individual em dedicação consistente. É uma estimativa de planejamento, não compromisso de prazo; integrações externas e ajustes de escopo podem alterá-la.

## 2. Prioridades

### P0 — MVP obrigatório
- autenticação;
- organização;
- empresas e leads;
- importação CSV;
- deduplicação;
- proveniência;
- análise com evidências;
- score explicável;
- pipeline;
- rascunho de mensagem e revisão;
- registro de interação;
- lista de não contato;
- dashboard básico.

### P1 — MVP ampliado
- integração inicial com fonte autorizada;
- tarefas;
- templates e prévia simples;
- filtros e relatórios adicionais.

### P2 — Futuro
- integrações oficiais de mensageria;
- publicação de sites;
- editor visual;
- SaaS billing;
- automações avançadas;
- aplicativo mobile.

## 3. Sprint 0 — Preparação

Entregas:
- criar repositórios;
- definir convenções;
- configurar CI;
- escolher versões estáveis;
- preparar Docker Compose;
- revisar contratos iniciais;
- criar backlog.

Critério de conclusão:
- frontend e backend iniciam localmente;
- PostgreSQL acessível;
- build e testes executam no CI.

## 4. Sprint 1 — Fundação

Entregas:
- estrutura modular;
- migrations iniciais;
- Organization, User e OrganizationMember;
- autenticação;
- autorização;
- contexto de organização;
- tratamento global de erros.

Critério de conclusão:
- usuário autenticado acessa apenas a organização autorizada;
- testes negativos de isolamento passam.

## 5. Sprint 2 — Empresas e leads

Entregas:
- CRUD de empresas;
- CRUD de leads;
- listagem paginada;
- filtros;
- importação CSV;
- relatório de importação;
- deduplicação assistida;
- proveniência.

Critério de conclusão:
- importar arquivo de teste;
- identificar duplicatas potenciais;
- revisar e persistir registros válidos;
- consultar histórico de origem.

## 6. Sprint 3 — Inteligência

Entregas:
- modelo de análise;
- achados e evidências;
- estados de verificação;
- análise inicial manual/assistida;
- score determinístico;
- revisão de achados;
- job assíncrono para tarefas demoradas.

Integração de fonte:
- selecionar fornecedor após validar cobertura regional, preço, termos e armazenamento;
- implementar um adaptador;
- não acoplar o domínio ao fornecedor.

Critério de conclusão:
- uma análise mostra evidências, estado, confiança e versão do score;
- informação desconhecida não é tratada como deficiência confirmada.

## 7. Sprint 4 — CRM

Entregas:
- pipeline padrão;
- Kanban;
- deals;
- interações;
- tarefas básicas, se houver tempo;
- dashboard.

Critério de conclusão:
- operador acompanha lead do estado inicial à conclusão;
- alterações e interações aparecem no histórico.

## 8. Sprint 5 — IA e Outreach

Entregas:
- interface AIProvider;
- provedor inicial;
- prompts versionados;
- geração de rascunhos;
- validação de saída;
- edição e aprovação;
- registro manual de envio;
- suppression list;
- bloqueio antes de nova abordagem.

Critério de conclusão:
- IA gera uma mensagem com base apenas em contexto disponível;
- operador revisa;
- sistema impede abordagem quando há bloqueio;
- nenhum envio é disparado automaticamente.

## 9. Sprint 6 — Website Studio e lançamento

Entregas:
- catálogo de templates;
- configuração de template;
- prévia básica;
- indicação de demonstração não oficial;
- testes end-to-end do fluxo principal;
- documentação;
- deploy;
- backup e restauração testados.

Critério de conclusão:
- operador cria e visualiza uma demonstração usando dados autorizados;
- ambiente de produção está protegido e monitorado.

## 10. Primeiro marco comercial

Validar com aproximadamente 30 empresas reais da região, obtidas de fontes permitidas ou cadastradas manualmente.

Para cada registro:
- fonte e data;
- segmento e localização;
- dados confirmados;
- oportunidade observada;
- revisão do operador;
- decisão de abordar ou não;
- resultado da interação.

Avaliar:
- qualidade dos dados;
- tempo de pesquisa;
- utilidade do score;
- relevância das mensagens;
- respostas e conversas iniciadas;
- segmentos com melhor aderência.

Não considerar quantidade de mensagens enviadas como indicador isolado de sucesso.

## 11. Definition of Done

Uma história só está concluída quando:
- regras de negócio implementadas;
- validação de entrada;
- autorização;
- tratamento de erros;
- testes adequados;
- documentação atualizada;
- revisão de segurança/privacidade quando aplicável;
- interface com estados de carregamento, erro e vazio;
- logs sem dados sensíveis desnecessários.

## 12. Backlog pós-MVP

- melhorar filtros e segmentação;
- novas fontes autorizadas;
- atualização programada de dados;
- análise técnica mais profunda de sites;
- propostas comerciais;
- integração oficial com e-mail/WhatsApp, após revisão de requisitos;
- templates adicionais;
- publicação controlada;
- organizações externas e planos SaaS.
