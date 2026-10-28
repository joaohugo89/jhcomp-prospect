package com.jhcomp.prospect.discovery;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.jhcomp.prospect.shared.web.InputPatterns;
import com.jhcomp.prospect.shared.web.WebUrlValidator;

/**
 * Validacao de uma linha importada.
 *
 * <p>Mesmas regras e mesmas mensagens do cadastro manual
 * ({@code CreateCompanyRequest}): uma planilha nao pode gravar o que o
 * formulario recusaria, inclusive URL com esquema perigoso. As mensagens
 * ficam em ingles, como no resto da API; a interface traduz.
 */
final class ImportRowValidator {

    /** Erro de um campo da linha. {@code field} e a chave da API, ou "row" para a linha inteira. */
    record RowError(String field, String message) {}

    /** Valores limpos e erros encontrados. */
    record Result(Map<ImportField, String> values, List<RowError> errors) {}

    private static final Map<ImportField, Integer> MAX_LENGTH = Map.ofEntries(
            Map.entry(ImportField.NAME, 200),
            Map.entry(ImportField.TRADE_NAME, 200),
            Map.entry(ImportField.SEGMENT, 80),
            Map.entry(ImportField.CITY, 120),
            Map.entry(ImportField.STATE, 2),
            Map.entry(ImportField.WEBSITE_URL, 500),
            Map.entry(ImportField.INSTAGRAM_URL, 500),
            Map.entry(ImportField.PHONE, 25),
            Map.entry(ImportField.BUSINESS_EMAIL, 254),
            Map.entry(ImportField.ADDRESS, 300),
            Map.entry(ImportField.NOTES, 2000));

    private static final Pattern STATE = Pattern.compile(InputPatterns.STATE);
    private static final Pattern PHONE = Pattern.compile(InputPatterns.PHONE);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ImportRowValidator() {
    }

    static Result validate(Map<ImportField, String> raw, int extraCells) {
        Map<ImportField, String> values = new EnumMap<>(ImportField.class);
        List<RowError> errors = new ArrayList<>();

        for (ImportField field : ImportField.values()) {
            String value = raw.get(field);
            String cleaned = value == null ? "" : value.strip();

            if (cleaned.isEmpty()) {
                if (field.required()) {
                    errors.add(new RowError(field.key(), "must not be blank"));
                }
                continue;
            }
            if (cleaned.length() > MAX_LENGTH.get(field)) {
                errors.add(new RowError(field.key(), "size must be between 0 and " + MAX_LENGTH.get(field)));
                continue;
            }

            String message = formatError(field, cleaned);
            if (message != null) {
                errors.add(new RowError(field.key(), message));
                continue;
            }
            values.put(field, field == ImportField.STATE ? cleaned.toUpperCase(Locale.ROOT) : cleaned);
        }

        if (extraCells > 0) {
            // Celula fora de qualquer coluna costuma ser delimitador dentro de
            // um valor sem aspas: o resto da linha estaria deslocado.
            errors.add(new RowError("row", "has more cells than the header"));
        }

        return new Result(values, List.copyOf(errors));
    }

    private static String formatError(ImportField field, String value) {
        return switch (field) {
            case STATE -> STATE.matcher(value).matches() ? null : "must be a two-letter state code";
            case WEBSITE_URL, INSTAGRAM_URL -> WebUrlValidator.isWebUrl(value) ? null : "must be an http or https URL";
            case PHONE -> PHONE.matcher(value).matches() ? null : "must be a phone number";
            case BUSINESS_EMAIL -> EMAIL.matcher(value).matches() ? null : "must be a well-formed email address";
            default -> null;
        };
    }
}
