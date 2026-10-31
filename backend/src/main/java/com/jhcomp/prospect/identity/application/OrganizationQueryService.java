package com.jhcomp.prospect.identity.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationRepository;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/** Dados da organizacao ativa, sempre a da sessao (RF-002). */
@Service
public class OrganizationQueryService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationContext organizationContext;

    OrganizationQueryService(OrganizationRepository organizationRepository, OrganizationContext organizationContext) {
        this.organizationRepository = organizationRepository;
        this.organizationContext = organizationContext;
    }

    /** Nome exibido como remetente das mensagens (doc 06 §8). */
    @Transactional(readOnly = true)
    public String currentOrganizationName() {
        return organizationRepository.findById(organizationContext.requireOrganizationId())
                .map(OrganizationEntity::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    }
}
