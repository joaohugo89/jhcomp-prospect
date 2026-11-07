package com.jhcomp.prospect.lead.domain;

import java.util.UUID;

/**
 * A lead's status changed (doc 06 §2).
 *
 * <p>Published inside the transaction of the change, so other modules can
 * react without the lead module depending on them: the CRM creates the deal
 * on reaching the proposal (D2).
 */
public record LeadStatusChanged(UUID organizationId, UUID leadId, LeadStatus from, LeadStatus to, String reason) {}
