package com.jhcomp.prospect.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.web.PageRequests;
import com.jhcomp.prospect.shared.web.PageResponse;

/**
 * The dashboard (RF-070, RF-071, doc 05 §3).
 *
 * <p>The period is in Brasília calendar dates, where the operator works:
 * "today" for them is the local day. Instants stay stored in UTC (RNF-009);
 * only the period's boundary uses the time zone. With no period, the last 30
 * days counting today. Every route accepts the same filters
 * ({@link DashboardParams}), so the numbers of one section match another's.
 *
 * <p>{@code assignee} filters by the lead's owner. An id from another
 * organisation simply returns zero: every query is already scoped by the
 * session.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    static final int MAX_PERIOD_DAYS = 366;

    private final DashboardQuery query;
    private final AttentionQuery attentionQuery;
    private final FunnelQuery funnelQuery;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    DashboardController(
            DashboardQuery query,
            AttentionQuery attentionQuery,
            FunnelQuery funnelQuery,
            OrganizationContext organizationContext,
            Clock clock) {
        this.query = query;
        this.attentionQuery = attentionQuery;
        this.funnelQuery = funnelQuery;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /** The shared filters, read from the query string. Absent fields filter nothing. */
    public record DashboardParams(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            String segment,
            String city,
            SourceType source,
            UUID assignee) {}

    @GetMapping("/summary")
    public SummaryResponse summary(@ModelAttribute DashboardParams params) {
        Period period = period(params);
        return new SummaryResponse(period.from(), period.to(), query.summary(filters(period, params), clock.instant()));
    }

    @GetMapping("/activity")
    public PageResponse<DashboardQuery.Activity> activity(
            @ModelAttribute DashboardParams params,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(query.activity(filters(period(params), params), pageable), item -> item);
    }

    /** Conversion by origin and segment for the leads opened in the period (RF-071). */
    @GetMapping("/conversion")
    public ConversionResponse conversion(@ModelAttribute DashboardParams params) {
        Period period = period(params);
        return new ConversionResponse(period.from(), period.to(), query.conversion(filters(period, params)));
    }

    /**
     * The period's metrics in intervals (RF-070). Without {@code granularity},
     * the choice follows the period's length, so the chart does not end up
     * with too many bars.
     */
    @GetMapping("/timeline")
    public TimelineResponse timeline(
            @ModelAttribute DashboardParams params,
            @RequestParam(required = false) DashboardQuery.Granularity granularity) {
        Period period = period(params);
        var unit = granularity == null ? defaultGranularity(period) : granularity;
        var points = query.timeline(filters(period, params), period.from(), period.to(), unit, BUSINESS_ZONE);
        return new TimelineResponse(period.from(), period.to(), unit, points);
    }

    /**
     * What needs action now: overdue tasks or tasks due today, stalled leads,
     * proposals with no contact, and leads with no next task. No period: this
     * is the situation of the moment; the other filters still apply.
     */
    @GetMapping("/attention")
    public AttentionQuery.Attention attention(
            @ModelAttribute DashboardParams params, @RequestParam(defaultValue = "5") int limit) {
        var now = clock.instant();
        var endOfToday = LocalDate.now(clock.withZone(BUSINESS_ZONE)).plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return attentionQuery.attention(filters(period(params), params), now, endOfToday, limit);
    }

    /** Each stage's reach and the average time in it, for the leads opened in the period (RF-071). */
    @GetMapping("/funnel")
    public FunnelResponse funnel(@ModelAttribute DashboardParams params) {
        Period period = period(params);
        return new FunnelResponse(period.from(), period.to(), funnelQuery.funnel(filters(period, params)));
    }

    /** Up to 31 days daily, up to 120 weekly, beyond that monthly. */
    static DashboardQuery.Granularity defaultGranularity(Period period) {
        long days = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
        if (days <= 31) {
            return DashboardQuery.Granularity.DAY;
        }
        return days <= 120 ? DashboardQuery.Granularity.WEEK : DashboardQuery.Granularity.MONTH;
    }

    /** The effective period, inclusive at both ends, as the operator chose it. */
    public record Period(LocalDate from, LocalDate to) {}

    /** The period used comes back in the response: the denominator stays explicit (RF-071). */
    public record SummaryResponse(LocalDate from, LocalDate to, DashboardQuery.Summary summary) {}

    public record TimelineResponse(
            LocalDate from, LocalDate to, DashboardQuery.Granularity granularity,
            List<DashboardQuery.TimelinePoint> points) {}

    public record ConversionResponse(LocalDate from, LocalDate to, DashboardQuery.Conversion conversion) {}

    public record FunnelResponse(LocalDate from, LocalDate to, FunnelQuery.Funnel funnel) {}

    private Period period(DashboardParams params) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDate end = params.to() == null ? today : params.to();
        LocalDate start = params.from() == null ? end.minusDays(29) : params.from();
        if (start.isAfter(end)) {
            throw new InvalidRequestException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
            throw new InvalidRequestException("Period must be at most " + MAX_PERIOD_DAYS + " days");
        }
        return new Period(start, end);
    }

    private DashboardSql.Filters filters(Period period, DashboardParams params) {
        return new DashboardSql.Filters(
                organizationContext.requireOrganizationId(),
                period.from().atStartOfDay(BUSINESS_ZONE).toInstant(),
                period.to().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant(),
                params.segment(),
                params.city(),
                params.source(),
                params.assignee());
    }
}
