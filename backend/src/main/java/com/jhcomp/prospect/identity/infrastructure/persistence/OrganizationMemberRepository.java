package com.jhcomp.prospect.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jhcomp.prospect.identity.domain.MemberStatus;

/**
 * Consultas de membros.
 *
 * <p>Toda consulta que retorna dado privado recebe {@code organizationId}
 * obrigatoriamente (RNF-001, doc 04 §4). Nao existe aqui um
 * {@code findById(UUID)} de uso livre: buscar por id sem escopo de organizacao
 * e exatamente o que permite acesso cruzado entre organizacoes, mesmo quando o
 * chamador tem um UUID valido.
 */
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMemberEntity, UUID> {

    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.user u
            where m.organization.id = :organizationId
              and m.status = :status
            order by m.createdAt asc
            """)
    Page<OrganizationMemberEntity> findByOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("status") MemberStatus status,
            Pageable pageable);

    /** Busca escopada: um id de outra organizacao resulta em vazio, nao em acesso. */
    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.user u
            where m.id = :memberId
              and m.organization.id = :organizationId
            """)
    Optional<OrganizationMemberEntity> findByIdAndOrganization(
            @Param("memberId") UUID memberId,
            @Param("organizationId") UUID organizationId);

    /**
     * Associacao ativa usada para resolver a organizacao no login.
     *
     * <p>Exige organizacao ativa: membro ativo de organizacao suspensa nao
     * autentica.
     */
    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.organization o
            join fetch m.user u
            where u.email = :email
              and m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE
              and o.status = com.jhcomp.prospect.identity.domain.OrganizationStatus.ACTIVE
            """)
    java.util.List<OrganizationMemberEntity> findActiveMembershipsByEmail(@Param("email") String email);

    /**
     * Associacao ativa de um usuario, usada para revalidar a sessao.
     *
     * <p>Exige usuario ativo, associacao ativa e organizacao ativa. Se
     * qualquer um dos tres deixar de valer, a consulta retorna vazio e a
     * sessao deve ser encerrada.
     *
     * <p>Retorna lista, e nao Optional: duas associacoes ativas tornariam a
     * organizacao ambigua, e o chamador precisa poder recusar esse caso em vez
     * de receber uma excecao.
     */
    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.organization o
            join fetch m.user u
            where u.id = :userId
              and m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE
              and o.status = com.jhcomp.prospect.identity.domain.OrganizationStatus.ACTIVE
              and u.status = com.jhcomp.prospect.identity.domain.UserStatus.ACTIVE
            """)
    java.util.List<OrganizationMemberEntity> findActiveMembershipsByUserId(@Param("userId") UUID userId);
}
