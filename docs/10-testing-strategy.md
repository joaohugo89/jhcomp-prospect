# 10 — Estratégia de testes

## 1. Objetivos

Garantir:
- integridade das regras comerciais;
- isolamento entre organizações;
- qualidade de importação;
- rastreabilidade de dados;
- segurança do fluxo de outreach;
- consistência das análises;
- estabilidade das integrações;
- usabilidade do fluxo principal.

## 2. Pirâmide de testes

### Unitários
Ferramentas: JUnit 5 e Mockito.

Cobrir:
- normalização de empresas;
- deduplicação;
- transições de status;
- cálculo do score;
- bloqueio de não contato;
- validação de mensagens;
- regras de pipeline;
- políticas de autorização em serviços.

Testes unitários devem ser rápidos e não depender de rede ou serviços externos.

### Integração
Ferramentas: Spring Boot Test e Testcontainers com PostgreSQL.

Cobrir:
- repositories;
- migrations Flyway;
- constraints;
- transações;
- filtros por organização;
- importação;
- consultas paginadas;
- endpoints e serialização;
- persistência de histórico.

Preferir PostgreSQL real em container para testes de persistência, em vez de depender apenas de H2.

### Frontend
Ferramentas a selecionar conforme projeto, por exemplo Vitest e React Testing Library.

Cobrir:
- formulários;
- validação;
- filtros;
- estados vazios e de erro;
- componentes de score;
- pipeline;
- revisão de mensagem;
- acessibilidade básica.

### End-to-end
Ferramenta a selecionar, por exemplo Playwright.

Fluxos:
1. login;
2. cadastrar empresa;
3. criar lead;
4. importar CSV;
5. revisar duplicata;
6. analisar lead;
7. mover pipeline;
8. gerar e revisar mensagem;
9. registrar interação;
10. registrar não contato;
11. confirmar bloqueio de nova abordagem;
12. criar prévia de site.

## 3. Testes críticos de isolamento

Criar duas organizações de teste, A e B.

Verificar que:
- usuário A não lista empresas de B;
- usuário A não acessa lead de B por UUID;
- usuário A não altera deal de B;
- usuário A não acessa mensagens de B;
- importações e jobs mantêm o escopo correto;
- filtros e dashboard não misturam dados;
- relações entre entidades de organizações diferentes são rejeitadas.

Testar tanto respostas HTTP quanto efeitos no banco.

## 4. Testes de deduplicação

Casos:
- mesmo domínio e nome;
- telefone com formatações diferentes;
- nome com acentos e espaços;
- empresas homônimas em cidades diferentes;
- nome parecido, mas empresas distintas;
- registro sem site ou telefone;
- importação repetida do mesmo arquivo.

Critério:
- duplicatas fortes são sinalizadas;
- casos ambíguos exigem revisão;
- nenhum registro é mesclado silenciosamente apenas por similaridade textual.

## 5. Testes do score

- score mínimo 0;
- score máximo 100;
- soma de critérios correta;
- desconhecido não pontua como deficiência;
- critério não aplicável tratado conforme regra;
- versão da fórmula persistida;
- alterações em achados recalculam score de maneira reproduzível;
- resultado exibe decomposição.

## 6. Testes de outreach e privacidade

- mensagem gerada inicia como rascunho;
- IA não possui caminho de envio;
- mensagem exige revisão;
- mensagem aprovada não equivale a enviada;
- envio só é registrado após confirmação;
- pedido de não contato bloqueia novos contatos;
- importação não remove bloqueios;
- bloqueio é consultado antes de aprovar ou registrar abordagem;
- logs não expõem conteúdo sensível desnecessário.

## 7. Testes de integrações

Usar mocks/stubs para:
- timeout;
- indisponibilidade;
- rate limit;
- resposta inválida;
- dados parciais;
- duplicação de resposta;
- falha de autenticação;
- quota excedida.

Não executar testes automatizados contra produção sem autorização e limites definidos.

## 8. Segurança

Incluir verificações para:
- validação de entrada;
- autorização por recurso;
- CSRF conforme estratégia de autenticação;
- XSS em conteúdo renderizado;
- SQL injection;
- SSRF em verificação de URLs;
- upload de arquivos;
- rate limiting;
- exposição de segredos;
- exportação CSV e formula injection;
- dependências vulneráveis.

## 9. CI

Pipeline mínimo:
1. format/lint;
2. compilação;
3. testes unitários;
4. testes de integração;
5. build frontend;
6. testes frontend;
7. análise de dependências;
8. build de imagens, quando aplicável.

Bloquear merge quando testes obrigatórios falharem.

## 10. Dados de teste

- Usar dados fictícios em testes automatizados.
- Não colocar dados pessoais reais em fixtures.
- Não copiar listas reais de empresas para o repositório.
- Não utilizar credenciais de produção em CI.
- Mascarar dados em ambientes não produtivos.
