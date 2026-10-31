package com.jhcomp.prospect.crm;

import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;

/**
 * Consulta a lista de nao contato antes de registrar abordagem (regra 4).
 *
 * <p>Implementada pelo modulo de outreach, dono da lista. A interface fica
 * aqui para que o CRM nao dependa do outreach, que ja depende do CRM para
 * registrar o envio como interacao.
 */
public interface ContactBlockCheck {

    /** Algum identificador da empresa do lead esta bloqueado no canal, ou em todos. */
    boolean isBlocked(LeadEntity lead, InteractionChannel channel);
}
