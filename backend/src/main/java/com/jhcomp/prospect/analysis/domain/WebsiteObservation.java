package com.jhcomp.prospect.analysis.domain;

import java.time.Instant;

/**
 * What the automated verification saw on the site (doc 06 §6).
 *
 * <p>External page content is untrusted data (product rule 9): only boolean
 * signals and a hash come out of here, never the page's text.
 *
 * @param httpsAvailable null when it could not be determined
 * @param detail a short technical reason, such as {@code DNS} or {@code TIMEOUT}
 */
public record WebsiteObservation(
        Outcome outcome,
        String requestedUrl,
        String finalUrl,
        Boolean httpsAvailable,
        Integer httpStatus,
        boolean hasViewport,
        boolean hasContactLink,
        String contentHash,
        String detail,
        Instant observedAt) {

    public enum Outcome {
        /** No website on file: nothing was accessed. */
        NO_WEBSITE,
        /** No answer: DNS, connection, a blocked address, or a timeout. */
        UNREACHABLE,
        /** The site answered, but robots.txt does not allow reading the home page. */
        ROBOTS_DISALLOWED,
        /** The home page answered with an error, or with content that is not HTML. */
        PAGE_UNUSABLE,
        /** The home page was read. */
        PAGE_READ
    }

    public static WebsiteObservation noWebsite(Instant now) {
        return new WebsiteObservation(Outcome.NO_WEBSITE, null, null, null, null, false, false, null, null, now);
    }

    public boolean siteResponded() {
        return outcome == Outcome.ROBOTS_DISALLOWED || outcome == Outcome.PAGE_UNUSABLE || outcome == Outcome.PAGE_READ;
    }
}
