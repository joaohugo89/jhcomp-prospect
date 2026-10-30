package com.jhcomp.prospect.shared.jobs;

/**
 * Executor de um tipo de job.
 *
 * <p>Roda fora de requisicao HTTP: nao ha sessao nem
 * {@code OrganizationContext}. A organizacao vem do proprio job, gravado a
 * partir da sessao de quem pediu, e nunca do payload.
 *
 * <p>Nao deve manter transacao aberta durante chamada de rede. Uma excecao
 * lancada faz o job ser tentado de novo, ate o limite de tentativas.
 */
public interface JobHandler {

    String jobType();

    void handle(JobEntity job);

    /**
     * Chamado quando o job falha de vez, para que o recurso ligado a ele nao
     * fique esperando para sempre.
     */
    void onGiveUp(JobEntity job);
}
