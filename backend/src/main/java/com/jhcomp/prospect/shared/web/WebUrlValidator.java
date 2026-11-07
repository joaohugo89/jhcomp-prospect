package com.jhcomp.prospect.shared.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link WebUrl} by parsing, not by regular expression.
 *
 * <p>The URL becomes a link in the interface, so a scheme other than
 * {@code http}/{@code https} means script execution on click. The first
 * version decided the scheme with a lookahead that let {@code host:port}
 * through, and therefore accepted {@code javascript:1,alert(1)}: after the
 * colon came a digit. Deciding a scheme with a regex is fragile by nature;
 * here the value is parsed as a URI and its parts are checked one by one.
 *
 * <p>Without {@code ://}, the value is treated as a host — with optional port
 * and path — and receives {@code https://} purely so it can be parsed. A
 * scheme such as {@code javascript:} then becomes either a host with no dot or
 * a port that is not a number, and is refused.
 */
public class WebUrlValidator implements ConstraintValidator<WebUrl, String> {

    private static final Pattern EXPLICIT_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");

    /** DNS labels separated by dots; at least one dot. */
    private static final Pattern HOSTNAME =
            Pattern.compile("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.?$");

    private static final Pattern FORBIDDEN_CHARACTERS = Pattern.compile("[\\s<>\"'\\\\`{}|^]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return isWebUrl(value);
    }

    public static boolean isWebUrl(String value) {
        if (FORBIDDEN_CHARACTERS.matcher(value).find()) {
            return false;
        }

        String candidate = EXPLICIT_SCHEME.matcher(value).matches() ? value : "https://" + value;

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException exception) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
            return false;
        }

        // An authority with no parsable host — a non-numeric port, say — is
        // the case of "javascript:1,alert(1)" with no explicit scheme.
        String host = uri.getHost();
        if (host == null || !HOSTNAME.matcher(host.toLowerCase(Locale.ROOT)).matches()) {
            return false;
        }

        // Userinfo in a URL ("https://bank.test@attacker.test") exists to
        // disguise the link's destination; a company website has no need of it.
        return uri.getRawUserInfo() == null;
    }
}
