package com.bioqc.service.reports.v2.generator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mapa tipado e imutavel de filtros de execucao. Encapsula o raw map recebido
 * do controller, depois de validado pelo {@code FilterValidator}, e oferece
 * acessores tipados que retornam {@link Optional} para cada {@code key}.
 *
 * <p>Chaves ausentes retornam {@code Optional.empty()}. Tipos incompativeis
 * geram {@link IllegalArgumentException} — o validator ja deveria ter filtrado
 * isso.
 */
public record ReportFilters(Map<String, Object> values) {

    public ReportFilters {
        values = values == null ? Map.of() : Collections.unmodifiableMap(values);
    }

    public static ReportFilters empty() {
        return new ReportFilters(Map.of());
    }

    public Optional<String> getString(String key) {
        Object raw = values.get(key);
        if (raw == null) return Optional.empty();
        return Optional.of(raw.toString());
    }

    public Optional<Integer> getInteger(String key) {
        Object raw = values.get(key);
        if (raw == null) return Optional.empty();
        if (raw instanceof Integer i) return Optional.of(i);
        if (raw instanceof Number n) return Optional.of(n.intValue());
        if (raw instanceof String s) {
            try {
                return Optional.of(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Filtro " + key + " nao e um inteiro valido: " + s);
            }
        }
        throw new IllegalArgumentException("Filtro " + key + " nao e um inteiro");
    }

    public Optional<LocalDate> getDate(String key) {
        Object raw = values.get(key);
        if (raw == null) return Optional.empty();
        if (raw instanceof LocalDate ld) return Optional.of(ld);
        if (raw instanceof String s) {
            try {
                return Optional.of(LocalDate.parse(s.trim()));
            } catch (Exception ex) {
                throw new IllegalArgumentException("Filtro " + key + " nao e uma data ISO valida: " + s);
            }
        }
        throw new IllegalArgumentException("Filtro " + key + " nao e uma data");
    }

    @SuppressWarnings("unchecked")
    public Optional<List<UUID>> getUuidList(String key) {
        Object raw = values.get(key);
        if (raw == null) return Optional.empty();
        if (raw instanceof List<?> list) {
            List<UUID> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof UUID u) {
                    converted.add(u);
                } else if (item instanceof String s) {
                    try {
                        converted.add(UUID.fromString(s.trim()));
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("Filtro " + key + " contem UUID invalido: " + s);
                    }
                } else {
                    throw new IllegalArgumentException("Filtro " + key + " deve ser lista de UUIDs");
                }
            }
            return Optional.of(List.copyOf(converted));
        }
        throw new IllegalArgumentException("Filtro " + key + " deve ser uma lista");
    }

    public Optional<Boolean> getBoolean(String key) {
        Object raw = values.get(key);
        if (raw == null) return Optional.empty();
        if (raw instanceof Boolean b) return Optional.of(b);
        if (raw instanceof String s) return Optional.of(Boolean.parseBoolean(s.trim()));
        throw new IllegalArgumentException("Filtro " + key + " nao e booleano");
    }

    /**
     * Retorna lista de strings (usado por filtros STRING_ENUM_MULTI).
     * Aceita tanto um {@code List<?>} quanto uma string CSV.
     */
    public Optional<List<String>> getStringList(String key) {
        Object raw = values.get(key);
        if (raw == null) return Optional.empty();
        if (raw instanceof List<?> list) {
            List<String> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item == null) continue;
                converted.add(item.toString());
            }
            return Optional.of(List.copyOf(converted));
        }
        if (raw instanceof String s) {
            if (s.isBlank()) return Optional.of(List.of());
            List<String> parts = new ArrayList<>();
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) parts.add(trimmed);
            }
            return Optional.of(List.copyOf(parts));
        }
        throw new IllegalArgumentException("Filtro " + key + " deve ser uma lista de strings");
    }
}
