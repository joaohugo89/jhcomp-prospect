package com.jhcomp.prospect.lead.domain;

import java.util.UUID;

/**
 * O status de um lead mudou (doc 06 §2).
 *
 * <p>Publicado na transacao da mudanca, para que outros modulos reajam sem que
 * o modulo de lead dependa deles: o CRM cria o deal ao chegar a proposta (D2).
 */
public record LeadStatusChanged(UUID organizationId, UUID leadId, LeadStatus from, LeadStatus to, String reason) {}
