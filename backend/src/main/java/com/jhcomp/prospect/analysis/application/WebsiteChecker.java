package com.jhcomp.prospect.analysis.application;

import com.jhcomp.prospect.analysis.domain.WebsiteObservation;

/**
 * Verificacao do site de uma empresa (doc 06 §6).
 *
 * <p>Porta de saida: o caso de uso nao conhece HTTP nem a biblioteca usada.
 * Nunca lanca por falha de rede; falta de resposta vira
 * {@link WebsiteObservation.Outcome#UNREACHABLE}.
 */
public interface WebsiteChecker {

    WebsiteObservation inspect(String websiteUrl);
}
