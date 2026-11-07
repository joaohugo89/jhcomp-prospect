package com.jhcomp.prospect.lead.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The valid transitions of the lead lifecycle (doc 06 §2 and §3).
 *
 * <p>Rules this class guarantees:
 *
 * <ul>
 *   <li>{@code WON} and {@code LOST} are terminal: a new commercial cycle
 *       requires a new lead.
 *   <li>{@code DO_NOT_CONTACT} is reachable from any state, because the data
 *       subject's request has to be honoured immediately (doc 06 §9), and it
 *       is not reversible through this operation: removing the block requires
 *       an authorised, justified process, which is not a plain state change.
 *   <li>Going back from {@code CONTACTED} to {@code QUALIFIED} is allowed: a
 *       contact can turn out to have been premature, and correcting the
 *       classification is legitimate.
 * </ul>
 *
 * <p>This class does not decide when to record {@code CONTACTED}. Doc 06 §2
 * forbids moving the lead automatically when a message is generated or
 * approved: only the operator may, by confirming the contact happened. That
 * rule is enforced by the caller, since only the pair of states is known
 * here.
 *
 * <p>No dependency on Spring: this is a domain rule (doc 03 §5).
 */
public final class LeadStatusTransitions {

    private static final Map<LeadStatus, Set<LeadStatus>> ALLOWED = buildAllowed();

    private LeadStatusTransitions() {
    }

    private static Map<LeadStatus, Set<LeadStatus>> buildAllowed() {
        Map<LeadStatus, Set<LeadStatus>> allowed = new EnumMap<>(LeadStatus.class);

        allowed.put(LeadStatus.NEW, EnumSet.of(LeadStatus.QUALIFIED, LeadStatus.LOST));
        allowed.put(LeadStatus.QUALIFIED, EnumSet.of(LeadStatus.CONTACT_APPROVED, LeadStatus.LOST));
        allowed.put(
                LeadStatus.CONTACT_APPROVED,
                EnumSet.of(LeadStatus.CONTACTED, LeadStatus.QUALIFIED, LeadStatus.LOST));
        allowed.put(
                LeadStatus.CONTACTED,
                EnumSet.of(LeadStatus.IN_CONVERSATION, LeadStatus.QUALIFIED, LeadStatus.LOST));
        allowed.put(
                LeadStatus.IN_CONVERSATION,
                EnumSet.of(LeadStatus.PROPOSAL_SENT, LeadStatus.WON, LeadStatus.LOST));
        allowed.put(LeadStatus.PROPOSAL_SENT, EnumSet.of(LeadStatus.WON, LeadStatus.LOST));

        // Terminal states and the block have no way out through a state change.
        allowed.put(LeadStatus.WON, EnumSet.noneOf(LeadStatus.class));
        allowed.put(LeadStatus.LOST, EnumSet.noneOf(LeadStatus.class));
        allowed.put(LeadStatus.DO_NOT_CONTACT, EnumSet.noneOf(LeadStatus.class));

        // DO_NOT_CONTACT is reachable from any non-terminal state.
        for (LeadStatus from : LeadStatus.values()) {
            if (!from.isTerminal() && from != LeadStatus.DO_NOT_CONTACT) {
                allowed.get(from).add(LeadStatus.DO_NOT_CONTACT);
            }
        }

        return Map.copyOf(allowed);
    }

    public static boolean isAllowed(LeadStatus from, LeadStatus to) {
        if (from == to) {
            // Re-sending the same state is not a transition: the caller treats
            // it as a no-op, not as an error.
            return true;
        }

        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** The states reachable from {@code from}, for display in the interface. */
    public static Set<LeadStatus> allowedFrom(LeadStatus from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, Set.of()));
    }

    /** The initial state of a newly created lead (doc 06 §2). */
    public static LeadStatus initial() {
        return LeadStatus.NEW;
    }
}
