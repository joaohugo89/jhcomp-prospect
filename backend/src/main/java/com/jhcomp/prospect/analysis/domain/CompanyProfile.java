package com.jhcomp.prospect.analysis.domain;

/**
 * What the analysis reads from the company record.
 *
 * <p>Only what the criteria need: no notes, no address, no e-mail in clear
 * text (data minimisation, doc 07 §2).
 */
public record CompanyProfile(
        String websiteUrl, String segment, String state, boolean hasPhone, boolean hasEmail, boolean hasInstagram) {

    public boolean hasWebsite() {
        return websiteUrl != null && !websiteUrl.isBlank();
    }

    public boolean hasContactChannel() {
        return hasPhone || hasEmail || hasInstagram || hasWebsite();
    }
}
