package com.jhcomp.prospect.identity.presentation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.identity.application.MemberQueryService;
import com.jhcomp.prospect.identity.application.MemberService;
import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.infrastructure.persistence.MemberActivityQuery;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.web.InputPatterns;
import com.jhcomp.prospect.shared.web.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Membros da organizacao ativa (RF-003, D28).
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
    private final MemberService memberService;
    private final MemberActivityQuery activityQuery;

    MemberController(
            MemberQueryService memberQueryService, MemberService memberService, MemberActivityQuery activityQuery) {
        this.activityQuery = activityQuery;
        this.memberQueryService = memberQueryService;
        this.memberService = memberService;
    }

    /** Ativos por padrao; {@code includeRevoked} mostra tambem quem perdeu o acesso, para reativar. */
    @GetMapping
    public PageResponse<MemberResponse> listMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeRevoked,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) String search) {

        // Teto no tamanho da pagina: um size arbitrario permitiria extrair a
        // base inteira numa requisicao e sobrecarregar o servidor (doc 05 §12).
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));

        // A ordenacao fica na propria consulta do repositorio.
        if (search != null && search.length() > 200) {
            throw new InvalidRequestException("search must have at most 200 characters");
        }
        // Sem status explicito, vale o includeRevoked de antes: so ativos por padrao.
        MemberStatus effective = status != null ? status : includeRevoked ? null : MemberStatus.ACTIVE;
        Page<OrganizationMemberEntity> members = memberQueryService.search(effective, search, pageable);
        UUID organizationId = members.isEmpty() ? null : members.getContent().getFirst().getOrganization().getId();
        Map<UUID, Instant> lastLogins = organizationId == null
                ? Map.of()
                : activityQuery.lastLogins(organizationId,
                        members.getContent().stream().map(member -> member.getUser().getId()).toList());
        return PageResponse.from(members,
                member -> MemberResponse.from(member, lastLogins.get(member.getUser().getId())));
    }

    /** Visao geral da equipe, para o topo da tela. */
    @GetMapping("/summary")
    public MemberQueryService.TeamSummary summary() {
        return memberQueryService.summary();
    }

    @GetMapping("/{memberId}")
    public MemberResponse getMember(@PathVariable UUID memberId) {
        return MemberResponse.from(memberQueryService.findMember(memberId));
    }

    /** A senha temporaria so aparece nesta resposta: nao e guardada nem pode ser consultada depois. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberWithPasswordResponse createMember(@Valid @RequestBody NewMemberRequest request) {
        return MemberWithPasswordResponse.from(memberService.create(request.email(), request.displayName(), request.role()));
    }

    @PatchMapping("/{memberId}")
    public MemberResponse updateMember(@PathVariable UUID memberId, @Valid @RequestBody UpdateMemberRequest request) {
        return MemberResponse.from(memberService.update(memberId, request.role(), request.status()));
    }

    @PostMapping("/{memberId}/reset-password")
    public MemberWithPasswordResponse resetPassword(@PathVariable UUID memberId) {
        return MemberWithPasswordResponse.from(memberService.resetPassword(memberId));
    }

    public record NewMemberRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) @Pattern(regexp = InputPatterns.NOT_BLANK_WHEN_PRESENT) String displayName,
            @NotNull OrganizationRole role) {}

    /** Campo ausente nao muda. */
    public record UpdateMemberRequest(OrganizationRole role, MemberStatus status) {}

    public record MemberWithPasswordResponse(MemberResponse member, String temporaryPassword) {

        static MemberWithPasswordResponse from(MemberService.CreatedMember created) {
            return new MemberWithPasswordResponse(MemberResponse.from(created.member()), created.temporaryPassword());
        }

        /** A senha temporaria nunca vai para log (RNF-007). */
        @Override
        public String toString() {
            return "MemberWithPasswordResponse[member=" + member.memberId() + ", temporaryPassword=***]";
        }
    }
}
