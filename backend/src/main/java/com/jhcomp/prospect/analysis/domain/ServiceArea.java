package com.jhcomp.prospect.analysis.domain;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Segmentos e estados atendidos, para os criterios de compatibilidade e
 * localizacao (doc 06 §7). Comparacao sem diferenca de caixa.
 */
public record ServiceArea(Set<String> segments, Set<String> states) {

    public ServiceArea {
        segments = normalize(segments);
        states = normalize(states);
    }

    private static Set<String> normalize(Set<String> values) {
        return values.stream()
                .map(value -> value.strip().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean servesSegment(String segment) {
        return segments.contains(segment.strip().toUpperCase(Locale.ROOT));
    }

    public boolean servesState(String state) {
        return states.contains(state.strip().toUpperCase(Locale.ROOT));
    }
}
