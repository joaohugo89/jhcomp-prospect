package com.jhcomp.prospect.lead.presentation;

import java.util.List;
import java.util.UUID;

import com.jhcomp.prospect.company.presentation.SourceRequest;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Requests of the lead module (doc 05 §5). None of them carries an organisation. */
public final class LeadRequests {

    private LeadRequests() {
    }

    /** An absent priority becomes {@code MEDIUM}; an absent origin becomes manual entry. */
    public record CreateLeadRequest(@NotNull UUID companyId, LeadPriority priority, @Valid SourceRequest source) {}

    /**
     * A status change.
     *
     * @param contactConfirmed required in order to reach {@code CONTACTED}:
     *     the operator confirms the contact actually happened (doc 06 §2).
     *     {@code Boolean}, not a primitive: Jackson 3 refuses a missing
     *     primitive in the body, and missing here means "not confirmed"
     */
    public record ChangeStatusRequest(
            @NotNull LeadStatus status, @Size(max = 500) String reason, Boolean contactConfirmed) {

        public boolean isContactConfirmed() {
            return Boolean.TRUE.equals(contactConfirmed);
        }
    }

    public record ChangePriorityRequest(@NotNull LeadPriority priority) {}

    /** The lead's editable data. A null {@code assignedUserId} clears the owner. */
    public record UpdateLeadRequest(UUID assignedUserId) {}

    /** What a bulk action changes. Not the status: that requires confirmation lead by lead. */
    public enum BulkAction { ASSIGN, PRIORITY }

    /**
     * A bulk action. {@code ASSIGN} uses {@code assignedUserId} (null clears
     * the owner); {@code PRIORITY} requires {@code priority}.
     */
    public record BulkUpdateRequest(
            @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> leadIds,
            @NotNull BulkAction action,
            UUID assignedUserId,
            LeadPriority priority) {}

    public record BulkUpdateResponse(int updated) {}
}
