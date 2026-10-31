package com.jhcomp.prospect.outreach.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Conteudo de mensagem ja saneado e validado contra as regras do canal.
 *
 * <p>Vale para a saida da IA e para a edicao do operador: as duas passam pelas
 * mesmas regras. Construir so por {@link #of}, que devolve o motivo da recusa
 * em vez de lancar, para a camada de aplicacao escolher o erro certo.
 */
public record MessageDraft(String subject, String body, List<String> unverifiedClaims) {

    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_CLAIMS = 10;
    public static final int MAX_CLAIM_LENGTH = 300;

    /** Controles C0 e DEL, exceto quebra de linha e tabulacao. */
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0B-\\x1F\\x7F]");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public MessageDraft {
        unverifiedClaims = List.copyOf(unverifiedClaims);
    }

    /** Resultado de {@link #of}: rascunho valido ou motivo da recusa. */
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
