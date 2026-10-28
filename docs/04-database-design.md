# 04 — Modelo de dados

## 1. Convenções

- Chaves primárias UUID.
- `created_at` e `updated_at` em entidades mutáveis.
- Timestamps persistidos em UTC.
- `organization_id` obrigatório em dados privados de organização.
- Índices para filtros frequentes.
- Soft delete apenas quando houver necessidade de negócio e política de retenção definida; não usar como substituto de exclusão legalmente necessária.
- Valores monetários em `NUMERIC`, nunca `FLOAT`.
- Enums persistidos como strings ou tabelas de domínio, com migrações controladas.

## 2. Entidades

### Organization
- `id`
- `name`
- `slug`
- `status`
- `created_at`
- `updated_at`

### User
- `id`
- `email`
- `password_hash`
- `display_name`
- `status`
- `created_at`
- `updated_at`

### OrganizationMember
- `id`
- `organization_id`
- `user_id`
- `role`
- `status`
- `created_at`

Restrição única: `(organization_id, user_id)`.

### Company
- `id`
- `organization_id`
- `name`
- `normalized_name` (D4: persistido, para a deduplicação usar índice)
- `trade_name` nullable
- `segment` nullable
- `city`
- `state`
- `website_url` nullable
- `instagram_url` nullable
- `normalized_domain` nullable (D10)
- `phone` nullable
- `normalized_phone` nullable, E.164 (D10)
- `business_email` nullable
- `address` nullable
- `notes` nullable
- `archived_at` nullable
- `created_at`
- `updated_at`

As colunas normalizadas são preenchidas pelo componente único de normalização (D10), o mesmo usado pela lista de não contato.
Não há verificação por campo em `Company`: o estado de verificação vive na análise e em `LeadSource` (D12).

Índices sugeridos:
- `(organization_id, normalized_name, city, state)`
- `(organization_id, segment)`
- `(organization_id, city, state)`

`normalized_name` é persistido (D4) e nunca substitui nem é exibido no lugar do nome original.

### Lead
- `id`
- `organization_id`
- `company_id`
- `status`
- `priority`
- `score` nullable
- `score_version` nullable
- `assigned_user_id` nullable
- `created_at`
- `updated_at`
- `closed_at` nullable

Um lead representa uma oportunidade comercial, não necessariamente uma empresa única. A política para múltiplas oportunidades ativas da mesma empresa deve ser definida pelo produto.

### LeadSource
- `id`
- `organization_id`
- `lead_id` nullable
- `company_id` obrigatório (D6)
- `source_type`
- `source_name`
- `source_reference` nullable
- `source_url` nullable
- `collected_at`
- `collected_by_user_id` nullable
- `terms_or_dataset_note` nullable

Registrar a origem sem copiar conteúdo protegido ou dados além do necessário.

### CompanyContact

**Fora do MVP (D5).** O contato principal fica em `Company`. Reintroduzir quando houver necessidade real de vários contatos por empresa.

- `id`
- `organization_id`
- `company_id`
- `contact_type`
- `value`
- `label` nullable
- `is_public_business_contact`
- `source_id` nullable
- `verified_at` nullable
- `created_at`

Acesso e retenção devem considerar se o contato identifica pessoa natural.

### LeadAnalysis
- `id`
- `organization_id`
- `lead_id`
- `analysis_type`
- `status`
- `score` nullable
- `confidence` nullable
- `summary` nullable
- `provider` nullable
- `model` nullable
- `prompt_version` nullable
- `started_at`
- `completed_at` nullable
- `created_at`

### AnalysisFinding
- `id`
- `organization_id`
- `analysis_id`
- `criterion`
- `finding_status`
- `description`
- `confidence` nullable
- `points_awarded` default 0
- `review_status`
- `reviewed_by_user_id` nullable
- `reviewed_at` nullable

Estados de achado sugeridos:
- `CONFIRMED`
- `NOT_FOUND`
- `NOT_VERIFIED`
- `NOT_APPLICABLE`
- `CONTRADICTED`

### AnalysisEvidence
- `id`
- `organization_id`
- `finding_id`
- `evidence_type`
- `reference_url` nullable
- `description`
- `observed_at`
- `captured_by`
- `content_hash` nullable

Evitar armazenar cópias integrais de páginas ou dados pessoais desnecessários. Uma URL e uma descrição concisa podem ser suficientes.

### Pipeline
- `id`
- `organization_id`
- `name`
- `is_default`
- `created_at`

### PipelineStage
- `id`
- `organization_id`
- `pipeline_id`
- `name`
- `stage_key`
- `position`
- `is_terminal`
- `created_at`

### Deal
- `id`
- `organization_id`
- `lead_id`
- `pipeline_id`
- `stage_id`
- `title`
- `estimated_value` nullable
- `currency` default `BRL`
- `outcome` nullable
- `loss_reason` nullable
- `created_at`
- `updated_at`
- `closed_at` nullable

### Interaction
- `id`
- `organization_id`
- `lead_id`
- `deal_id` nullable
- `interaction_type`
- `channel` nullable
- `direction` nullable
- `occurred_at`
- `summary`
- `created_by_user_id`
- `created_at`

### Task
- `id`
- `organization_id`
- `lead_id` nullable
- `deal_id` nullable
- `title`
- `description` nullable
- `due_at` nullable
- `status`
- `assigned_user_id` nullable
- `created_at`
- `updated_at`

### OutreachMessage
- `id`
- `organization_id`
- `lead_id`
- `channel`
- `content`
- `status`
- `generated_by` nullable
- `prompt_version` nullable
- `approved_by_user_id` nullable
- `approved_at` nullable
- `sent_at` nullable
- `provider_message_id` nullable
- `created_at`
- `updated_at`

Status sugeridos:
- `DRAFT`
- `NEEDS_REVIEW`
- `APPROVED`
- `SENT`
- `FAILED`
- `CANCELLED`

`SENT` só deve ser aplicado quando o envio tiver sido realizado e confirmado, não apenas quando o usuário abrir um aplicativo de mensagens.

### SuppressionEntry
- `id`
- `organization_id`
- `contact_identifier_normalized`
- `channel` nullable
- `reason`
- `requested_at`
- `created_at`

Armazenar o mínimo necessário para impedir novos contatos. Definir controles de acesso e retenção para essa lista.

### WebsiteTemplate
- `id`
- `template_key`
- `name`
- `segment` nullable
- `version`
- `is_active`
- `configuration_schema`
- `created_at`

Templates globais devem ser explicitamente separados dos templates privados de uma organização, se estes forem adicionados futuramente.

### WebsiteProject
- `id`
- `organization_id`
- `lead_id` nullable
- `company_id` nullable
- `template_id`
- `name`
- `project_type`
- `status`
- `configuration`
- `authorization_status`
- `created_at`
- `updated_at`

### WebsiteDeployment
- `id`
- `organization_id`
- `website_project_id`
- `environment`
- `deployment_url`
- `status`
- `deployed_at` nullable

### ImportBatch (D23)
- `id`
- `organization_id`
- `file_name`
- `file_sha256` (identifica reenvio do mesmo arquivo)
- `source_name`
- `dataset_note` nullable
- `column_mapping`, `ignored_columns`
- `status` (`ANALYZED`, `IMPORTING`, `COMPLETED`, `DISCARDED`)
- totais: `total_rows`, `valid_rows`, `rejected_rows`, `duplicate_rows`, `imported_rows`, `merged_rows`, `leads_created`
- `created_by_user_id`
- `created_at`
- `completed_at` nullable

### ImportRow (D23)
- `id`
- `organization_id`
- `import_id`
- `row_number`
- `status` (`VALID`, `REJECTED`, `DUPLICATE`, `IMPORTED`, `MERGED`, `SKIPPED`)
- campos mapeados da empresa (nulos após descarte)
- `errors` nullable
- `duplicate_of_company_id`, `duplicate_of_row_number`, `duplicate_signals` nullable
- `merged_fields` nullable
- `company_id`, `lead_id` nullable

Não guarda a linha bruta do arquivo nem colunas que não viram campo.

## 3. Relacionamentos

- Organization 1:N OrganizationMember.
- User 1:N OrganizationMember.
- Organization 1:N Company.
- Company 1:N Lead.
- Company 1:N CompanyContact (fora do MVP, D5).
- Company/Lead 1:N LeadSource.
- ImportBatch 1:N ImportRow (D23).
- Lead 1:N LeadAnalysis.
- LeadAnalysis 1:N AnalysisFinding.
- AnalysisFinding 1:N AnalysisEvidence.
- Lead 1:N Interaction.
- Lead 1:N OutreachMessage.
- Lead 1:N Task.
- Lead 1:N Deal, conforme regra comercial adotada.
- Pipeline 1:N PipelineStage.
- Deal N:1 PipelineStage.
- WebsiteTemplate 1:N WebsiteProject.
- WebsiteProject 1:N WebsiteDeployment.

## 4. Integridade e isolamento

- Chaves estrangeiras explícitas.
- Unicidade de associação de membro.
- Validar que entidades relacionadas pertencem à mesma organização.
- Não confiar em UUID enviado pelo frontend como prova de autorização.
- Consultas devem sempre incluir escopo da organização.
- Testar acesso cruzado entre organizações.
- Chaves estrangeiras compostas com `organization_id` impedem associações entre tenants diferentes (V4); toda tabela nova segue o mesmo padrão.

## 5. Deduplicação

A deduplicação deve usar uma combinação de:
- identificador empresarial público, quando legitimamente disponível e necessário;
- nome normalizado;
- cidade e estado;
- domínio do site;
- telefone comercial normalizado.

Não mesclar automaticamente apenas por similaridade de nome. O sistema deve apresentar candidatos e permitir confirmação humana.

## 6. Migrações

Usar Flyway:
- migrations versionadas e imutáveis após aplicadas;
- índices criados considerando consultas reais;
- dados iniciais mínimos para pipeline e papéis;
- nenhuma alteração manual em produção fora do processo de migração;
- testar migrações em banco PostgreSQL real via Testcontainers.
