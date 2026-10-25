# 06 — Regras de negócio

## 1. Conceitos

- **Company:** cadastro mestre de uma empresa.
- **Lead:** oportunidade comercial relacionada a uma empresa.
- **Source:** origem de uma informação ou registro.
- **Finding:** achado de uma análise.
- **Deal:** negociação comercial acompanhada no pipeline.
- **Interaction:** evento de relacionamento com o lead.
- **Outreach:** preparação e registro de uma abordagem.

Não tratar Company e Lead como sinônimos. Uma empresa pode existir sem oportunidade ativa; um lead representa uma intenção comercial concreta de avaliar ou oferecer uma solução.

## 2. Ciclo de vida do lead

Estados iniciais sugeridos:
- `NEW`
- `QUALIFIED`
- `CONTACT_APPROVED`
- `CONTACTED`
- `IN_CONVERSATION`
- `PROPOSAL_SENT`
- `WON`
- `LOST`
- `DO_NOT_CONTACT`

Regras:
- `WON` e `LOST` são estados terminais da oportunidade, mas um novo ciclo comercial pode exigir um novo lead.
- `DO_NOT_CONTACT` deve impedir novas abordagens abrangidas pelo pedido de não contato, mesmo que o lead seja reclassificado.
- Mudanças relevantes devem registrar ator, data e estado anterior/novo.
- O sistema não deve mover automaticamente um lead para `CONTACTED` ao gerar ou aprovar uma mensagem.
- Só registrar `CONTACTED` após o operador confirmar que o contato ocorreu.

## 3. Pipeline e Deal

O pipeline inicial pode conter:
1. Novo.
2. Qualificado.
3. Contato aprovado.
4. Contatado.
5. Em conversa.
6. Proposta enviada.
7. Ganho.
8. Perdido.

A implementação deve evitar duplicar conceitos desnecessariamente. `Lead.status` pode representar a qualificação geral; `Deal.stage` representa a etapa da negociação. Se o produto mantiver ambos, documentar a diferença e manter transições consistentes.

No MVP simples, é aceitável usar um pipeline principal e uma oportunidade ativa por lead. Se múltiplas vendas por empresa forem necessárias, criar Deals independentes sem duplicar Company.

## 4. Deduplicação

Normalização:
- remover espaços excedentes;
- normalizar caixa para comparação;
- normalizar telefone para formato consistente quando possível;
- normalizar domínio e URL;
- preservar valores originais.

Sinais de correspondência:
1. Identificador empresarial confiável e legitimamente coletado.
2. Domínio do site.
3. Telefone comercial.
4. Nome normalizado + cidade + estado.

Regras:
- correspondência exata de identificador confiável pode ser forte candidata;
- similaridade de nome é apenas sugestão;
- nunca mesclar automaticamente com base apenas em nome parecido;
- preservar fontes e histórico após uma mesclagem;
- permitir desfazer ou auditar a operação quando tecnicamente possível.

## 5. Proveniência e qualidade dos dados

Todo dado obtido de fonte externa deve registrar:
- fonte;
- referência/URL quando permitido;
- data da coleta;
- responsável ou integração;
- status de verificação.

Estados:
- confirmado;
- não encontrado;
- não verificado;
- não aplicável;
- contradito.

“Não encontrado” significa apenas que a pesquisa realizada não localizou a informação. Não significa prova de inexistência.

Dados antigos devem ser marcados como potencialmente desatualizados conforme política definida por tipo de informação.

## 6. Análise de presença digital

A análise deve observar sinais relevantes, por exemplo:
- existência e acessibilidade de site;
- HTTPS;
- viewport/responsividade observável;
- links de contato;
- informações básicas de serviços;
- presença de catálogo ou agendamento, quando aplicável.

Não inferir qualidade comercial, faturamento, intenção de compra ou problemas operacionais sem evidência.

Cada achado contém:
- critério;
- estado;
- descrição;
- evidência;
- confiança;
- pontos atribuídos;
- revisão humana.

A análise automatizada deve respeitar robots.txt, termos de uso, limites técnicos e legislação aplicável ao acessar páginas. Não contornar autenticação, bloqueios ou controles de acesso.

## 7. Lead Score

Score determinístico inicial, máximo 100:

| Critério | Pontos |
|---|---:|
| Ausência de site confirmada por método adequado | 25 |
| Problema técnico verificável no site | até 20 |
| Necessidade funcional observável e pertinente | até 20 |
| Informações comerciais suficientes para avaliação | até 15 |
| Compatibilidade com serviços oferecidos | até 10 |
| Localização atendida | até 10 |

Regras:
- Informação desconhecida não recebe automaticamente pontos de deficiência.
- Critério não aplicável não deve penalizar.
- Score deve registrar versão da fórmula.
- Mostrar decomposição dos pontos.
- Operador pode corrigir achados, o que pode recalcular o score.
- Score não é probabilidade de compra nem garantia de conversão.
- Não usar atributos sensíveis ou inferências pessoais na pontuação.

Faixas de prioridade sugeridas:
- 0–29: baixa.
- 30–59: média.
- 60–79: alta.
- 80–100: muito alta.

Essas faixas devem ser calibradas com dados reais e não devem ser apresentadas como avaliação objetiva da empresa.

## 8. Mensagens e aprovação

- Mensagens geradas por IA começam como `DRAFT` ou `NEEDS_REVIEW`.
- A IA só pode usar dados e achados disponíveis no contexto.
- Não inventar relacionamento prévio, observação pessoal ou informação sobre a empresa.
- Não afirmar que o operador visitou, testou ou analisou algo que não realizou.
- Toda mensagem deve identificar claramente o remetente e o motivo do contato.
- Operador revisa conteúdo e destinatário.
- Nenhum envio externo automático no MVP.
- Registrar resultado somente com confirmação do operador ou retorno confiável da integração.

## 9. Não contato

- Registrar pedido de não contato prontamente.
- Bloquear novas abordagens no canal/identificador e escopo aplicáveis.
- Verificar bloqueio antes de gerar, aprovar e registrar novos contatos.
- Não remover bloqueio sem processo autorizado e justificativa documentada.
- Manter somente os dados mínimos necessários para respeitar a restrição.

## 10. Website Studio

- Prévia é uma demonstração, não um site oficial.
- Usar apenas informações públicas verificadas ou fornecidas/autorizadas.
- Não utilizar logotipos, fotografias ou materiais de terceiros sem autorização/licença apropriada.
- Não publicar em domínio associado à empresa sem autorização.
- Não representar a demonstração como encomendada ou aprovada pelo estabelecimento.
- Projeto contratado deve ter escopo e autorização registrados.
- Código e templates devem ser controlados, versionados e revisados.

## 11. IA — limites funcionais

IA pode:
- resumir achados;
- sugerir oportunidades;
- gerar rascunhos;
- estruturar conteúdo de demonstração.

IA não pode:
- enviar mensagens;
- alterar permissões;
- decidir base legal;
- inventar evidências;
- publicar site;
- executar código gerado;
- eliminar registros;
- remover bloqueios de contato.

Saídas devem ser validadas e revisáveis.
