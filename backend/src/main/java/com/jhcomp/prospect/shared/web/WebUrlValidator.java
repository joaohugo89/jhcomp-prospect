package com.jhcomp.prospect.shared.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida {@link WebUrl} por parsing, e nao por expressao regular.
 *
 * <p>A URL vira link na interface, entao um esquema diferente de
 * {@code http}/{@code https} e execucao de script no clique. A primeira versao
 * decidia o esquema com um lookahead que deixava passar {@code host:porta}, e
 * por isso aceitava {@code javascript:1,alert(1)}: depois dos dois-pontos vinha
 * um digito. Decidir o esquema com regex e fragil por natureza; aqui o valor e
 * interpretado como URI e as partes sao verificadas uma a uma.
 *
 * <p>Sem {@code ://}, o valor e tratado como host (com porta e caminho
 * opcionais) e recebe {@code https://} so para ser interpretado. Um esquema
 * como {@code javascript:} vira entao um host sem ponto ou uma porta que nao e
 * numero, e e recusado.
 */
public class WebUrlValidator implements ConstraintValidator<WebUrl, String> {

    private static final Pattern EXPLICIT_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");

    /** Rotulos DNS separados por ponto; pelo menos um ponto. */
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

        // Autoridade sem host interpretavel (porta nao numerica, por exemplo)
        // e o caso de "javascript:1,alert(1)" sem esquema explicito.
        String host = uri.getHost();
        if (host == null || !HOSTNAME.matcher(host.toLowerCase(Locale.ROOT)).matches()) {
            return false;
        }

        // Usuario na URL ("https://banco.test@atacante.test") serve para
        // disfarcar o destino do link; site de empresa nao precisa disso.
        return uri.getRawUserInfo() == null;
    }
}
