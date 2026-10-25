package com.jhcomp.prospect.shared.normalization;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalizacao de nome, telefone, dominio e e-mail (decisao D10).
 *
 * <p>Componente unico, consumido pela deduplicacao (doc 06 §4) e pela lista de
 * nao contato (doc 07 §10). Duas implementacoes separadas divergiriam com o
 * tempo, e divergencia aqui significa que um pedido de nao contato pode ser
 * burlado por diferenca de formatacao do telefone. Isso e falha de
 * conformidade, nao defeito cosmetico.
 *
 * <p>Toda normalizacao e deterministica e preserva o valor original: o
 * normalizado serve para comparar, nunca para exibir (doc 04 §2).
 *
 * <p>Quando um valor nao pode ser normalizado com confianca, o metodo devolve
 * {@link Optional#empty()}. Devolver um palpite seria pior que nao devolver
 * nada: aproximaria registros distintos e, no caso do bloqueio de contato,
 * poderia tanto liberar quem pediu para nao ser contatado quanto bloquear
 * terceiros.
 */
public final class ContactNormalizer {

    /** Dois ou mais espacos, tabulacoes ou quebras de linha. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Marcas de acentuacao resultantes da decomposicao Unicode. */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    private static final Pattern LEADING_WWW = Pattern.compile("^www\\.");

    private ContactNormalizer() {
    }

    /**
     * Nome normalizado para comparacao: sem acento, sem espaco excedente, em
     * minusculas.
     *
     * <p>Mantem pontuacao e simbolos, porque removê-los aproximaria nomes
     * legitimamente diferentes.
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
     * Telefone brasileiro em formato E.164 ({@code +5535988887777}).
     *
     * <p>Aceita as formas usuais: com ou sem DDI, com DDD entre parenteses, com
     * hifen, espaco ou ponto, e com o zero de operadora antes do DDD.
     *
     * <p>Devolve vazio quando falta o DDD. Um numero de oito ou nove digitos
     * sozinho nao identifica um telefone no pais: assumir um DDD aproximaria
     * empresas de cidades diferentes e, na lista de nao contato, bloquearia
     * terceiros com o mesmo numero local.
     */
    public static Optional<String> phone(String rawPhone) {
        if (isBlank(rawPhone)) {
            return Optional.empty();
        }

        String digits = NON_DIGITS.matcher(rawPhone).replaceAll("");

        // Zero de operadora antes do DDD: 035..., 0xx35...
        if (digits.startsWith("0")) {
            digits = digits.replaceFirst("^0+", "");
        }

        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
            // Ja vem com DDI.
            return Optional.of("+" + digits);
        }

        if (digits.length() == 10 || digits.length() == 11) {
            // DDD + numero, sem DDI.
            return Optional.of("+55" + digits);
        }

        return Optional.empty();
    }

    /**
     * Dominio registravel de um site, em minusculas, sem esquema, sem
     * {@code www.}, sem porta e sem caminho.
     *
     * <p>E o sinal de deduplicacao mais confiavel depois de um identificador
     * empresarial (doc 06 §4): dois registros com o mesmo dominio sao
     * praticamente sempre a mesma empresa.
     */
    public static Optional<String> domain(String rawUrl) {
        if (isBlank(rawUrl)) {
            return Optional.empty();
        }

        String candidate = rawUrl.strip();

        // Sem esquema, URI trata tudo como caminho e nao enxerga o host.
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
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
                // Ponto final de FQDN absoluto.
                .replaceAll("\\.$", "");

        // Precisa de ao menos um ponto: "localhost" ou "empresa" nao sao
        // dominios de site de empresa.
        return normalized.contains(".") ? Optional.of(normalized) : Optional.empty();
    }

    /** E-mail em minusculas e sem espaco em volta. */
    public static Optional<String> email(String rawEmail) {
        if (isBlank(rawEmail)) {
            return Optional.empty();
        }

        String normalized = rawEmail.strip().toLowerCase(Locale.ROOT);

        // Validacao de formato e responsabilidade da validacao de entrada; aqui
        // basta recusar o que claramente nao e endereco.
        return normalized.indexOf('@') > 0 && normalized.indexOf('@') < normalized.length() - 1
                ? Optional.of(normalized)
                : Optional.empty();
    }

    /**
     * Identificador usado na lista de nao contato.
     *
     * <p>Aceita telefone ou e-mail e aplica a mesma normalizacao do resto do
     * sistema, que e o ponto da decisao D10.
     */
    public static Optional<String> contactIdentifier(String rawValue) {
        if (isBlank(rawValue)) {
            return Optional.empty();
        }

        return rawValue.contains("@") ? email(rawValue) : phone(rawValue);
    }

    private static String stripAccents(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
