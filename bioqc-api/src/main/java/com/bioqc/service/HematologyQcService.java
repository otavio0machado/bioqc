package com.bioqc.service;

import com.bioqc.dto.request.HematologyBioRequest;
import com.bioqc.dto.request.HematologyMeasurementRequest;
import com.bioqc.dto.request.HematologyParameterRequest;
import com.bioqc.dto.response.HematologyMeasurementResponse;
import com.bioqc.dto.response.HematologyParameterResponse;
import com.bioqc.entity.HematologyBioRecord;
import com.bioqc.entity.HematologyQcMeasurement;
import com.bioqc.entity.HematologyQcParameter;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.HematologyBioRecordRepository;
import com.bioqc.repository.HematologyQcMeasurementRepository;
import com.bioqc.repository.HematologyQcParameterRepository;
import com.bioqc.util.NumericUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HematologyQcService {

    private final HematologyQcParameterRepository hematologyQcParameterRepository;
    private final HematologyQcMeasurementRepository hematologyQcMeasurementRepository;
    private final HematologyBioRecordRepository hematologyBioRecordRepository;

    public HematologyQcService(
        HematologyQcParameterRepository hematologyQcParameterRepository,
        HematologyQcMeasurementRepository hematologyQcMeasurementRepository,
        HematologyBioRecordRepository hematologyBioRecordRepository
    ) {
        this.hematologyQcParameterRepository = hematologyQcParameterRepository;
        this.hematologyQcMeasurementRepository = hematologyQcMeasurementRepository;
        this.hematologyBioRecordRepository = hematologyBioRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<HematologyParameterResponse> getParameters(String analito) {
        List<HematologyQcParameter> parameters = (analito == null || analito.isBlank())
            ? hematologyQcParameterRepository.findByIsActiveTrue()
            : hematologyQcParameterRepository.findByAnalitoAndIsActiveTrue(analito);
        return parameters.stream().map(this::toParameterResponse).toList();
    }

    @Transactional
    public HematologyParameterResponse createParameter(HematologyParameterRequest request) {
        HematologyQcParameter parameter = HematologyQcParameter.builder()
            .analito(request.analito())
            .equipamento(request.equipamento())
            .loteControle(request.loteControle())
            .nivelControle(request.nivelControle())
            .modo(request.modo())
            .alvoValor(NumericUtils.defaultIfNull(request.alvoValor()))
            .minValor(NumericUtils.defaultIfNull(request.minValor()))
            .maxValor(NumericUtils.defaultIfNull(request.maxValor()))
            .toleranciaPercentual(NumericUtils.defaultIfNull(request.toleranciaPercentual()))
            .isActive(Boolean.TRUE)
            .build();
        return toParameterResponse(hematologyQcParameterRepository.save(parameter));
    }

    @Transactional
    public HematologyParameterResponse updateParameter(UUID id, HematologyParameterRequest request) {
        HematologyQcParameter parameter = hematologyQcParameterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Parâmetro de hematologia não encontrado"));
        parameter.setAnalito(request.analito());
        parameter.setEquipamento(request.equipamento());
        parameter.setLoteControle(request.loteControle());
        parameter.setNivelControle(request.nivelControle());
        parameter.setModo(request.modo());
        parameter.setAlvoValor(NumericUtils.defaultIfNull(request.alvoValor()));
        parameter.setMinValor(NumericUtils.defaultIfNull(request.minValor()));
        parameter.setMaxValor(NumericUtils.defaultIfNull(request.maxValor()));
        parameter.setToleranciaPercentual(NumericUtils.defaultIfNull(request.toleranciaPercentual()));
        return toParameterResponse(hematologyQcParameterRepository.save(parameter));
    }

    @Transactional
    public void deleteParameter(UUID id) {
        if (!hematologyQcParameterRepository.existsById(id)) {
            throw new ResourceNotFoundException("Parâmetro de hematologia não encontrado");
        }
        hematologyQcParameterRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<HematologyMeasurementResponse> getMeasurements(UUID parameterId) {
        List<HematologyQcMeasurement> measurements = (parameterId == null)
            ? hematologyQcMeasurementRepository.findAll(Sort.by(Sort.Direction.DESC, "dataMedicao"))
            : hematologyQcMeasurementRepository.findByParameterIdOrderByDataMedicaoDesc(parameterId);
        return measurements.stream().map(this::toMeasurementResponse).toList();
    }

    @Transactional
    public HematologyMeasurementResponse createMeasurement(HematologyMeasurementRequest request) {
        HematologyQcParameter parameter = hematologyQcParameterRepository.findById(request.parameterId())
            .orElseThrow(() -> new ResourceNotFoundException("Parâmetro de hematologia não encontrado"));

        double minAplicado;
        double maxAplicado;
        if ("PERCENTUAL".equalsIgnoreCase(parameter.getModo())) {
            double tolerancia = NumericUtils.defaultIfNull(parameter.getToleranciaPercentual());
            double alvo = NumericUtils.defaultIfNull(parameter.getAlvoValor());
            minAplicado = alvo - (alvo * tolerancia / 100D);
            maxAplicado = alvo + (alvo * tolerancia / 100D);
        } else {
            minAplicado = NumericUtils.defaultIfNull(parameter.getMinValor());
            maxAplicado = NumericUtils.defaultIfNull(parameter.getMaxValor());
        }

        String status = request.valorMedido() >= minAplicado && request.valorMedido() <= maxAplicado
            ? "APROVADO"
            : "REPROVADO";

        HematologyQcMeasurement measurement = HematologyQcMeasurement.builder()
            .parameter(parameter)
            .dataMedicao(request.dataMedicao())
            .analito(request.analito())
            .valorMedido(request.valorMedido())
            .modoUsado(parameter.getModo())
            .minAplicado(minAplicado)
            .maxAplicado(maxAplicado)
            .status(status)
            .observacao(request.observacao())
            .build();
        return toMeasurementResponse(hematologyQcMeasurementRepository.save(measurement));
    }

    @Transactional(readOnly = true)
    public List<HematologyBioRecord> getBioRecords() {
        return hematologyBioRecordRepository.findAllByOrderByDataBioDesc();
    }

    @Transactional
    public HematologyBioRecord createBioRecord(HematologyBioRequest request) {
        HematologyBioRecord record = HematologyBioRecord.builder()
            .dataBio(request.dataBio())
            .dataPad(request.dataPad())
            .registroBio(request.registroBio())
            .registroPad(request.registroPad())
            .modoCi(request.modoCi() == null ? "bio" : request.modoCi())
            .bioHemacias(NumericUtils.defaultIfNull(request.bioHemacias()))
            .bioHematocrito(NumericUtils.defaultIfNull(request.bioHematocrito()))
            .bioHemoglobina(NumericUtils.defaultIfNull(request.bioHemoglobina()))
            .bioLeucocitos(NumericUtils.defaultIfNull(request.bioLeucocitos()))
            .bioPlaquetas(NumericUtils.defaultIfNull(request.bioPlaquetas()))
            .bioRdw(NumericUtils.defaultIfNull(request.bioRdw()))
            .bioVpm(NumericUtils.defaultIfNull(request.bioVpm()))
            .padHemacias(NumericUtils.defaultIfNull(request.padHemacias()))
            .padHematocrito(NumericUtils.defaultIfNull(request.padHematocrito()))
            .padHemoglobina(NumericUtils.defaultIfNull(request.padHemoglobina()))
            .padLeucocitos(NumericUtils.defaultIfNull(request.padLeucocitos()))
            .padPlaquetas(NumericUtils.defaultIfNull(request.padPlaquetas()))
            .padRdw(NumericUtils.defaultIfNull(request.padRdw()))
            .padVpm(NumericUtils.defaultIfNull(request.padVpm()))
            .ciMinHemacias(NumericUtils.defaultIfNull(request.ciMinHemacias()))
            .ciMaxHemacias(NumericUtils.defaultIfNull(request.ciMaxHemacias()))
            .ciMinHematocrito(NumericUtils.defaultIfNull(request.ciMinHematocrito()))
            .ciMaxHematocrito(NumericUtils.defaultIfNull(request.ciMaxHematocrito()))
            .ciMinHemoglobina(NumericUtils.defaultIfNull(request.ciMinHemoglobina()))
            .ciMaxHemoglobina(NumericUtils.defaultIfNull(request.ciMaxHemoglobina()))
            .ciMinLeucocitos(NumericUtils.defaultIfNull(request.ciMinLeucocitos()))
            .ciMaxLeucocitos(NumericUtils.defaultIfNull(request.ciMaxLeucocitos()))
            .ciMinPlaquetas(NumericUtils.defaultIfNull(request.ciMinPlaquetas()))
            .ciMaxPlaquetas(NumericUtils.defaultIfNull(request.ciMaxPlaquetas()))
            .ciMinRdw(NumericUtils.defaultIfNull(request.ciMinRdw()))
            .ciMaxRdw(NumericUtils.defaultIfNull(request.ciMaxRdw()))
            .ciMinVpm(NumericUtils.defaultIfNull(request.ciMinVpm()))
            .ciMaxVpm(NumericUtils.defaultIfNull(request.ciMaxVpm()))
            .ciPctHemacias(NumericUtils.defaultIfNull(request.ciPctHemacias()))
            .ciPctHematocrito(NumericUtils.defaultIfNull(request.ciPctHematocrito()))
            .ciPctHemoglobina(NumericUtils.defaultIfNull(request.ciPctHemoglobina()))
            .ciPctLeucocitos(NumericUtils.defaultIfNull(request.ciPctLeucocitos()))
            .ciPctPlaquetas(NumericUtils.defaultIfNull(request.ciPctPlaquetas()))
            .ciPctRdw(NumericUtils.defaultIfNull(request.ciPctRdw()))
            .ciPctVpm(NumericUtils.defaultIfNull(request.ciPctVpm()))
            .build();
        return hematologyBioRecordRepository.save(record);
    }

    private HematologyParameterResponse toParameterResponse(HematologyQcParameter parameter) {
        return new HematologyParameterResponse(
            parameter.getId(),
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

    private HematologyMeasurementResponse toMeasurementResponse(HematologyQcMeasurement measurement) {
        HematologyQcParameter param = measurement.getParameter();
        return new HematologyMeasurementResponse(
            measurement.getId(),
            param != null ? param.getId() : null,
            param != null ? param.getEquipamento() : null,
            param != null ? param.getLoteControle() : null,
            param != null ? param.getNivelControle() : null,
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
