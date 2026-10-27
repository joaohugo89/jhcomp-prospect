package com.jhcomp.prospect.company.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Empresas.
 *
 * <p>Toda consulta recebe {@code organizationId} (RNF-001, doc 04 §4). O
 * {@code findById} herdado nao e usado pelos casos de uso: buscar por id sem
 * escopo e exatamente o que permite acesso cruzado entre organizacoes.
 * Listagens filtradas usam {@link CompanySpecifications}, que sempre aplica o
 * escopo.
 */
public interface CompanyRepository
        extends JpaRepository<CompanyEntity, UUID>, JpaSpecificationExecutor<CompanyEntity> {

    Optional<CompanyEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    // Candidatos a duplicata (doc 06 §4). Um metodo por sinal, para que cada
    // consulta use o indice correspondente da V3.

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedDomainAndIdNot(
            UUID organizationId, String normalizedDomain, UUID excludedId);

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedPhoneAndIdNot(
            UUID organizationId, String normalizedPhone, UUID excludedId);

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedNameAndCityIgnoreCaseAndStateAndIdNot(
            UUID organizationId, String normalizedName, String city, String state, UUID excludedId);
}
