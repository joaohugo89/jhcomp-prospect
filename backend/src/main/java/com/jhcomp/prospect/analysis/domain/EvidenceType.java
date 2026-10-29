package com.jhcomp.prospect.analysis.domain;

/** Origem de uma evidencia. */
public enum EvidenceType {
    /** Resposta (ou falta de resposta) de uma requisicao ao site. */
    HTTP_RESPONSE,
    /** Sinal observado no HTML da pagina, como meta viewport ou link de contato. */
    HTML_SIGNAL,
    ROBOTS_TXT,
    /** Dado do cadastro da empresa. */
    COMPANY_RECORD,
    /** Configuracao de atendimento da organizacao (segmentos, estados). */
    CONFIGURATION,
    /** Observacao registrada pelo operador na revisao. */
    OPERATOR_NOTE
}
