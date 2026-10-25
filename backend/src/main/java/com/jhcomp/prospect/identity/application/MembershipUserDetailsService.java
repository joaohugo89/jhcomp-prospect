package com.jhcomp.prospect.identity.application;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;

/**
 * Carrega o usuario e resolve sua organizacao ativa no login (RF-002).
 *
 * <p>Carrega pela associacao, nao pelo usuario: sem associacao ativa a uma
 * organizacao ativa nao existe contexto em que operar, portanto nao ha
 * autenticacao possivel.
 *
 * <p>Todas as recusas lancam {@link UsernameNotFoundException}, que o
 * provedor de autenticacao converte em credenciais invalidas. Isso mantem a
 * resposta identica para e-mail inexistente, usuario desabilitado e
 * organizacao suspensa, como exige o critério de aceite do RF-001.
 */
@Service
public class MembershipUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(MembershipUserDetailsService.class);

    private final OrganizationMemberRepository memberRepository;

    MembershipUserDetailsService(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String username) {
        String email = normalizeEmail(username);

        List<OrganizationMemberEntity> memberships = memberRepository.findActiveMembershipsByEmail(email);

        if (memberships.isEmpty()) {
            // Sem e-mail no log: evita registrar dado pessoal de quem talvez
            // nem seja usuario do sistema (RNF-007).
            throw new UsernameNotFoundException("No active membership");
        }

        if (memberships.size() > 1) {
            // No MVP um usuario pertence a uma organizacao. Varias associacoes
            // ativas tornariam a organizacao ambigua, e deixar o cliente
            // escolher violaria o RF-002; recusar e a opcao segura.
            log.error(
                    "Usuario com {} associacoes ativas; esperado 1. Login recusado por ambiguidade.",
                    memberships.size());
            throw new UsernameNotFoundException("Ambiguous membership");
        }

        OrganizationMemberEntity membership = memberships.getFirst();

        if (membership.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new UsernameNotFoundException("User not active");
        }

        return new AuthenticatedUser(
                membership.getUser().getId(),
                membership.getOrganization().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                membership.getUser().getPasswordHash());
    }

    /** O e-mail e persistido normalizado; comparar sem normalizar impediria o login. */
    public static String normalizeEmail(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.strip().toLowerCase(Locale.ROOT);
    }
}
