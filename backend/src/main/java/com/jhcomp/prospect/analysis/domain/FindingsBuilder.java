package com.jhcomp.prospect.analysis.domain;

import java.time.Instant;
import java.util.List;

import com.jhcomp.prospect.analysis.domain.FindingDraft.Evidence;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation.Outcome;

/**
 * Transforma cadastro e observacao do site em um achado por criterio.
 *
 * <p>Regra central: so se afirma o que foi visto. Site fora do ar nao confirma
 * ausencia de site; pagina nao lida nao confirma falta de viewport. Nesses
 * casos o achado fica {@link FindingStatus#NOT_VERIFIED} e nao pontua
 * (regra 3 do produto, doc 06 §5).
 *
 * <p>Funcao pura, sem Spring nem rede: o mesmo par de entradas da sempre os
 * mesmos achados.
 */
public final class FindingsBuilder {

    public static final String WEBSITE_CHECK = "WEBSITE_CHECK/v1";
    public static final String COMPANY_RECORD = "COMPANY_RECORD/v1";
    public static final String CONFIGURATION = "CONFIGURATION/v1";

    private FindingsBuilder() {
    }

    public static List<FindingDraft> build(
            CompanyProfile company, WebsiteObservation site, ServiceArea area, Instant now) {
        return List.of(
                siteAbsent(site, now),
                noHttps(site),
                pageSignal(Criterion.NO_MOBILE_VIEWPORT, site, site.hasViewport(),
                        "A página inicial declara meta viewport, sinal de adaptação a celular.",
                        "A página inicial não declara meta viewport, sinal de que não foi adaptada a celular."),
                pageSignal(Criterion.NO_CONTACT_LINK, site, site.hasContactLink(),
                        "A página inicial tem link de telefone, e-mail ou WhatsApp.",
                        "Nenhum link de telefone (tel:), e-mail (mailto:) ou WhatsApp na página inicial. "
                                + "Formulário ou contato em outra página não são detectados."),
                new FindingDraft(Criterion.NO_ONLINE_BOOKING_OR_CATALOG, FindingStatus.NOT_VERIFIED,
                        "Verificação manual: não há detecção automática, para não afirmar sem evidência.",
                        null, List.of()),
                businessInfo(company, now),
                serviceFit(company, area, now),
                locationServed(company, area, now));
    }

    private static FindingDraft siteAbsent(WebsiteObservation site, Instant now) {
        return switch (site.outcome()) {
            case NO_WEBSITE -> new FindingDraft(Criterion.SITE_ABSENT, FindingStatus.NOT_VERIFIED,
                    "Nenhum site cadastrado. A ausência não foi confirmada: confirme na revisão se a empresa "
                            + "de fato não tem site.",
                    null,
                    List.of(new Evidence(EvidenceType.COMPANY_RECORD, null, "Cadastro da empresa sem site.",
                            now, COMPANY_RECORD, null)));
            case UNREACHABLE -> new FindingDraft(Criterion.SITE_ABSENT, FindingStatus.NOT_VERIFIED,
                    "O site cadastrado não respondeu. Pode ser indisponibilidade temporária; não confirma "
                            + "que a empresa não tenha site.",
                    null, List.of(httpEvidence(site, "Sem resposta: " + describe(site.detail()) + ".")));
            case ROBOTS_DISALLOWED, PAGE_UNUSABLE, PAGE_READ -> new FindingDraft(Criterion.SITE_ABSENT,
                    FindingStatus.CONTRADICTED, "O site cadastrado respondeu.", Confidence.HIGH,
                    List.of(httpEvidence(site, responseText(site))));
        };
    }

    private static FindingDraft noHttps(WebsiteObservation site) {
        if (site.outcome() == Outcome.NO_WEBSITE) {
            return notApplicable(Criterion.NO_HTTPS);
        }
        if (!site.siteResponded() || site.httpsAvailable() == null) {
            return unreachable(Criterion.NO_HTTPS, site);
        }
        return site.httpsAvailable()
                ? new FindingDraft(Criterion.NO_HTTPS, FindingStatus.CONTRADICTED, "O site responde por HTTPS.",
                        Confidence.HIGH, List.of(httpEvidence(site, "Conexão HTTPS estabelecida.")))
                : new FindingDraft(Criterion.NO_HTTPS, FindingStatus.CONFIRMED,
                        "O site só respondeu por HTTP; a conexão HTTPS falhou.", Confidence.HIGH,
                        List.of(httpEvidence(site, "HTTPS sem resposta; HTTP respondeu.")));
    }

    private static FindingDraft pageSignal(
            Criterion criterion, WebsiteObservation site, boolean signalPresent, String present, String absent) {
        return switch (site.outcome()) {
            case NO_WEBSITE -> notApplicable(criterion);
            case UNREACHABLE -> unreachable(criterion, site);
            case ROBOTS_DISALLOWED -> new FindingDraft(criterion, FindingStatus.NOT_VERIFIED,
                    "A página inicial não foi lida: o robots.txt do site não permite acesso automatizado.",
                    null, List.of(new Evidence(EvidenceType.ROBOTS_TXT, site.requestedUrl(),
                            "robots.txt proíbe a página inicial para robôs.", site.observedAt(), WEBSITE_CHECK, null)));
            case PAGE_UNUSABLE -> new FindingDraft(criterion, FindingStatus.NOT_VERIFIED,
                    "A página inicial não pôde ser lida.", null, List.of(httpEvidence(site, responseText(site))));
            case PAGE_READ -> new FindingDraft(criterion,
                    signalPresent ? FindingStatus.CONTRADICTED : FindingStatus.CONFIRMED,
                    signalPresent ? present : absent,
                    // Sinal de HTML pode estar em conteudo gerado por script, que
                    // nao e executado: confianca media, a revisao decide.
                    Confidence.MEDIUM,
                    List.of(new Evidence(EvidenceType.HTML_SIGNAL, site.finalUrl(),
                            signalPresent ? "Sinal encontrado no HTML." : "Sinal ausente no HTML.",
                            site.observedAt(), WEBSITE_CHECK, site.contentHash())));
        };
    }

    private static FindingDraft businessInfo(CompanyProfile company, Instant now) {
        boolean sufficient = company.segment() != null && company.hasContactChannel();
        return new FindingDraft(Criterion.BUSINESS_INFO_SUFFICIENT,
                sufficient ? FindingStatus.CONFIRMED : FindingStatus.NOT_FOUND,
                sufficient
                        ? "O cadastro tem segmento e ao menos um canal de contato."
                        : "O cadastro não tem segmento ou não tem canal de contato.",
                Confidence.HIGH,
                List.of(new Evidence(EvidenceType.COMPANY_RECORD, null, "Campos do cadastro da empresa.", now,
                        COMPANY_RECORD, null)));
    }

    private static FindingDraft serviceFit(CompanyProfile company, ServiceArea area, Instant now) {
        if (area.segments().isEmpty()) {
            return new FindingDraft(Criterion.SERVICE_FIT, FindingStatus.NOT_VERIFIED,
                    "Nenhum segmento atendido configurado: confirme na revisão.", null, List.of());
        }
        if (company.segment() == null) {
            return new FindingDraft(Criterion.SERVICE_FIT, FindingStatus.NOT_FOUND,
                    "O cadastro não informa o segmento.", Confidence.HIGH, List.of());
        }
        boolean served = area.servesSegment(company.segment());
        return new FindingDraft(Criterion.SERVICE_FIT, served ? FindingStatus.CONFIRMED : FindingStatus.CONTRADICTED,
                served ? "O segmento da empresa está entre os atendidos." : "O segmento da empresa não está entre os atendidos.",
                Confidence.HIGH,
                List.of(new Evidence(EvidenceType.CONFIGURATION, null, "Segmentos atendidos configurados.", now,
                        CONFIGURATION, null)));
    }

    private static FindingDraft locationServed(CompanyProfile company, ServiceArea area, Instant now) {
        if (area.states().isEmpty()) {
            return new FindingDraft(Criterion.LOCATION_SERVED, FindingStatus.NOT_VERIFIED,
                    "Nenhum estado atendido configurado: confirme na revisão.", null, List.of());
        }
        boolean served = area.servesState(company.state());
        return new FindingDraft(Criterion.LOCATION_SERVED, served ? FindingStatus.CONFIRMED : FindingStatus.CONTRADICTED,
                served ? "A empresa fica em estado atendido." : "A empresa fica fora dos estados atendidos.",
                Confidence.HIGH,
                List.of(new Evidence(EvidenceType.CONFIGURATION, null, "Estados atendidos configurados.", now,
                        CONFIGURATION, null)));
    }

    private static FindingDraft notApplicable(Criterion criterion) {
        return new FindingDraft(criterion, FindingStatus.NOT_APPLICABLE, "Não se aplica: nenhum site cadastrado.",
                null, List.of());
    }

    private static FindingDraft unreachable(Criterion criterion, WebsiteObservation site) {
        return new FindingDraft(criterion, FindingStatus.NOT_VERIFIED,
                "Não verificado: o site não respondeu.", null,
                List.of(httpEvidence(site, "Sem resposta: " + describe(site.detail()) + ".")));
    }

    private static Evidence httpEvidence(WebsiteObservation site, String description) {
        String url = site.finalUrl() != null ? site.finalUrl() : site.requestedUrl();
        return new Evidence(EvidenceType.HTTP_RESPONSE, url, description, site.observedAt(), WEBSITE_CHECK, null);
    }

    private static String responseText(WebsiteObservation site) {
        if (site.httpStatus() == null) {
            return "O site respondeu.";
        }
        String text = "Status HTTP " + site.httpStatus();
        return site.detail() == null ? text + "." : text + " (" + describe(site.detail()) + ").";
    }

    /** Motivos tecnicos em texto para o operador. Codigo desconhecido aparece como veio. */
    private static String describe(String detail) {
        if (detail == null) {
            return "motivo não identificado";
        }
        return switch (detail) {
            case "DNS" -> "o endereço não foi encontrado (DNS)";
            case "BLOCKED_ADDRESS" -> "o endereço aponta para rede interna e não é acessado";
            case "BLOCKED_PORT" -> "porta não permitida";
            case "TIMEOUT" -> "tempo de resposta esgotado";
            case "CONNECTION" -> "falha de conexão";
            case "TOO_MANY_REDIRECTS" -> "redirecionamentos demais";
            case "INVALID_URL" -> "endereço inválido";
            case "NOT_HTML" -> "o conteúdo não é uma página HTML";
            default -> detail;
        };
    }
}
