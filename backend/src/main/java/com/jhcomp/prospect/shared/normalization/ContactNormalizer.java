package com.jhcomp.prospect.shared.normalization;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalisation of names, phones, domains and e-mails (decision D10).
 *
 * <p>A single component, used by deduplication (doc 06 §4) and by the
 * do-not-contact list (doc 07 §10). Two separate implementations would drift
 * apart over time, and drift here means a do-not-contact request can be
 * bypassed by a difference in phone formatting. That is a compliance failure,
 * not a cosmetic defect.
 *
 * <p>Every normalisation is deterministic and preserves the original value:
 * the normalised form is for comparing, never for displaying (doc 04 §2).
 *
 * <p>When a value cannot be normalised with confidence, the method returns
 * {@link Optional#empty()}. Returning a guess would be worse than returning
 * nothing: it would draw distinct records together and, in the case of a
 * contact block, could either free someone who asked not to be contacted or
 * block an unrelated third party.
 */
public final class ContactNormalizer {

    /** Two or more spaces, tabs or line breaks. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** The accent marks left behind by Unicode decomposition. */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    private static final Pattern LEADING_WWW = Pattern.compile("^www\\.");

    /** A URL scheme ({@code https://}); without one, {@link URI} reads everything as a path. */
    private static final Pattern HAS_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");

    /** Instagram's rules: letters, digits, dot and underscore, up to 30. */
    private static final Pattern INSTAGRAM_HANDLE = Pattern.compile("[a-z0-9._]{1,30}");

    /** Leading Instagram URL segments that are not a profile. */
    private static final Set<String> INSTAGRAM_RESERVED = Set.of("p", "reel", "reels", "explore", "stories", "tv", "accounts");

    private ContactNormalizer() {
    }

    /**
     * A name normalised for comparison: no accents, no excess whitespace,
     * lower case.
     *
     * <p>Punctuation and symbols are kept, because removing them would draw
     * legitimately different names together.
     */
    public static Optional<String> name(String rawName) {
        if (isBlank(rawName)) {
            return Optional.empty();
        }

        String collapsed = WHITESPACE.matcher(rawName.strip()).replaceAll(" ");
        String withoutAccents = stripAccents(collapsed);
        String normalized = withoutAccents.toLowerCase(Locale.ROOT);

        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }

    /**
     * A Brazilian phone number in E.164 form ({@code +5535988887777}).
     *
     * <p>It accepts the usual shapes: with or without the country code, with
     * the area code in parentheses, separated by hyphens, spaces or dots, and
     * with the carrier zero before the area code.
     *
     * <p>It returns empty when the area code is missing. An eight or
     * nine-digit number on its own does not identify a phone in this country:
     * assuming an area code would draw together companies from different
     * cities and, in the do-not-contact list, would block unrelated people who
     * share the same local number.
     */
    public static Optional<String> phone(String rawPhone) {
        if (isBlank(rawPhone)) {
            return Optional.empty();
        }

        String digits = NON_DIGITS.matcher(rawPhone).replaceAll("");

        // With an explicit "+", what follows is the country code. Only
        // Brazil's is accepted, with a full area code and number: without this
        // rule, "+1 415 555 0100" has 11 digits and would be read as area code
        // 14, colliding with a valid Brazilian number — including in the
        // do-not-contact list.
        if (rawPhone.strip().startsWith("+")) {
            return digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)
                    ? Optional.of("+" + digits)
                    : Optional.empty();
        }

        // The carrier zero before the area code: 035..., 0xx35...
        if (digits.startsWith("0")) {
            digits = digits.replaceFirst("^0+", "");
        }

        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
            // It already carries the country code.
            return Optional.of("+" + digits);
        }

        if (digits.length() == 10 || digits.length() == 11) {
            // Area code plus number, with no country code.
            return Optional.of("+55" + digits);
        }

        return Optional.empty();
    }

    /**
     * A site's registrable domain, lower case, with no scheme, no
     * {@code www.}, no port and no path.
     *
     * <p>It is the most reliable deduplication signal after a business
     * identifier (doc 06 §4): two records sharing a domain are almost always
     * the same company.
     */
    public static Optional<String> domain(String rawUrl) {
        if (isBlank(rawUrl)) {
            return Optional.empty();
        }

        String candidate = rawUrl.strip();

        // Without a scheme, URI treats everything as a path and never sees the host.
        if (!HAS_SCHEME.matcher(candidate).matches()) {
            candidate = "http://" + candidate;
        }

        String host;
        try {
            host = URI.create(candidate).getHost();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        if (host == null || host.isBlank()) {
            return Optional.empty();
        }

        String normalized = LEADING_WWW
                .matcher(host.toLowerCase(Locale.ROOT))
                .replaceFirst("")
                // The trailing dot of an absolute FQDN.
                .replaceAll("\\.$", "");

        // It needs at least one dot: "localhost" or "empresa" are not the
        // domains of a company website.
        return normalized.contains(".") ? Optional.of(normalized) : Optional.empty();
    }

    /** An e-mail in lower case, with surrounding whitespace removed. */
    public static Optional<String> email(String rawEmail) {
        if (isBlank(rawEmail)) {
            return Optional.empty();
        }

        String normalized = rawEmail.strip().toLowerCase(Locale.ROOT);

        // Format validation belongs to input validation; here it is enough to
        // refuse what clearly is not an address.
        return normalized.indexOf('@') > 0 && normalized.indexOf('@') < normalized.length() - 1
                ? Optional.of(normalized)
                : Optional.empty();
    }

    /**
     * An Instagram handle in lower case, without the {@code @}.
     *
     * <p>It accepts {@code @handle}, {@code handle} or the profile URL. It
     * refuses URLs from another host and paths that are not a profile
     * ({@code /p/...}, {@code /explore/...}): blocking a post instead of the
     * profile would leave the profile free to contact.
     */
    public static Optional<String> instagram(String rawValue) {
        if (isBlank(rawValue)) {
            return Optional.empty();
        }

        String candidate = rawValue.strip();
        if (candidate.contains("/")) {
            String withScheme = HAS_SCHEME.matcher(candidate).matches() ? candidate : "https://" + candidate;
            URI uri;
            try {
                uri = URI.create(withScheme);
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
            String host = uri.getHost() == null ? "" : LEADING_WWW.matcher(uri.getHost().toLowerCase(Locale.ROOT)).replaceFirst("");
            String path = uri.getPath() == null ? "" : uri.getPath();
            String[] segments = path.replaceAll("^/+|/+$", "").split("/");
            if (!host.equals("instagram.com") || segments.length != 1) {
                return Optional.empty();
            }
            candidate = segments[0];
        }

        String handle = candidate.replaceFirst("^@", "").toLowerCase(Locale.ROOT);
        return INSTAGRAM_HANDLE.matcher(handle).matches() && !INSTAGRAM_RESERVED.contains(handle)
                ? Optional.of(handle)
                : Optional.empty();
    }

    private static String stripAccents(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
