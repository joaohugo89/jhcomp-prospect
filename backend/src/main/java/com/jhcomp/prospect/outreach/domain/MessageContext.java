package com.jhcomp.prospect.outreach.domain;

import java.util.List;

/**
 * Everything the AI may use to write the message (doc 06 §8, D26).
 *
 * <p>It holds only company data and confirmed findings; phone, e-mail,
 * Instagram, the operator's notes and any external page content are left out
 * on purpose (doc 07 §7). The findings are the system's own fixed
 * descriptions, never text coming from outside.
 */
public record MessageContext(
        OutreachChannel channel,
        String senderOrganization,
        String senderName,
        String companyName,
        String segment,
        String city,
        String state,
        List<String> confirmedFindings) {

    public MessageContext {
        confirmedFindings = List.copyOf(confirmedFindings);
    }
}
