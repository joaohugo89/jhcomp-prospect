package com.jhcomp.prospect.discovery;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The company fields a CSV column can fill.
 *
 * <p>{@code key} is the name used by the API, identical to the company form's
 * (doc 05 §4), so a validation error points at the same field in both flows.
 * The aliases allow common spreadsheet headers to be recognised without manual
 * mapping.
 */
public enum ImportField {
    NAME("name", true, Set.of("nome", "name", "empresa", "razaosocial", "nomedaempresa", "companyname")),
    TRADE_NAME("tradeName", false, Set.of("nomefantasia", "fantasia", "nomecomercial", "tradename")),
    SEGMENT("segment", false, Set.of("segmento", "ramo", "categoria", "atividade", "segment", "category")),
    CITY("city", true, Set.of("cidade", "municipio", "city")),
    STATE("state", true, Set.of("uf", "estado", "state")),
    WEBSITE_URL("websiteUrl", false, Set.of("site", "website", "url", "siteurl", "websiteurl", "pagina")),
    INSTAGRAM_URL("instagramUrl", false, Set.of("instagram", "insta", "instagramurl")),
    PHONE("phone", false, Set.of("telefone", "fone", "celular", "whatsapp", "phone", "tel", "telefonecomercial")),
    BUSINESS_EMAIL("businessEmail", false, Set.of("email", "emailcomercial", "businessemail")),
    ADDRESS("address", false, Set.of("endereco", "logradouro", "address", "enderecocomercial")),
    NOTES("notes", false, Set.of("observacoes", "observacao", "obs", "notas", "notes"));

    private final String key;
    private final boolean required;
    private final Set<String> aliases;

    ImportField(String key, boolean required, Set<String> aliases) {
        this.key = key;
        this.required = required;
        this.aliases = aliases;
    }

    public String key() {
        return key;
    }

    public boolean required() {
        return required;
    }

    public static Optional<ImportField> fromKey(String key) {
        for (ImportField field : values()) {
            if (field.key.equals(key)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    /** The field suggested for a header: ignoring accents, case, spacing and punctuation. */
    public static Optional<ImportField> guess(String header) {
        String normalized = Normalizer.normalize(header, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        for (ImportField field : values()) {
            if (field.aliases.contains(normalized)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }
}
