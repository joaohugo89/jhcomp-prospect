package com.jhcomp.prospect.company.infrastructure.persistence;

/**
 * Origem de uma empresa em SQL: o tipo da primeira proveniencia gravada, o
 * caminho pelo qual ela entrou no sistema (D29). Uma importacao que depois
 * completa a empresa nao muda a origem.
 *
 * <p>Um lugar so para a regra: o dashboard conta por ela e a fila de leads
 * filtra por ela, entao o numero que o dashboard mostra bate com a lista que
 * ele abre.
 */
public final class CompanyOriginSql {

    private CompanyOriginSql() {
    }

    /** Subconsulta sobre a empresa de alias {@code c}. */
    public static final String ORIGIN = """
            (select s.source_type from lead_source s
              where s.organization_id = c.organization_id and s.company_id = c.id
              order by s.recorded_seq limit 1)""";

    /**
     * Segmento da empresa {@code c} igual ao parametro {@code :segment}, sem
     * distinguir maiusculas nem espacos nas pontas: "Barbearia" e " barbearia"
     * sao o mesmo segmento.
     */
    public static final String SEGMENT_EQUALS = "lower(btrim(c.segment)) = lower(btrim(:segment))";

    /** Empresa {@code c} sem segmento informado. */
    public static final String WITHOUT_SEGMENT = "(c.segment is null or btrim(c.segment) = '')";
}
