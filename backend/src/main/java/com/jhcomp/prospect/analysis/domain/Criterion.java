package com.jhcomp.prospect.analysis.domain;

/**
 * The criteria of score formula v1 (doc 06 §7, D24).
 *
 * <p>Each criterion is a statement, worth its full points when confirmed and
 * zero otherwise. The groups add up exactly to the table in doc 06 §7: absent
 * website 25, technical problem 20, functional need 20, business information
 * 15, service fit 10, location 10.
 */
public enum Criterion {
    /** Absence of a website, confirmed by an adequate method: human review only. */
    SITE_ABSENT(25),
    NO_HTTPS(10),
    NO_MOBILE_VIEWPORT(10),
    NO_CONTACT_LINK(10),
    /** No automatic heuristic: inferring it from page text would be asserting without evidence. */
    NO_ONLINE_BOOKING_OR_CATALOG(10),
    BUSINESS_INFO_SUFFICIENT(15),
    SERVICE_FIT(10),
    LOCATION_SERVED(10);

    private final int maxPoints;

    Criterion(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public int maxPoints() {
        return maxPoints;
    }
}
