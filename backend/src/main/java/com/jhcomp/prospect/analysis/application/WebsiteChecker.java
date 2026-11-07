package com.jhcomp.prospect.analysis.application;

import com.jhcomp.prospect.analysis.domain.WebsiteObservation;

/**
 * Verification of a company's website (doc 06 §6).
 *
 * <p>An outbound port: the use case knows nothing of HTTP or of the library
 * behind it. It never throws on a network failure; no answer becomes
 * {@link WebsiteObservation.Outcome#UNREACHABLE}.
 */
public interface WebsiteChecker {

    WebsiteObservation inspect(String websiteUrl);
}
