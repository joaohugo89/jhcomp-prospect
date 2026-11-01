package com.jhcomp.prospect.identity.presentation;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.identity.application.MemberQueryService;
import com.jhcomp.prospect.shared.web.PageResponse;

/**
 * Equipe da organizacao, para qualquer membro: escolher o responsavel de um
 * lead e filtrar o dashboard por responsavel.
 *
 * <p>So os ativos e so id e nome de exibicao. E-mail, papel e situacao ficam
 * na gestao de membros ({@code /organizations/members}), que e do
 * administrador: o operador nao precisa deles para atribuir um lead (LGPD,
 * minimo necessario).
 */
@RestController
@RequestMapping("/team")
public class TeamController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MemberQueryService memberQueryService;

    TeamController(MemberQueryService memberQueryService) {
        this.memberQueryService = memberQueryService;
    }

    public record TeammateResponse(UUID userId, String displayName) {}

    @GetMapping
    public PageResponse<TeammateResponse> team(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(
                memberQueryService.listActiveMembers(pageable),
                member -> new TeammateResponse(member.getUser().getId(), member.getUser().getDisplayName()));
    }
}
