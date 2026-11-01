package com.jhcomp.prospect.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationRepository;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserRepository;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService.AuditEntry;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Administracao de membros da organizacao ativa (RF-003, D28).
 *
 * <p>Sem envio de convite: nao ha canal de e-mail no MVP. O sistema gera uma
 * senha temporaria, devolvida uma unica vez ao administrador, que a entrega
 * ao membro por fora; o membro e obrigado a troca-la no primeiro acesso.
 *
 * <p>O administrador nao altera a propria associacao (papel, acesso ou
 * senha temporaria): e o que impede uma organizacao de ficar sem
 * administrador por engano. Uma trava consultiva do PostgreSQL por
 * organizacao serializa mudancas concorrentes de dois administradores. Nao
 * e {@code SELECT ... FOR UPDATE} na organizacao: a auditoria grava em
 * transacao propria, com chave estrangeira para a organizacao, e esperaria
 * para sempre pela trava da transacao que a chamou.
 */
@Service
public class MemberService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    MemberService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            JdbcTemplate jdbc,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            OrganizationContext organizationContext,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /** Membro criado e a senha temporaria em texto, que nao e guardada em lugar nenhum. */
    public record CreatedMember(OrganizationMemberEntity member, String temporaryPassword) {}

    /**
     * Cadastra membro novo. E-mail ja cadastrado e recusado, em qualquer
     * organizacao: anexar uma conta existente deixaria o administrador desta
     * organizacao definir senha de alguem que tambem pertence a outra.
     */
    @Transactional
    public CreatedMember create(String rawEmail, String displayName, OrganizationRole role) {
        AuthenticatedUser admin = organizationContext.requireCurrentUser();
        OrganizationEntity organization = lockOrganization(admin);
        String email = MembershipUserDetailsService.normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("E-mail already registered");
        }

        Instant now = clock.instant();
        String temporaryPassword = TemporaryPasswords.generate();
        UserEntity user = userRepository.save(new UserEntity(
                UUID.randomUUID(), email, passwordEncoder.encode(temporaryPassword), displayName.strip(),
                UserStatus.ACTIVE, true, now));
        OrganizationMemberEntity member = memberRepository.save(new OrganizationMemberEntity(
                UUID.randomUUID(), organization, user, role, MemberStatus.ACTIVE, now));

        audit(admin, AuditAction.MEMBER_CREATE, member, role.name());
        return new CreatedMember(member, temporaryPassword);
    }

    /** Muda papel e/ou acesso. Campo nulo nao muda. */
    @Transactional
    public OrganizationMemberEntity update(UUID memberId, OrganizationRole role, MemberStatus status) {
        AuthenticatedUser admin = organizationContext.requireCurrentUser();
        lockOrganization(admin);
        OrganizationMemberEntity member = findOtherMember(admin, memberId);

        List<String> changes = new ArrayList<>();
        if (role != null && role != member.getRole()) {
            member.changeRole(role);
            changes.add("ROLE_" + role.name());
        }
        if (status != null && status != member.getStatus()) {
            member.changeStatus(status);
            changes.add("STATUS_" + status.name());
        }
        // Defesa em profundidade: com a propria associacao intocavel o caso nao
        // ocorre, mas a regra vale mais que o caminho que a garante hoje.
        memberRepository.flush();
        if (memberRepository.countActiveAdmins(admin.organizationId()) == 0) {
            throw new ConflictException("Organization must keep at least one active administrator");
        }
        // Depois das regras: a auditoria grava em transacao propria e nao
        // seria desfeita se a mudanca fosse recusada.
        if (!changes.isEmpty()) {
            audit(admin, AuditAction.MEMBER_UPDATE, member, String.join(",", changes));
        }
        return member;
    }

    /**
     * Nova senha temporaria para o membro. As sessoes abertas dele sao
     * encerradas na proxima requisicao (D28).
     */
    @Transactional
    public CreatedMember resetPassword(UUID memberId) {
        AuthenticatedUser admin = organizationContext.requireCurrentUser();
        lockOrganization(admin);
        OrganizationMemberEntity member = findOtherMember(admin, memberId);
        if (memberRepository.existsInOtherOrganization(member.getUser().getId(), admin.organizationId())) {
            // Redefinir aqui mudaria o acesso dele em outra organizacao.
            throw new ConflictException("Member also belongs to another organization");
        }

        String temporaryPassword = TemporaryPasswords.generate();
        member.getUser().replacePassword(passwordEncoder.encode(temporaryPassword), true, clock.instant());
        audit(admin, AuditAction.PASSWORD_RESET, member, null);
        return new CreatedMember(member, temporaryPassword);
    }

    /** Trava consultiva ate o fim da transacao, por organizacao. */
    private OrganizationEntity lockOrganization(AuthenticatedUser admin) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                (ResultSetExtractor<Void>) resultSet -> null, "members:" + admin.organizationId());
        return organizationRepository.findById(admin.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    }

    /** Membro da organizacao da sessao (404 fora dela) e diferente de quem pede. */
    private OrganizationMemberEntity findOtherMember(AuthenticatedUser admin, UUID memberId) {
        OrganizationMemberEntity member = memberRepository.findByIdAndOrganization(memberId, admin.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Member"));
        if (member.getUser().getId().equals(admin.userId())) {
            throw new ConflictException("Administrators cannot change their own membership");
        }
        return member;
    }

    /** Sem e-mail nem senha no detalhe: o id do membro basta para a trilha. */
    private void audit(AuthenticatedUser admin, AuditAction action, OrganizationMemberEntity member, String detail) {
        auditService.record(new AuditEntry(admin.organizationId(), admin.userId(), action, AuditOutcome.SUCCESS,
                "OrganizationMember", member.getId().toString(), null, detail));
    }
}
