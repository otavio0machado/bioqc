package com.bioqc.service;

import com.bioqc.dto.request.AreaQcMeasurementRequest;
import com.bioqc.dto.request.AreaQcParameterRequest;
import com.bioqc.dto.response.AreaQcMeasurementResponse;
import com.bioqc.dto.response.AreaQcParameterResponse;
import com.bioqc.entity.AreaQcMeasurement;
import com.bioqc.entity.AreaQcParameter;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.AreaQcMeasurementRepository;
import com.bioqc.repository.AreaQcParameterRepository;
import com.bioqc.util.NumericUtils;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AreaQcService {

    private static final Set<String> ALLOWED_AREAS = Set.of(
        "imunologia",
        "parasitologia",
        "microbiologia",
        "uroanalise"
    );

    private final AreaQcParameterRepository areaQcParameterRepository;
    private final AreaQcMeasurementRepository areaQcMeasurementRepository;

    public AreaQcService(
        AreaQcParameterRepository areaQcParameterRepository,
        AreaQcMeasurementRepository areaQcMeasurementRepository
    ) {
        this.areaQcParameterRepository = areaQcParameterRepository;
        this.areaQcMeasurementRepository = areaQcMeasurementRepository;
    }

    @Transactional(readOnly = true)
    public List<AreaQcParameterResponse> getParameters(String area, String analito) {
        String normalizedArea = normalizeArea(area);
        List<AreaQcParameter> parameters = (analito == null || analito.isBlank())
            ? areaQcParameterRepository.findByAreaAndIsActiveTrueOrderByCreatedAtDesc(normalizedArea)
            : areaQcParameterRepository.findByAreaAndAnalitoIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(
                normalizedArea,
                analito.trim()
            );
        return parameters.stream().map(this::toParameterResponse).toList();
    }

    @Transactional
    public AreaQcParameterResponse createParameter(String area, AreaQcParameterRequest request) {
        String normalizedArea = normalizeArea(area);
        validateParameterRequest(request);
        AreaQcParameter parameter = AreaQcParameter.builder()
            .area(normalizedArea)
            .analito(normalizeAnalito(request.analito()))
            .equipamento(normalizeNullable(request.equipamento()))
            .loteControle(normalizeNullable(request.loteControle()))
            .nivelControle(normalizeNullable(request.nivelControle()))
            .modo(normalizeMode(request.modo()))
            .alvoValor(NumericUtils.defaultIfNull(request.alvoValor()))
            .minValor(NumericUtils.defaultIfNull(request.minValor()))
            .maxValor(NumericUtils.defaultIfNull(request.maxValor()))
            .toleranciaPercentual(NumericUtils.defaultIfNull(request.toleranciaPercentual()))
            .isActive(Boolean.TRUE)
            .build();
        return toParameterResponse(areaQcParameterRepository.save(parameter));
    }

    @Transactional
    public AreaQcParameterResponse updateParameter(String area, UUID id, AreaQcParameterRequest request) {
        String normalizedArea = normalizeArea(area);
        validateParameterRequest(request);
        AreaQcParameter parameter = areaQcParameterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Parâmetro da área não encontrado"));
        if (!parameter.getArea().equals(normalizedArea)) {
            throw new ResourceNotFoundException("Parâmetro da área não encontrado");
        }
        parameter.setAnalito(normalizeAnalito(request.analito()));
        parameter.setEquipamento(normalizeNullable(request.equipamento()));
        parameter.setLoteControle(normalizeNullable(request.loteControle()));
        parameter.setNivelControle(normalizeNullable(request.nivelControle()));
        parameter.setModo(normalizeMode(request.modo()));
        parameter.setAlvoValor(NumericUtils.defaultIfNull(request.alvoValor()));
        parameter.setMinValor(NumericUtils.defaultIfNull(request.minValor()));
        parameter.setMaxValor(NumericUtils.defaultIfNull(request.maxValor()));
        parameter.setToleranciaPercentual(NumericUtils.defaultIfNull(request.toleranciaPercentual()));
        parameter.setIsActive(Boolean.TRUE);
        return toParameterResponse(areaQcParameterRepository.save(parameter));
    }

    @Transactional
    public void deleteParameter(String area, UUID id) {
        String normalizedArea = normalizeArea(area);
        AreaQcParameter parameter = areaQcParameterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Parâmetro da área não encontrado"));
        if (!parameter.getArea().equals(normalizedArea)) {
            throw new ResourceNotFoundException("Parâmetro da área não encontrado");
        }
        parameter.setIsActive(Boolean.FALSE);
        areaQcParameterRepository.save(parameter);
    }

    @Transactional(readOnly = true)
    public List<AreaQcMeasurementResponse> getMeasurements(
        String area,
        String analito,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String normalizedArea = normalizeArea(area);
        List<AreaQcMeasurement> measurements = (analito == null || analito.isBlank())
            ? areaQcMeasurementRepository.findByAreaOrderByDataMedicaoDesc(normalizedArea)
            : areaQcMeasurementRepository.findByAreaAndAnalitoIgnoreCaseOrderByDataMedicaoDesc(normalizedArea, analito.trim());
        return measurements.stream()
            .filter(item -> startDate == null || !item.getDataMedicao().isBefore(startDate))
            .filter(item -> endDate == null || !item.getDataMedicao().isAfter(endDate))
            .map(this::toMeasurementResponse)
            .toList();
    }

    @Transactional
    public AreaQcMeasurementResponse createMeasurement(String area, AreaQcMeasurementRequest request) {
        String normalizedArea = normalizeArea(area);
        AreaQcParameter parameter = resolveParameter(normalizedArea, request);

        double minAplicado;
        double maxAplicado;
        if ("PERCENTUAL".equals(parameter.getModo())) {
            double tolerancia = NumericUtils.defaultIfNull(parameter.getToleranciaPercentual());
            double alvo = NumericUtils.defaultIfNull(parameter.getAlvoValor());
            minAplicado = alvo * (1 - (tolerancia / 100D));
            maxAplicado = alvo * (1 + (tolerancia / 100D));
        } else {
            minAplicado = NumericUtils.defaultIfNull(parameter.getMinValor());
            maxAplicado = NumericUtils.defaultIfNull(parameter.getMaxValor());
        }

        String status = request.valorMedido() >= minAplicado && request.valorMedido() <= maxAplicado
            ? "APROVADO"
            : "REPROVADO";

        AreaQcMeasurement measurement = AreaQcMeasurement.builder()
            .parameter(parameter)
            .area(normalizedArea)
            .dataMedicao(request.dataMedicao())
            .analito(normalizeAnalito(request.analito()))
            .valorMedido(request.valorMedido())
            .modoUsado(parameter.getModo())
            .minAplicado(minAplicado)
            .maxAplicado(maxAplicado)
            .status(status)
            .observacao(normalizeNullable(request.observacao()))
            .build();
        return toMeasurementResponse(areaQcMeasurementRepository.save(measurement));
    }

    private AreaQcParameter resolveParameter(String area, AreaQcMeasurementRequest request) {
        if (request.parameterId() != null) {
            return resolveExplicitParameter(area, request);
        }
        return findBestParameter(area, request);
    }

    private AreaQcParameter resolveExplicitParameter(String area, AreaQcMeasurementRequest request) {
        AreaQcParameter parameter = areaQcParameterRepository.findById(request.parameterId())
            .orElseThrow(() -> new ResourceNotFoundException("Parâmetro da área não encontrado"));
        if (!parameter.getArea().equals(area) || !Boolean.TRUE.equals(parameter.getIsActive())) {
            throw new BusinessException("O parâmetro informado não está ativo para a área selecionada.");
        }
        if (!parameter.getAnalito().equalsIgnoreCase(request.analito().trim())) {
            throw new BusinessException("O parâmetro informado não pertence ao analito selecionado.");
        }
        if (!isCompatible(parameter, request)) {
            throw new BusinessException(
                "O parâmetro informado não é compatível com equipamento, lote ou nível de controle da medição."
            );
        }
        return parameter;
    }

    private AreaQcParameter findBestParameter(String area, AreaQcMeasurementRequest request) {
        List<AreaQcParameter> candidates = areaQcParameterRepository
            .findByAreaAndAnalitoIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(area, request.analito().trim());
        if (candidates.isEmpty()) {
            throw new BusinessException(
                "Nenhum parâmetro ativo para o analito informado. Cadastre um parâmetro antes de registrar medições."
            );
        }
        List<AreaQcParameter> compatibleCandidates = candidates.stream()
            .filter(parameter -> isCompatible(parameter, request))
            .toList();
        if (compatibleCandidates.isEmpty()) {
            throw new BusinessException(
                "Nenhum parâmetro ativo é compatível com equipamento, lote ou nível informados para esta medição."
            );
        }

        int bestScore = compatibleCandidates.stream()
            .mapToInt(parameter -> scoreMatch(parameter, request))
            .max()
            .orElseThrow(() -> new BusinessException("Nenhum parâmetro compatível encontrado."));

        List<AreaQcParameter> bestCandidates = compatibleCandidates.stream()
            .filter(parameter -> scoreMatch(parameter, request) == bestScore)
            .toList();

        if (bestCandidates.size() > 1) {
            throw new BusinessException(
                "Mais de um parâmetro ativo é compatível com a medição informada. Selecione explicitamente o parâmetro correto ou refine equipamento, lote e nível."
            );
        }

        return bestCandidates.getFirst();
    }

    private int scoreMatch(AreaQcParameter parameter, AreaQcMeasurementRequest request) {
        int score = 0;
        score += compareOptional(parameter.getEquipamento(), request.equipamento());
        score += compareOptional(parameter.getLoteControle(), request.loteControle());
        score += compareOptional(parameter.getNivelControle(), request.nivelControle());
        return score;
    }

    private int compareOptional(String source, String target) {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return 0;
        }
        return source.equalsIgnoreCase(target.trim()) ? 1 : 0;
    }

    private boolean isCompatible(AreaQcParameter parameter, AreaQcMeasurementRequest request) {
        return matchesOptional(parameter.getEquipamento(), request.equipamento())
            && matchesOptional(parameter.getLoteControle(), request.loteControle())
            && matchesOptional(parameter.getNivelControle(), request.nivelControle());
    }

    private boolean matchesOptional(String source, String target) {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return true;
        }
        return source.equalsIgnoreCase(target.trim());
    }

    private void validateParameterRequest(AreaQcParameterRequest request) {
        String mode = normalizeMode(request.modo());
        if (NumericUtils.defaultIfNull(request.alvoValor()) <= 0D) {
            throw new BusinessException("O valor alvo deve ser maior que zero.");
        }
        if ("INTERVALO".equals(mode)) {
            if (request.minValor() == null || request.maxValor() == null) {
                throw new BusinessException("Preencha mínimo e máximo para parâmetros por intervalo.");
            }
            if (request.minValor() > request.maxValor()) {
                throw new BusinessException("O valor mínimo não pode ser maior que o máximo.");
            }
            return;
        }
        if (request.toleranciaPercentual() == null || request.toleranciaPercentual() <= 0D) {
            throw new BusinessException("Preencha a tolerância percentual com um valor maior que zero.");
        }
    }

    private String normalizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("INTERVALO", "PERCENTUAL").contains(normalized)) {
            throw new BusinessException("Modo inválido. Use INTERVALO ou PERCENTUAL.");
        }
        return normalized;
    }

    private String normalizeArea(String area) {
        String normalized = area == null ? "" : area.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_AREAS.contains(normalized)) {
            throw new BusinessException("Área laboratorial inválida.");
        }
        return normalized;
    }

    private String normalizeAnalito(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AreaQcParameterResponse toParameterResponse(AreaQcParameter parameter) {
        return new AreaQcParameterResponse(
            parameter.getId(),
            parameter.getArea(),
            parameter.getAnalito(),
            parameter.getEquipamento(),
            parameter.getLoteControle(),
            parameter.getNivelControle(),
            parameter.getModo(),
            parameter.getAlvoValor(),
            parameter.getMinValor(),
            parameter.getMaxValor(),
            parameter.getToleranciaPercentual(),
            parameter.getIsActive(),
            parameter.getCreatedAt(),
            parameter.getUpdatedAt()
        );
    }

    private AreaQcMeasurementResponse toMeasurementResponse(AreaQcMeasurement measurement) {
        return new AreaQcMeasurementResponse(
            measurement.getId(),
            measurement.getParameter() != null ? measurement.getParameter().getId() : null,
            measurement.getParameter() != null ? measurement.getParameter().getEquipamento() : null,
            measurement.getParameter() != null ? measurement.getParameter().getLoteControle() : null,
            measurement.getParameter() != null ? measurement.getParameter().getNivelControle() : null,
            measurement.getArea(),
            measurement.getDataMedicao(),
            measurement.getAnalito(),
            measurement.getValorMedido(),
            measurement.getModoUsado(),
            measurement.getMinAplicado(),
            measurement.getMaxAplicado(),
            measurement.getStatus(),
            measurement.getObservacao(),
            measurement.getCreatedAt()
        );
    }
}
