package com.jhcomp.prospect.analysis.domain;

import java.time.Instant;

/**
 * O que a verificacao automatica viu no site (doc 06 §6).
 *
 * <p>Conteudo de pagina externa e dado nao confiavel (regra 9 do produto):
 * daqui saem apenas sinais booleanos e um hash, nunca texto da pagina.
 *
 * @param httpsAvailable nulo quando nao foi possivel saber
 * @param detail motivo tecnico curto, como {@code DNS} ou {@code TIMEOUT}
 */
public record WebsiteObservation(
        Outcome outcome,
        String requestedUrl,
        String finalUrl,
        Boolean httpsAvailable,
        Integer httpStatus,
        boolean hasViewport,
        boolean hasContactLink,
        String contentHash,
        String detail,
        Instant observedAt) {

    public enum Outcome {
        /** Nenhum site cadastrado: nada foi acessado. */
        NO_WEBSITE,
        /** Nenhuma resposta: DNS, conexao, endereco bloqueado ou tempo esgotado. */
        UNREACHABLE,
        /** O site respondeu, mas o robots.txt nao permite ler a pagina inicial. */
        ROBOTS_DISALLOWED,
        /** A pagina inicial respondeu com erro ou com conteudo que nao e HTML. */
        PAGE_UNUSABLE,
        /** Pagina inicial lida. */
        PAGE_READ
    }

    public static WebsiteObservation noWebsite(Instant now) {
        return new WebsiteObservation(Outcome.NO_WEBSITE, null, null, null, null, false, false, null, null, now);
    }

    public boolean siteResponded() {
        return outcome == Outcome.ROBOTS_DISALLOWED || outcome == Outcome.PAGE_UNUSABLE || outcome == Outcome.PAGE_READ;
    }
}
