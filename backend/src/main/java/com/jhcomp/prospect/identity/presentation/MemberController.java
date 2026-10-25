package com.jhcomp.prospect.identity.presentation;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.identity.application.MemberQueryService;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.shared.web.PageResponse;

/**
 * Membros da organizacao ativa (RF-003).
 *
 * <p>Operacao administrativa: bloqueada para operador sem permissao, conforme o
 * critério de aceite do RF-003.
 *
 * <p>Nao recebe {@code organizationId} em lugar algum — nem no path, nem em
 * query, nem no corpo. A organizacao vem da sessao (RF-002).
 */
@RestController
@RequestMapping("/organizations/members")
@PreAuthorize("hasRole('ADMIN')")
public class MemberController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MemberQueryService memberQueryService;

    MemberController(MemberQueryService memberQueryService) {
        this.memberQueryService = memberQueryService;
    }

    @GetMapping
    public PageResponse<MemberResponse> listMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Teto no tamanho da pagina: um size arbitrario permitiria extrair a
        // base inteira numa requisicao e sobrecarregar o servidor (doc 05 §12).
        int effectiveSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);

        // A ordenacao fica na propria consulta do repositorio, e nao num
        // Sort.by("createdAt"): referencia textual de propriedade nao e
        // verificada pelo compilador e quebra silenciosamente se o campo for
        // renomeado.
        Page<OrganizationMemberEntity> members =
                memberQueryService.listActiveMembers(PageRequest.of(effectivePage, effectiveSize));

        return PageResponse.from(members, MemberResponse::from);
    }

    @GetMapping("/{memberId}")
    public MemberResponse getMember(@PathVariable UUID memberId) {
        return MemberResponse.from(memberQueryService.findMember(memberId));
    }
}
