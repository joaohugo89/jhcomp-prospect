package com.jhcomp.prospect.outreach.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jhcomp.prospect.outreach.domain.MessageDraft;
import com.jhcomp.prospect.outreach.domain.MessageStatus;
import com.jhcomp.prospect.outreach.domain.OutreachChannel;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * An outreach message (RF-050 to RF-052).
 *
 * <p>The service checks the state and the contact block before calling the
 * transitions; the table repeats the approval and send consistency as CHECK
 * constraints.
 */
@Entity
@Table(name = "outreach_message")
public class OutreachMessageEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OutreachChannel channel;

    @Column(name = "subject")
    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(name = "unverified_claims")
    private String unverifiedClaims;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status;

    @Column(name = "generated_by", updatable = false)
    private String generatedBy;

    @Column(name = "prompt_version", updatable = false)
    private String promptVersion;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "send_recorded_by_user_id")
    private UUID sendRecordedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutreachMessageEntity() {
        // Required by JPA.
    }

    public OutreachMessageEntity(
            UUID organizationId,
            UUID leadId,
            OutreachChannel channel,
            MessageDraft draft,
            String generatedBy,
            String promptVersion,
            UUID createdByUserId,
            Instant now) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.leadId = leadId;
        this.channel = channel;
        this.generatedBy = generatedBy;
        this.promptVersion = promptVersion;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        applyContent(draft, now);
    }

    /** The operator's edit. An approved message returns to review and loses its approval (D26). */
    public void edit(MessageDraft draft, Instant now) {
        approvedByUserId = null;
        approvedAt = null;
        applyContent(draft, now);
    }

    public void approve(UUID userId, Instant now) {
        status = MessageStatus.APPROVED;
        approvedByUserId = userId;
        approvedAt = now;
        updatedAt = now;
    }

    public void recordSend(boolean delivered, Instant sentAt, UUID userId, Instant now) {
        status = delivered ? MessageStatus.SENT : MessageStatus.FAILED;
        this.sentAt = sentAt;
        sendRecordedByUserId = userId;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        status = MessageStatus.CANCELLED;
        updatedAt = now;
    }

    private void applyContent(MessageDraft draft, Instant now) {
        subject = draft.subject();
        body = draft.body();
        unverifiedClaims = draft.unverifiedClaims().isEmpty() ? null : String.join("\n", draft.unverifiedClaims());
        status = draft.unverifiedClaims().isEmpty() ? MessageStatus.DRAFT : MessageStatus.NEEDS_REVIEW;
        updatedAt = now;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public OutreachChannel getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    /** One per line in the database: draft validation guarantees none of them contains a line break. */
    public List<String> getUnverifiedClaims() {
        return unverifiedClaims == null ? List.of() : List.of(unverifiedClaims.split("\n"));
    }

    public MessageStatus getStatus() {
        return status;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public UUID getApprovedByUserId() {
        return approvedByUserId;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
