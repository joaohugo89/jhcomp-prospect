package com.jhcomp.prospect.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.web.PageRequests;
import com.jhcomp.prospect.shared.web.PageResponse;

/**
 * Dashboard (RF-070, doc 05 §3).
 *
 * <p>O periodo e em datas do calendario de Brasilia, onde o operador trabalha:
 * "hoje" para ele e o dia local. Os instantes continuam gravados em UTC
 * (RNF-009); so a fronteira do periodo usa o fuso. Sem periodo, os ultimos 30
 * dias, contando hoje.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    static final int MAX_PERIOD_DAYS = 366;

    private final DashboardQuery query;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    DashboardController(DashboardQuery query, OrganizationContext organizationContext, Clock clock) {
        this.query = query;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String city) {
        Period period = period(from, to);
        var summary = query.summary(filters(period, segment, city), clock.instant());
        return new SummaryResponse(period.from(), period.to(), summary);
    }

    @GetMapping("/activity")
    public PageResponse<DashboardQuery.Activity> activity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(query.activity(filters(period(from, to), segment, city), pageable), item -> item);
    }

    /** Periodo efetivo, inclusivo nas duas pontas, como o operador escolheu. */
    public record Period(LocalDate from, LocalDate to) {}

    /** O periodo usado volta na resposta: o denominador fica explicito (RF-071). */
    public record SummaryResponse(LocalDate from, LocalDate to, DashboardQuery.Summary summary) {}

    private Period period(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw new InvalidRequestException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
            throw new InvalidRequestException("Period must be at most " + MAX_PERIOD_DAYS + " days");
        }
        return new Period(start, end);
    }

    private DashboardQuery.Filters filters(Period period, String segment, String city) {
        return new DashboardQuery.Filters(
                organizationContext.requireOrganizationId(),
                period.from().atStartOfDay(BUSINESS_ZONE).toInstant(),
                period.to().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant(),
                segment,
                city);
    }
}
