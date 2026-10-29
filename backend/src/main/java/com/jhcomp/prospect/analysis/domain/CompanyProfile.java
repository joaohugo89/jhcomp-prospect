package com.jhcomp.prospect.analysis.domain;

/**
 * O que a analise le do cadastro da empresa.
 *
 * <p>So o necessario para os criterios: nem notas, nem endereco, nem e-mail em
 * claro (minimizacao, doc 07 §2).
 */
public record CompanyProfile(
        String websiteUrl, String segment, String state, boolean hasPhone, boolean hasEmail, boolean hasInstagram) {

    public boolean hasWebsite() {
        return websiteUrl != null && !websiteUrl.isBlank();
    }

    public boolean hasContactChannel() {
        return hasPhone || hasEmail || hasInstagram || hasWebsite();
    }
}
