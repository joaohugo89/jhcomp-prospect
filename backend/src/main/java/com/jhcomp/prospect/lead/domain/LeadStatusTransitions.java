package com.jhcomp.prospect.lead.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Transicoes validas do ciclo do lead (doc 06 §2 e §3).
 *
 * <p>Regras que esta classe garante:
 *
 * <ul>
 *   <li>{@code WON} e {@code LOST} sao terminais: um novo ciclo comercial exige
 *       um novo lead.
 *   <li>{@code DO_NOT_CONTACT} e alcancavel de qualquer estado, porque o pedido
 *       do titular tem de ser atendido prontamente (doc 06 §9), e nao e
 *       reversivel por esta operacao: remover o bloqueio exige processo
 *       autorizado e justificado, que nao e uma simples mudanca de estado.
 *   <li>Retroceder de {@code CONTACTED} para {@code QUALIFIED} e permitido:
 *       um contato pode se mostrar prematuro, e corrigir a classificacao e
 *       legitimo.
 * </ul>
 *
 * <p>Esta classe nao decide quando registrar {@code CONTACTED}. O doc 06 §2
 * proibe mover o lead automaticamente ao gerar ou aprovar uma mensagem: so o
 * operador, confirmando que o contato ocorreu. A regra e aplicada em quem chama,
 * ja que aqui so se conhece o par de estados.
 *
 * <p>Sem dependencia de Spring: e regra de dominio (doc 03 §5).
 */
public final class LeadStatusTransitions {

    private static final Map<LeadStatus, Set<LeadStatus>> ALLOWED = buildAllowed();

    private LeadStatusTransitions() {
    }

    private static Map<LeadStatus, Set<LeadStatus>> buildAllowed() {
        Map<LeadStatus, Set<LeadStatus>> allowed = new EnumMap<>(LeadStatus.class);

        allowed.put(LeadStatus.NEW, EnumSet.of(LeadStatus.QUALIFIED, LeadStatus.LOST));
        allowed.put(LeadStatus.QUALIFIED, EnumSet.of(LeadStatus.CONTACT_APPROVED, LeadStatus.LOST));
        allowed.put(
                LeadStatus.CONTACT_APPROVED,
                EnumSet.of(LeadStatus.CONTACTED, LeadStatus.QUALIFIED, LeadStatus.LOST));
        allowed.put(
                LeadStatus.CONTACTED,
                EnumSet.of(LeadStatus.IN_CONVERSATION, LeadStatus.QUALIFIED, LeadStatus.LOST));
        allowed.put(
                LeadStatus.IN_CONVERSATION,
                EnumSet.of(LeadStatus.PROPOSAL_SENT, LeadStatus.WON, LeadStatus.LOST));
        allowed.put(LeadStatus.PROPOSAL_SENT, EnumSet.of(LeadStatus.WON, LeadStatus.LOST));

        // Terminais e bloqueio nao tem saida por mudanca de estado.
        allowed.put(LeadStatus.WON, EnumSet.noneOf(LeadStatus.class));
        allowed.put(LeadStatus.LOST, EnumSet.noneOf(LeadStatus.class));
        allowed.put(LeadStatus.DO_NOT_CONTACT, EnumSet.noneOf(LeadStatus.class));

        // DO_NOT_CONTACT e alcancavel de qualquer estado nao terminal.
        for (LeadStatus from : LeadStatus.values()) {
            if (!from.isTerminal() && from != LeadStatus.DO_NOT_CONTACT) {
                allowed.get(from).add(LeadStatus.DO_NOT_CONTACT);
            }
        }

        return Map.copyOf(allowed);
    }

    public static boolean isAllowed(LeadStatus from, LeadStatus to) {
        if (from == to) {
            // Reenviar o mesmo estado nao e transicao: quem chama trata como
            // operacao sem efeito, nao como erro.
            return true;
        }

        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Estados alcancaveis a partir de {@code from}, para exibicao na interface. */
    public static Set<LeadStatus> allowedFrom(LeadStatus from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, Set.of()));
    }

    /** Estado inicial de um lead recem-criado (doc 06 §2). */
    public static LeadStatus initial() {
        return LeadStatus.NEW;
    }
}
