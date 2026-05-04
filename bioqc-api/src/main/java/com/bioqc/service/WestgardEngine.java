package com.bioqc.service;

import com.bioqc.entity.QcRecord;
import com.bioqc.util.NumericUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Motor responsável por avaliar as regras clássicas de Westgard.
 */
@Service
public class WestgardEngine {

    public enum Severity {
        WARNING,
        REJECTION
    }

    public record Violation(String rule, String description, Severity severity) {
    }

    /**
     * Avalia o registro atual com base no histórico já ordenado do mais recente para o mais antigo.
     */
    public List<Violation> evaluate(QcRecord current, List<QcRecord> history) {
        List<Violation> violations = new ArrayList<>();
        List<QcRecord> safeHistory = history == null ? List.of() : history;

        if (current.getTargetSd() == null || current.getTargetSd() == 0D) {
            violations.add(new Violation(
                "SD=0",
                "Desvio Padrão igual a zero.",
                Severity.WARNING
            ));
            return Collections.unmodifiableList(violations);
        }

        double zScore = calculateZScore(current);

        if (Math.abs(zScore) > 2) {
            violations.add(new Violation(
                "1-2s",
                "Alerta: Valor excede 2 SD.",
                Severity.WARNING
            ));
            if (Math.abs(zScore) > 3) {
                violations.add(new Violation(
                    "1-3s",
                    "Erro Aleatório: Valor excede 3 SD.",
                    Severity.REJECTION
                ));
            }
            check22s(current, safeHistory).ifPresent(violations::add);
            checkR4s(current, safeHistory).ifPresent(violations::add);
        }

        check41s(current, safeHistory).ifPresent(violations::add);
        check10x(current, safeHistory).ifPresent(violations::add);

        return Collections.unmodifiableList(violations);
    }

    public double calculateZScore(QcRecord record) {
        return NumericUtils.calculateZScore(record.getValue(), record.getTargetValue(), record.getTargetSd());
    }

    private java.util.Optional<Violation> check22s(QcRecord current, List<QcRecord> history) {
        if (history.isEmpty() || history.getFirst().getTargetSd() == null || history.getFirst().getTargetSd() == 0D) {
            return java.util.Optional.empty();
        }
        double currentZ = calculateZScore(current);
        double previousZ = calculateZScore(history.getFirst());
        if ((currentZ > 2 && previousZ > 2) || (currentZ < -2 && previousZ < -2)) {
            return java.util.Optional.of(new Violation(
                "2-2s",
                "Erro Sistemático: Dois valores consecutivos excedem 2 SD do mesmo lado.",
                Severity.REJECTION
            ));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Violation> checkR4s(QcRecord current, List<QcRecord> history) {
        if (history.isEmpty() || history.getFirst().getTargetSd() == null || history.getFirst().getTargetSd() == 0D) {
            return java.util.Optional.empty();
        }
        double currentZ = calculateZScore(current);
        double previousZ = calculateZScore(history.getFirst());
        if (Math.abs(currentZ - previousZ) > 4) {
            return java.util.Optional.of(new Violation(
                "R-4s",
                "Erro Aleatório: Diferença entre pontos consecutivos excede 4 SD.",
                Severity.REJECTION
            ));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Violation> check41s(QcRecord current, List<QcRecord> history) {
        if (history.size() < 3) {
            return java.util.Optional.empty();
        }
        List<Double> zScores = new ArrayList<>();
        zScores.add(calculateZScore(current));
        for (int index = 0; index < 3; index++) {
            QcRecord record = history.get(index);
            if (record.getTargetSd() == null || record.getTargetSd() == 0D) {
                return java.util.Optional.empty();
            }
            zScores.add(calculateZScore(record));
        }

        boolean allAbove = zScores.stream().allMatch(score -> score > 1);
        boolean allBelow = zScores.stream().allMatch(score -> score < -1);
        if (allAbove || allBelow) {
            return java.util.Optional.of(new Violation(
                "4-1s",
                "Erro Sistemático: Quatro valores consecutivos excedem 1 SD do mesmo lado.",
                Severity.REJECTION
            ));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Violation> check10x(QcRecord current, List<QcRecord> history) {
        if (history.size() < 9) {
            return java.util.Optional.empty();
        }
        List<Double> zScores = new ArrayList<>();
        zScores.add(calculateZScore(current));
        for (int index = 0; index < 9; index++) {
            QcRecord record = history.get(index);
            if (record.getTargetSd() == null || record.getTargetSd() == 0D) {
                return java.util.Optional.empty();
            }
            zScores.add(calculateZScore(record));
        }

        boolean allAbove = zScores.stream().allMatch(score -> score > 0);
        boolean allBelow = zScores.stream().allMatch(score -> score < 0);
        if (allAbove || allBelow) {
            return java.util.Optional.of(new Violation(
                "10x",
                "Erro Sistemático: Dez resultados consecutivos estão do mesmo lado da média.",
                Severity.REJECTION
            ));
        }
        return java.util.Optional.empty();
    }
}
