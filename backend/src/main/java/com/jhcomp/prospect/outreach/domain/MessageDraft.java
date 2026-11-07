package com.jhcomp.prospect.outreach.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Message content already sanitised and validated against the channel's
 * rules.
 *
 * <p>It covers both the AI's output and the operator's edit: the two go
 * through the same rules. Construct only through {@link #of}, which returns
 * the reason for refusal instead of throwing, so the application layer can
 * choose the right error.
 */
public record MessageDraft(String subject, String body, List<String> unverifiedClaims) {

    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_CLAIMS = 10;
    public static final int MAX_CLAIM_LENGTH = 300;

    /** C0 controls and DEL, apart from line break and tab. */
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0B-\\x1F\\x7F]");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public MessageDraft {
        unverifiedClaims = List.copyOf(unverifiedClaims);
    }

    /** The result of {@link #of}: a valid draft, or the reason for refusal. */
    public record Result(Optional<MessageDraft> draft, String violation) {

        public static Result valid(MessageDraft draft) {
            return new Result(Optional.of(draft), null);
        }

        public static Result invalid(String violation) {
            return new Result(Optional.empty(), violation);
        }
    }

    public static Result of(OutreachChannel channel, String subject, String body, List<String> claims) {
        String cleanBody = body == null ? "" : clean(body).strip();
        if (cleanBody.isEmpty()) {
            return Result.invalid("Message body must not be empty");
        }
        if (cleanBody.length() > channel.maxBodyLength()) {
            return Result.invalid("Message body exceeds " + channel.maxBodyLength() + " characters for " + channel);
        }

        String cleanSubject = subject == null ? null : singleLine(subject);
        if (cleanSubject != null && cleanSubject.isEmpty()) {
            cleanSubject = null;
        }
        if (cleanSubject != null && !channel.allowsSubject()) {
            return Result.invalid("Subject is allowed only for EMAIL");
        }
        if (cleanSubject != null && cleanSubject.length() > MAX_SUBJECT_LENGTH) {
            return Result.invalid("Subject exceeds " + MAX_SUBJECT_LENGTH + " characters");
        }

        List<String> cleanClaims = new ArrayList<>();
        for (String claim : claims == null ? List.<String>of() : claims) {
            String line = claim == null ? "" : singleLine(claim);
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() > MAX_CLAIM_LENGTH) {
                return Result.invalid("Unverified claim exceeds " + MAX_CLAIM_LENGTH + " characters");
            }
            cleanClaims.add(line);
        }
        if (cleanClaims.size() > MAX_CLAIMS) {
            return Result.invalid("At most " + MAX_CLAIMS + " unverified claims");
        }
        return Result.valid(new MessageDraft(cleanSubject, cleanBody, cleanClaims));
    }

    private static String clean(String value) {
        return CONTROL.matcher(value.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("");
    }

    private static String singleLine(String value) {
        return WHITESPACE.matcher(clean(value)).replaceAll(" ").strip();
    }
}
