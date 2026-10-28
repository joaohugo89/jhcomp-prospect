package com.jhcomp.prospect.discovery;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jhcomp.prospect.company.application.CompanyService;
import com.jhcomp.prospect.company.domain.DuplicateSignal;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.shared.normalization.ContactNormalizer;

/**
 * Marca como possivel duplicata as linhas validas que coincidem com uma empresa
 * ja cadastrada ou com uma linha anterior do mesmo arquivo (doc 06 §4).
 *
 * <p>Mesmos sinais e mesma normalizacao do detalhe da empresa (D10): dominio,
 * telefone e nome normalizado na mesma cidade e estado. Nome parecido nao e
 * sinal. Marcar nao descarta: a linha fica no relatorio, e o operador decide
 * se importa (RF-014).
 */
final class DuplicateClassifier {

    private DuplicateClassifier() {
    }

    /** Chaves de comparacao de uma linha. Nulo quando o valor nao normaliza. */
    private record Keys(String domain, String phone, String nameCityState) {

        static Keys of(Map<ImportField, String> values) {
            return new Keys(
                    ContactNormalizer.domain(values.get(ImportField.WEBSITE_URL)).orElse(null),
                    ContactNormalizer.phone(values.get(ImportField.PHONE)).orElse(null),
                    ContactNormalizer.name(values.get(ImportField.NAME))
                            .map(name -> nameKey(name, values.get(ImportField.CITY), values.get(ImportField.STATE)))
                            .orElse(null));
        }
    }

    private static String nameKey(String normalizedName, String city, String state) {
        return normalizedName + '|' + (city == null ? "" : city.strip().toLowerCase(Locale.ROOT)) + '|'
                + (state == null ? "" : state.strip().toUpperCase(Locale.ROOT));
    }

    /**
     * Classifica as linhas com status {@code VALID}.
     *
     * @param checkWithinFile {@code false} na confirmacao, quando so interessa o
     *     que foi gravado na base desde a analise
     * @return quantas linhas passaram a duplicata
     */
    static int classify(List<ImportRowEntity> rows, CompanyService companyService, boolean checkWithinFile) {
        Map<ImportRowEntity, Keys> keys = new HashMap<>();
        Set<String> domains = new HashSet<>();
        Set<String> phones = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (ImportRowEntity row : rows) {
            if (row.getStatus() == ImportRowEntity.Status.REJECTED) {
                continue;
            }
            Keys rowKeys = Keys.of(row.values());
            keys.put(row, rowKeys);
            if (rowKeys.domain() != null) {
                domains.add(rowKeys.domain());
            }
            if (rowKeys.phone() != null) {
                phones.add(rowKeys.phone());
            }
            ContactNormalizer.name(row.values().get(ImportField.NAME)).ifPresent(names::add);
        }

        List<CompanyEntity> existing = companyService.findByNormalizedValues(domains, phones, names);

        // Primeira linha do arquivo com cada chave. Duplicatas tambem entram:
        // a segunda copia de uma linha ja duplicada continua sendo copia.
        Map<String, Integer> firstRowByKey = new HashMap<>();
        int marked = 0;

        for (ImportRowEntity row : rows) {
            Keys rowKeys = keys.get(row);
            if (rowKeys == null) {
                continue;
            }

            if (row.getStatus() == ImportRowEntity.Status.VALID) {
                if (markAgainstExisting(row, rowKeys, existing)) {
                    marked++;
                } else if (checkWithinFile && markAgainstFile(row, rowKeys, firstRowByKey)) {
                    marked++;
                }
            }

            if (checkWithinFile) {
                remember(firstRowByKey, "d:" + rowKeys.domain(), rowKeys.domain(), row.getRowNumber());
                remember(firstRowByKey, "p:" + rowKeys.phone(), rowKeys.phone(), row.getRowNumber());
                remember(firstRowByKey, "n:" + rowKeys.nameCityState(), rowKeys.nameCityState(), row.getRowNumber());
            }
        }
        return marked;
    }

    private static boolean markAgainstExisting(ImportRowEntity row, Keys rowKeys, List<CompanyEntity> existing) {
        for (CompanyEntity company : existing) {
            Set<DuplicateSignal> signals = EnumSet.noneOf(DuplicateSignal.class);
            if (rowKeys.domain() != null && rowKeys.domain().equals(company.getNormalizedDomain())) {
                signals.add(DuplicateSignal.DOMAIN);
            }
            if (rowKeys.phone() != null && rowKeys.phone().equals(company.getNormalizedPhone())) {
                signals.add(DuplicateSignal.PHONE);
            }
            if (rowKeys.nameCityState() != null && rowKeys.nameCityState()
                    .equals(nameKey(company.getNormalizedName(), company.getCity(), company.getState()))) {
                signals.add(DuplicateSignal.NAME_CITY_STATE);
            }
            if (!signals.isEmpty()) {
                row.markDuplicate(company.getId(), null, join(signals));
                return true;
            }
        }
        return false;
    }

    private static boolean markAgainstFile(ImportRowEntity row, Keys rowKeys, Map<String, Integer> firstRowByKey) {
        Map<DuplicateSignal, String> lookups = new EnumMap<>(DuplicateSignal.class);
        if (rowKeys.domain() != null) {
            lookups.put(DuplicateSignal.DOMAIN, "d:" + rowKeys.domain());
        }
        if (rowKeys.phone() != null) {
            lookups.put(DuplicateSignal.PHONE, "p:" + rowKeys.phone());
        }
        if (rowKeys.nameCityState() != null) {
            lookups.put(DuplicateSignal.NAME_CITY_STATE, "n:" + rowKeys.nameCityState());
        }

        Set<DuplicateSignal> signals = EnumSet.noneOf(DuplicateSignal.class);
        Integer firstRow = null;
        for (Map.Entry<DuplicateSignal, String> lookup : lookups.entrySet()) {
            Integer match = firstRowByKey.get(lookup.getValue());
            if (match != null) {
                signals.add(lookup.getKey());
                firstRow = firstRow == null ? match : Math.min(firstRow, match);
            }
        }
        if (signals.isEmpty()) {
            return false;
        }
        row.markDuplicate(null, firstRow, join(signals));
        return true;
    }

    private static void remember(Map<String, Integer> firstRowByKey, String key, String value, int rowNumber) {
        if (value != null) {
            firstRowByKey.putIfAbsent(key, rowNumber);
        }
    }

    private static String join(Set<DuplicateSignal> signals) {
        return signals.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    static Set<DuplicateSignal> parseSignals(String signals) {
        Set<DuplicateSignal> parsed = new LinkedHashSet<>();
        if (signals != null && !signals.isBlank()) {
            for (String signal : signals.split(",")) {
                parsed.add(DuplicateSignal.valueOf(signal));
            }
        }
        return parsed;
    }
}
