package com.jhcomp.prospect.crm;

import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;

/**
 * Queries the do-not-contact list before recording an approach (rule 4).
 *
 * <p>Implemented by the outreach module, which owns the list. The interface
 * lives here so the CRM does not depend on outreach, which already depends on
 * the CRM to record a send as an interaction.
 */
public interface ContactBlockCheck {

    /** Some identifier of the lead's company is blocked on the channel, or on all of them. */
    boolean isBlocked(LeadEntity lead, InteractionChannel channel);
}
