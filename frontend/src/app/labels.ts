import type { DuplicateSignal, SourceType } from '../types/company';
import type { ImportField, ImportRowStatus, ImportStatus } from '../types/import';
import type { LeadPriority, LeadStatus } from '../types/lead';

/** Rótulos em português. Os valores da API continuam em inglês, que é o contrato. */

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

export const IMPORT_STATUS_LABEL: Readonly<Record<ImportStatus, string>> = {
  ANALYZED: 'Aguardando revisão',
  IMPORTING: 'Importando',
  COMPLETED: 'Importada',
  DISCARDED: 'Descartada',
};

export const IMPORT_ROW_STATUS_LABEL: Readonly<Record<ImportRowStatus, string>> = {
  VALID: 'Válida',
  REJECTED: 'Rejeitada',
  DUPLICATE: 'Possível duplicata',
  IMPORTED: 'Importada',
  MERGED: 'Completou empresa existente',
  SKIPPED: 'Não importada',
};
