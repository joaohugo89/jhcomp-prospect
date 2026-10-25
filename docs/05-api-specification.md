# 05 — Especificação inicial da API

## 1. Convenções

- Base path: `/api/v1`.
- JSON UTF-8.
- UUID em identificadores.
- Datas em ISO 8601.
- Paginação com `page`, `size` e `sort`.
- Erros em formato consistente.
- Autenticação via sessão/token seguro conforme implementação.
- Organização resolvida no contexto autenticado, nunca confiada ao corpo da requisição.

Exemplo de erro:

```json
{
  "type": "VALIDATION_ERROR",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/companies",
  "errors": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

## 2. Autenticação

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/auth/login` | Autenticar |
| POST | `/auth/logout` | Encerrar sessão, se aplicável |
| POST | `/auth/refresh` | Renovar credencial, se adotado |
| GET | `/auth/me` | Usuário e organização ativa |

Não armazenar tokens de longa duração em `localStorage` sem avaliação de risco. Definir estratégia de refresh, cookies `HttpOnly`, `Secure`, `SameSite` e proteção CSRF conforme arquitetura de autenticação escolhida.

## 3. Dashboard

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/dashboard/summary` | Indicadores gerais |
| GET | `/dashboard/pipeline` | Contagem por etapa |
| GET | `/dashboard/activity` | Atividades recentes |

Parâmetros opcionais: `from`, `to`, `segment`, `city`.

## 4. Empresas

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/companies` | Criar empresa |
| GET | `/companies` | Listar e filtrar |
| GET | `/companies/{companyId}` | Detalhar |
| PATCH | `/companies/{companyId}` | Atualizar |
| POST | `/companies/{companyId}/archive` | Arquivar |

Exemplo de criação:

```json
{
  "name": "Empresa Exemplo",
  "tradeName": "Exemplo",
  "segment": "BARBERSHOP",
  "city": "Poços de Caldas",
  "state": "MG",
  "websiteUrl": null,
  "instagramUrl": null,
  "phone": null
}
```

## 5. Leads

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/leads` | Criar lead |
| GET | `/leads` | Listar e filtrar |
| GET | `/leads/{leadId}` | Detalhar |
| PATCH | `/leads/{leadId}` | Atualizar dados permitidos |
| PATCH | `/leads/{leadId}/status` | Alterar status |
| PATCH | `/leads/{leadId}/priority` | Alterar prioridade |
| GET | `/leads/{leadId}/timeline` | Histórico consolidado |

Filtros sugeridos: `status`, `priority`, `segment`, `city`, `scoreMin`, `scoreMax`, `source`, `search`.

## 6. Importação

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/imports/csv` | Enviar CSV |
| GET | `/imports/{importId}` | Consultar status |
| GET | `/imports/{importId}/report` | Consultar erros e resultados |

Para arquivos grandes, usar processamento assíncrono. Validar tamanho, encoding, colunas e conteúdo. Não executar fórmulas de planilha; proteger exportações contra CSV injection.

## 7. Discovery

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/discovery/searches` | Criar pesquisa |
| GET | `/discovery/searches` | Listar pesquisas |
| GET | `/discovery/searches/{searchId}` | Consultar resultado |
| POST | `/discovery/searches/{searchId}/import` | Importar resultados selecionados |

Exemplo de critérios:

```json
{
  "city": "Poços de Caldas",
  "state": "MG",
  "segment": "BARBERSHOP",
  "limit": 20,
  "source": "MANUAL"
}
```

A API deve rejeitar fontes não configuradas ou não autorizadas. O limite é um limite de interface/consulta, não autorização para coleta ilimitada.

## 8. Análises

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/leads/{leadId}/analyses` | Solicitar análise |
| GET | `/leads/{leadId}/analyses` | Listar análises |
| GET | `/analyses/{analysisId}` | Detalhar análise |
| PATCH | `/findings/{findingId}/review` | Revisar achado |

Solicitações demoradas devem retornar `202 Accepted` com identificador de job ou análise.

## 9. CRM

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/pipelines` | Listar pipelines |
| GET | `/pipeline-stages` | Listar etapas |
| POST | `/deals` | Criar oportunidade |
| PATCH | `/deals/{dealId}/stage` | Mover etapa |
| POST | `/leads/{leadId}/interactions` | Registrar interação |
| GET | `/leads/{leadId}/interactions` | Listar interações |
| POST | `/tasks` | Criar tarefa |
| PATCH | `/tasks/{taskId}` | Atualizar tarefa |

## 10. Outreach

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/leads/{leadId}/messages/generate` | Gerar rascunho |
| GET | `/leads/{leadId}/messages` | Listar mensagens |
| PATCH | `/messages/{messageId}` | Editar rascunho |
| POST | `/messages/{messageId}/approve` | Aprovar |
| POST | `/messages/{messageId}/record-send` | Registrar envio confirmado |
| POST | `/messages/{messageId}/cancel` | Cancelar |
| POST | `/suppression-entries` | Registrar não contato |
| GET | `/suppression-entries` | Consultar bloqueios autorizados |

A ação `record-send` registra envio já realizado; não deve abrir uma integração de disparo oculto. Integrações oficiais futuras devem possuir endpoints e controles próprios.

## 11. Website Studio

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/website-templates` | Listar templates |
| GET | `/website-templates/{templateId}` | Detalhar template |
| POST | `/website-projects` | Criar projeto/prévia |
| GET | `/website-projects/{projectId}` | Consultar projeto |
| PATCH | `/website-projects/{projectId}` | Atualizar configuração |
| POST | `/website-projects/{projectId}/preview` | Gerar prévia |

## 12. Segurança dos endpoints

- Autenticação em todas as rotas privadas.
- Autorização por papel e organização.
- Validar ownership em cada recurso.
- Rate limit em login, pesquisa e geração de IA.
- Limites de payload e upload.
- Timeouts nas integrações.
- Não retornar stack trace.
- Não registrar tokens, senhas ou conteúdo pessoal desnecessário.
- OpenAPI deve refletir os contratos reais implementados.

## 13. Contratos e evolução

Os exemplos deste documento são contratos iniciais. Antes de implementar cada módulo:
1. Definir DTOs completos.
2. Definir enums e erros.
3. Definir regras de autorização.
4. Atualizar OpenAPI.
5. Escrever testes de contrato/integrados.
6. Atualizar este documento se houver mudança relevante.
