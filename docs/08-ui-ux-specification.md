# 08 — Especificação de UI/UX

## 1. Objetivo da interface

A interface deve permitir que um operador execute o fluxo comercial rapidamente, com pouca fricção e contexto suficiente para tomar decisões.

Prioridades:
- clareza;
- densidade informacional controlada;
- filtros eficientes;
- estados de carregamento e erro explícitos;
- acessibilidade;
- responsividade;
- ações críticas com confirmação.

## 2. Direção visual

A identidade deve seguir a marca JH.COMP sem competir com os dados.

Direção inicial:
- interface administrativa moderna;
- tema escuro como opção, não obrigação;
- fundo neutro;
- cor primária consistente com a identidade da marca;
- cores de status com significado semântico;
- tipografia legível;
- espaçamento consistente;
- ícones com rótulos acessíveis.

Não escolher cores finais antes de validar o design system e os logotipos oficiais dos produtos JH.COMP.

## 3. Navegação

Menu lateral sugerido:

```text
JH.COMP Prospect
├── Dashboard
├── Prospecção
│   ├── Pesquisar empresas
│   ├── Importações
│   └── Histórico de pesquisas
├── Empresas
├── Leads
├── Pipeline
├── Outreach
├── Website Studio
└── Configurações
```

Configurações:
- perfil;
- organização;
- membros;
- integrações;
- preferências;
- privacidade e exportação.

Itens ainda não implementados devem ficar ocultos ou claramente identificados como indisponíveis, sem simular funcionalidades.

## 4. Dashboard

Exibir:
- total de leads ativos;
- leads por etapa;
- empresas adicionadas no período;
- contatos registrados;
- propostas abertas;
- negócios ganhos;
- atividades recentes;
- tarefas pendentes.

Filtros:
- período;
- segmento;
- cidade;
- origem.

Evitar gráficos sem dados suficientes. Mostrar estado vazio com orientação para cadastrar/importar os primeiros leads.

## 5. Pesquisa de empresas

Campos:
- cidade;
- estado;
- segmento;
- fonte;
- limite;
- filtros adicionais suportados pela fonte.

Resultados:
- nome;
- segmento;
- localização;
- site, se disponível;
- telefone comercial, se disponível;
- fonte;
- data de coleta;
- indicação de possível duplicata.

Ações:
- abrir detalhes;
- selecionar;
- importar como empresa/lead;
- ignorar;
- revisar possível duplicata.

Não exibir campos que a fonte não forneceu como se estivessem confirmados.

## 6. Lista de empresas e leads

Tabela com:
- nome;
- segmento;
- cidade;
- status;
- score;
- prioridade;
- origem;
- última atualização.

Recursos:
- busca textual;
- filtros combináveis;
- ordenação;
- paginação;
- seleção em lote apenas para ações seguras;
- exportação autorizada.

Ações em lote não podem enviar mensagens.

## 7. Detalhes do lead

Cabeçalho:
- nome da empresa;
- segmento;
- localização;
- status;
- prioridade;
- score;
- ações principais.

Abas:
1. Visão geral.
2. Presença digital.
3. Histórico.
4. Mensagens.
5. Negociação.
6. Projeto de site.

A seção de análise deve exibir:
- achado;
- estado de verificação;
- evidência;
- data;
- confiança;
- pontos;
- revisão humana.

## 8. Pipeline Kanban

- Colunas por etapa.
- Cards compactos.
- Nome da empresa.
- Serviço potencial.
- Score.
- Última interação.
- Próxima tarefa.

Permitir mover cards por drag-and-drop e por controles acessíveis. Toda alteração deve ser persistida e apresentar feedback de sucesso/erro.

## 9. Outreach

Tela de composição:
- canal;
- destinatário;
- contexto utilizado;
- rascunho;
- indicação de informações não verificadas;
- botão de regenerar;
- edição manual;
- aprovar;
- descartar.

Antes de aprovar:
- verificar bloqueio de não contato;
- exibir identidade do remetente;
- exibir origem/contexto da mensagem;
- permitir revisar destinatário e conteúdo.

O MVP não deve ter botão de disparo em massa.

## 10. Website Studio

Fluxo:
1. selecionar lead/empresa;
2. escolher template;
3. preencher dados autorizados;
4. personalizar tema;
5. gerar conteúdo estruturado;
6. visualizar;
7. salvar prévia.

A prévia deve mostrar claramente que é conceitual e não oficial. Não utilizar marca ou imagem de terceiros sem autorização apropriada.

## 11. Estados de interface

Todas as telas devem prever:
- carregando;
- vazio;
- sucesso;
- erro;
- sem permissão;
- sessão expirada;
- integração indisponível;
- resultado parcial;
- dados desatualizados.

Mensagens de erro devem ser úteis e não revelar detalhes internos.

## 12. Acessibilidade e responsividade

- navegação por teclado;
- foco visível;
- labels em campos;
- contraste adequado;
- mensagens de validação associadas aos campos;
- não depender apenas de cor para indicar status;
- tabelas utilizáveis em telas menores;
- formulários com ordem lógica;
- confirmação em ações destrutivas.

## 13. Componentes reutilizáveis

- AppShell;
- Sidebar;
- PageHeader;
- DataTable;
- FilterBar;
- StatusBadge;
- ScoreIndicator;
- EvidenceCard;
- ActivityTimeline;
- KanbanBoard;
- MessageComposer;
- EmptyState;
- ConfirmDialog;
- FormField;
- Pagination.

## 14. Princípio de produto

A interface deve priorizar a próxima ação útil: revisar um lead, validar um achado, responder a uma empresa ou preparar uma proposta. Não otimizar para volume de disparos.
