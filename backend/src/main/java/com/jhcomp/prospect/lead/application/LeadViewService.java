package com.jhcomp.prospect.lead.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadViewEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadViewRepository;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Visoes salvas da lista de leads (D31). Privada: so o dono ve e apaga.
 * Compartilhada: o administrador publica, a equipe toda ve, so administrador
 * apaga. Privada de outra pessoa, ou visao de outra organizacao, responde como
 * inexistente.
 */
@Service
public class LeadViewService {

    /** Mais que isso deixa de ser atalho e vira outra lista para procurar. */
    public static final int MAX_VIEWS_PER_USER = 20;

    /** Teto das publicadas para a equipe, pela mesma razao. */
    public static final int MAX_SHARED_VIEWS = 20;

    private final LeadViewRepository repository;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    LeadViewService(LeadViewRepository repository, OrganizationContext organizationContext, Clock clock) {
        this.repository = repository;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /** Visao com o que quem pergunta pode fazer com ela. */
    public record VisibleView(LeadViewEntity view, boolean canDelete) {}

    @Transactional(readOnly = true)
    public List<VisibleView> visible() {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        return repository.findVisible(user.organizationId(), user.userId()).stream()
                .map(view -> new VisibleView(view, canDelete(view, user)))
                .toList();
    }

    @Transactional
    public VisibleView create(String name, String query, boolean shared) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        String trimmed = name.strip();
        UUID organizationId = user.organizationId();
        if (shared) {
            if (user.role() != OrganizationRole.ADMIN) {
                throw new AccessDeniedException("Only administrators share views");
            }
            if (repository.countByOrganizationIdAndSharedTrue(organizationId) >= MAX_SHARED_VIEWS) {
                throw new ConflictException("At most " + MAX_SHARED_VIEWS + " shared views per organization");
            }
            if (repository.existsByOrganizationIdAndSharedTrueAndNameIgnoreCase(organizationId, trimmed)) {
                throw new ConflictException("A shared view with this name already exists");
            }
        } else {
            if (repository.countByOrganizationIdAndUserIdAndSharedFalse(organizationId, user.userId())
                    >= MAX_VIEWS_PER_USER) {
                throw new ConflictException("At most " + MAX_VIEWS_PER_USER + " saved views per user");
            }
            if (repository.existsByOrganizationIdAndUserIdAndSharedFalseAndNameIgnoreCase(
                    organizationId, user.userId(), trimmed)) {
                throw new ConflictException("A saved view with this name already exists");
            }
        }
        LeadViewEntity view = repository.save(new LeadViewEntity(
                UUID.randomUUID(), organizationId, user.userId(), trimmed, query, shared, clock.instant()));
        return new VisibleView(view, true);
    }

    @Transactional
    public void delete(UUID viewId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        LeadViewEntity view = repository.findByIdAndOrganizationId(viewId, user.organizationId())
                .filter(found -> found.isShared() || found.getUserId().equals(user.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Saved view"));
        // Compartilhada e visivel para todos: negar e 403, nao 404 (para quem pergunta, ela existe).
        if (!canDelete(view, user)) {
            throw new AccessDeniedException("Only administrators remove shared views");
        }
        repository.delete(view);
    }

    private static boolean canDelete(LeadViewEntity view, AuthenticatedUser user) {
        return view.isShared() ? user.role() == OrganizationRole.ADMIN : view.getUserId().equals(user.userId());
    }
}
