package com.jhcomp.prospect.crm;

import java.time.Instant;
import java.util.UUID;

/**
 * A do-not-contact request recorded as an interaction (D25, D26).
 *
 * <p>Published even when the lead cannot change status (won or lost): the
 * request applies to the company, not to the commercial stage.
 */
public record DoNotContactRequested(UUID organizationId, UUID leadId, Instant requestedAt) {}
