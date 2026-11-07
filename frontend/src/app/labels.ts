import type { AnalysisStatus, Confidence, Criterion, EvidenceType, FindingStatus } from '../types/analysis';
import type { DuplicateSignal, SourceType } from '../types/company';
import type { InteractionChannel, InteractionDirection, InteractionType } from '../types/crm';
import type { ImportField, ImportRowStatus } from '../types/import';
import type { LeadPriority, LeadStatus } from '../types/lead';
import type { IdentifierType, MessageStatus, OutreachChannel } from '../types/outreach';

/** Portuguese labels for the interface. API values stay in English: that is the contract. */

export const STATUS_LABEL: Readonly<Record<LeadStatus, string>> = {
  NEW: 'Novo',
  QUALIFIED: 'Qualificado',
  CONTACT_APPROVED: 'Contato aprovado',
  CONTACTED: 'Contatado',
  IN_CONVERSATION: 'Em conversa',
  PROPOSAL_SENT: 'Proposta enviada',
  WON: 'Ganho',
  LOST: 'Perdido',
  DO_NOT_CONTACT: 'Não contatar',
};

export const PRIORITY_LABEL: Readonly<Record<LeadPriority, string>> = {
  LOW: 'Baixa',
  MEDIUM: 'Média',
  HIGH: 'Alta',
  VERY_HIGH: 'Muito alta',
};

export const SOURCE_TYPE_LABEL: Readonly<Record<SourceType, string>> = {
  MANUAL: 'Manual',
  CSV_IMPORT: 'Importação CSV',
  INTEGRATION: 'Integração',
};

export const DUPLICATE_SIGNAL_LABEL: Readonly<Record<DuplicateSignal, string>> = {
  DOMAIN: 'Mesmo domínio',
  PHONE: 'Mesmo telefone',
  NAME_CITY_STATE: 'Mesmo nome na mesma cidade',
};

export const IMPORT_FIELD_LABEL: Readonly<Record<ImportField, string>> = {
  name: 'Nome',
  tradeName: 'Nome comercial',
  segment: 'Segmento',
  city: 'Cidade',
  state: 'UF',
  websiteUrl: 'Site',
  instagramUrl: 'Instagram',
  phone: 'Telefone',
  businessEmail: 'E-mail',
  address: 'Endereço',
  notes: 'Observações',
};

export const IMPORT_ROW_STATUS_LABEL: Readonly<Record<ImportRowStatus, string>> = {
  VALID: 'Válida',
  REJECTED: 'Rejeitada',
  DUPLICATE: 'Possível duplicata',
  IMPORTED: 'Importada',
  MERGED: 'Completou empresa existente',
  SKIPPED: 'Não importada',
  REMOVED: 'Empresa excluída depois',
};

/** Criteria phrased as statements: they score when confirmed (D24). */
export const CRITERION_LABEL: Readonly<Record<Criterion, string>> = {
  SITE_ABSENT: 'Empresa sem site',
  NO_HTTPS: 'Site sem HTTPS',
  NO_MOBILE_VIEWPORT: 'Página inicial sem adaptação a celular',
  NO_CONTACT_LINK: 'Página inicial sem link de contato',
  NO_ONLINE_BOOKING_OR_CATALOG: 'Sem agendamento ou catálogo online',
  BUSINESS_INFO_SUFFICIENT: 'Informações comerciais suficientes',
  SERVICE_FIT: 'Segmento atendido',
  LOCATION_SERVED: 'Localização atendida',
};

export const FINDING_STATUS_LABEL: Readonly<Record<FindingStatus, string>> = {
  CONFIRMED: 'Confirmado',
  NOT_FOUND: 'Não encontrado',
  NOT_VERIFIED: 'Não verificado',
  NOT_APPLICABLE: 'Não se aplica',
  CONTRADICTED: 'Contradito',
};

export const ANALYSIS_STATUS_LABEL: Readonly<Record<AnalysisStatus, string>> = {
  PENDING: 'Na fila',
  PROCESSING: 'Em processamento',
  COMPLETED: 'Concluída',
  FAILED: 'Falhou',
};

export const CONFIDENCE_LABEL: Readonly<Record<Confidence, string>> = {
  LOW: 'baixa',
  MEDIUM: 'média',
  HIGH: 'alta',
};

export const EVIDENCE_TYPE_LABEL: Readonly<Record<EvidenceType, string>> = {
  HTTP_RESPONSE: 'Resposta do site',
  HTML_SIGNAL: 'Sinal na página',
  ROBOTS_TXT: 'robots.txt',
  COMPANY_RECORD: 'Cadastro',
  CONFIGURATION: 'Configuração',
  OPERATOR_NOTE: 'Nota do operador',
};

export const INTERACTION_TYPE_LABEL: Readonly<Record<InteractionType, string>> = {
  CONTACT: 'Contato',
  REPLY: 'Resposta',
  MEETING: 'Reunião',
  PROPOSAL: 'Proposta',
  DO_NOT_CONTACT_REQUEST: 'Pedido de não contato',
  NOTE: 'Anotação',
};

export const CHANNEL_LABEL: Readonly<Record<InteractionChannel, string>> = {
  PHONE: 'Telefone',
  WHATSAPP: 'WhatsApp',
  EMAIL: 'E-mail',
  INSTAGRAM: 'Instagram',
  IN_PERSON: 'Presencial',
  OTHER: 'Outro',
};

export const DIRECTION_LABEL: Readonly<Record<InteractionDirection, string>> = {
  OUTBOUND: 'Nós entramos em contato',
  INBOUND: 'A empresa entrou em contato',
  INTERNAL: 'Registro interno',
};

export const OUTREACH_CHANNEL_LABEL: Readonly<Record<OutreachChannel, string>> = {
  WHATSAPP: 'WhatsApp',
  EMAIL: 'E-mail',
  INSTAGRAM: 'Instagram (mensagem direta)',
  PHONE_CALL: 'Ligação (roteiro)',
};

export const MESSAGE_STATUS_LABEL: Readonly<Record<MessageStatus, string>> = {
  DRAFT: 'Rascunho',
  NEEDS_REVIEW: 'Precisa de revisão',
  APPROVED: 'Aprovada, não enviada',
  SENT: 'Envio registrado',
  FAILED: 'Envio falhou',
  CANCELLED: 'Descartada',
};

export const IDENTIFIER_TYPE_LABEL: Readonly<Record<IdentifierType, string>> = {
  PHONE: 'Telefone',
  EMAIL: 'E-mail',
  INSTAGRAM: 'Instagram',
};
