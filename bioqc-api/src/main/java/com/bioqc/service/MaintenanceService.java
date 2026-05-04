package com.bioqc.service;

import com.bioqc.dto.request.MaintenanceRequest;
import com.bioqc.entity.MaintenanceRecord;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.MaintenanceRecordRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private final MaintenanceRecordRepository maintenanceRecordRepository;

    public MaintenanceService(MaintenanceRecordRepository maintenanceRecordRepository) {
        this.maintenanceRecordRepository = maintenanceRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getRecords(String equipment) {
        if (equipment == null || equipment.isBlank()) {
            return maintenanceRecordRepository.findAllByOrderByDateDesc();
        }
        return maintenanceRecordRepository.findByEquipment(equipment);
    }

    @Transactional
    public MaintenanceRecord createRecord(MaintenanceRequest request) {
        validateNextDate(request);
        return maintenanceRecordRepository.save(MaintenanceRecord.builder()
            .equipment(request.equipment())
            .type(request.type())
            .date(request.date())
            .nextDate(request.nextDate())
            .technician(request.technician())
            .notes(request.notes())
            .build());
    }

    @Transactional
    public MaintenanceRecord updateRecord(UUID id, MaintenanceRequest request) {
        validateNextDate(request);
        MaintenanceRecord record = maintenanceRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Registro de manutenção não encontrado"));
        record.setEquipment(request.equipment());
        record.setType(request.type());
        record.setDate(request.date());
        record.setNextDate(request.nextDate());
        record.setTechnician(request.technician());
        record.setNotes(request.notes());
        return maintenanceRecordRepository.save(record);
    }

    @Transactional
    public void deleteRecord(UUID id) {
        if (!maintenanceRecordRepository.existsById(id)) {
            throw new ResourceNotFoundException("Registro de manutenção não encontrado");
        }
        maintenanceRecordRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getPendingMaintenances() {
        return maintenanceRecordRepository.findPendingMaintenances();
    }

    private void validateNextDate(MaintenanceRequest request) {
        if (request.nextDate() != null && request.date() != null && !request.nextDate().isAfter(request.date())) {
            throw new BusinessException("A próxima data de manutenção deve ser posterior à data do registro");
        }
    }
}
