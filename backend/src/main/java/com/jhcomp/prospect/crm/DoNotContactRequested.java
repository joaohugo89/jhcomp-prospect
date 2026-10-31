package com.jhcomp.prospect.crm;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de nao contato registrado como interacao (D25, D26).
 *
 * <p>Publicado mesmo quando o lead nao pode mudar de status (ganho ou
 * perdido): o pedido vale para a empresa, nao para a etapa comercial.
 */
public record DoNotContactRequested(UUID organizationId, UUID leadId, Instant requestedAt) {}
