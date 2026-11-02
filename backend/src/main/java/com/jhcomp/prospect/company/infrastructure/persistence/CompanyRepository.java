package com.jhcomp.prospect.company.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Grafia ja usada na organizacao para o mesmo segmento, sem distinguir
     * caixa: a mais usada (D31). Vazio quando o segmento e novo.
     */
    @Query(value = """
            select segment from company
             where organization_id = :organizationId and lower(segment) = lower(:segment)
             group by segment
             order by count(*) desc, segment
             limit 1
            """, nativeQuery = true)
    Optional<String> findExistingSegment(
            @Param("organizationId") UUID organizationId, @Param("segment") String segment);

    // Candidatos a duplicata (doc 06 §4). Um metodo por sinal, para que cada
    // consulta use o indice correspondente da V3.

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedDomainAndIdNot(
            UUID organizationId, String normalizedDomain, UUID excludedId);

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedPhoneAndIdNot(
            UUID organizationId, String normalizedPhone, UUID excludedId);

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedNameAndCityIgnoreCaseAndStateAndIdNot(
            UUID organizationId, String normalizedName, String city, String state, UUID excludedId);

    // Variantes em lote, para a importacao: uma consulta por sinal para o
    // arquivo inteiro, em vez de tres por linha.

    List<CompanyEntity> findByOrganizationIdAndNormalizedDomainIn(UUID organizationId, Collection<String> domains);

    List<CompanyEntity> findByOrganizationIdAndNormalizedPhoneIn(UUID organizationId, Collection<String> phones);

    List<CompanyEntity> findByOrganizationIdAndNormalizedNameIn(UUID organizationId, Collection<String> names);
}
