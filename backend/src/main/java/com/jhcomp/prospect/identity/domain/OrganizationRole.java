package com.jhcomp.prospect.identity.domain;

/**
 * Papel do membro dentro da organizacao (RF-003, doc 01 §6).
 *
 * <p>Menor privilegio necessario: {@link #OPERATOR} conduz o processo
 * comercial, {@link #ADMIN} administra a organizacao.
 */
public enum OrganizationRole {
    ADMIN,
    OPERATOR;

    /** Nome da authority usada pelo Spring Security, que espera o prefixo ROLE_. */
    public String authority() {
        return "ROLE_" + name();
    }
}
