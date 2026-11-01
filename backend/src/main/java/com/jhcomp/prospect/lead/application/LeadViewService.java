package com.jhcomp.prospect.lead.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.lead.infrastructure.persistence.LeadViewEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadViewRepository;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Visoes salvas da lista de leads (D31). Cada pessoa ve e apaga so as suas:
 * a visao de outra pessoa, ou de outra organizacao, responde como inexistente.
 */
@Service
public class LeadViewService {

    /** Mais que isso deixa de ser atalho e vira outra lista para procurar. */
    public static final int MAX_VIEWS_PER_USER = 20;

    private final LeadViewRepository repository;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    LeadViewService(LeadViewRepository repository, OrganizationContext organizationContext, Clock clock) {
        this.repository = repository;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LeadViewEntity> mine() {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        return repository.findByOrganizationIdAndUserIdOrderByNameAsc(user.organizationId(), user.userId());
    }

    @Transactional
    public LeadViewEntity create(String name, String query) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        String trimmed = name.strip();
        if (repository.countByOrganizationIdAndUserId(user.organizationId(), user.userId()) >= MAX_VIEWS_PER_USER) {
            throw new ConflictException("At most " + MAX_VIEWS_PER_USER + " saved views per user");
        }
        if (repository.existsByOrganizationIdAndUserIdAndNameIgnoreCase(user.organizationId(), user.userId(), trimmed)) {
            throw new ConflictException("A saved view with this name already exists");
        }
        return repository.save(new LeadViewEntity(
                UUID.randomUUID(), user.organizationId(), user.userId(), trimmed, query, clock.instant()));
    }

    @Transactional
    public void delete(UUID viewId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        LeadViewEntity view = repository.findByIdAndOrganizationIdAndUserId(viewId, user.organizationId(), user.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Saved view"));
        repository.delete(view);
    }
}
