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
 * The organisation's team, available to any member: for choosing a lead's
 * owner and filtering the dashboard by owner.
 *
 * <p>Active members only, and only their id and display name. E-mail, role
 * and status stay in member management ({@code /organizations/members}),
 * which belongs to the administrator: an operator does not need them in order
 * to assign a lead (LGPD, the minimum necessary).
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
