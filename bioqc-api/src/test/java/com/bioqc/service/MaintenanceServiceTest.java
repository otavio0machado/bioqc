package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.dto.request.MaintenanceRequest;
import com.bioqc.entity.MaintenanceRecord;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.MaintenanceRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock
    private MaintenanceRecordRepository maintenanceRecordRepository;

    @InjectMocks
    private MaintenanceService maintenanceService;

    @Test
    @DisplayName("getRecords sem filtro deve retornar todos ordenados por data desc")
    void getRecords_semFiltro_retornaTodos() {
        var records = List.of(buildRecord("Equip A"), buildRecord("Equip B"));
        when(maintenanceRecordRepository.findAllByOrderByDateDesc()).thenReturn(records);

        var result = maintenanceService.getRecords(null);

        assertThat(result).hasSize(2);
        verify(maintenanceRecordRepository).findAllByOrderByDateDesc();
        verify(maintenanceRecordRepository, never()).findByEquipment(any());
    }

    @Test
    @DisplayName("getRecords com equipment deve filtrar por equipamento")
    void getRecords_comEquipment_filtraPorEquipamento() {
        var records = List.of(buildRecord("Equip A"));
        when(maintenanceRecordRepository.findByEquipment("Equip A")).thenReturn(records);

        var result = maintenanceService.getRecords("Equip A");

        assertThat(result).hasSize(1);
        verify(maintenanceRecordRepository).findByEquipment("Equip A");
    }

    @Test
    @DisplayName("getRecords com equipment vazio deve tratar como sem filtro")
    void getRecords_comEquipmentVazio_tratComoSemFiltro() {
        when(maintenanceRecordRepository.findAllByOrderByDateDesc()).thenReturn(List.of());

        maintenanceService.getRecords("   ");

        verify(maintenanceRecordRepository).findAllByOrderByDateDesc();
        verify(maintenanceRecordRepository, never()).findByEquipment(any());
    }

    @Test
    @DisplayName("createRecord deve salvar e retornar entidade")
    void createRecord_salvaERetornaEntidade() {
        var request = new MaintenanceRequest("Equip A", "Preventiva",
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1), "Tecnico", "Notas");
        var saved = buildRecord("Equip A");
        when(maintenanceRecordRepository.save(any())).thenReturn(saved);

        var result = maintenanceService.createRecord(request);

        assertThat(result.getEquipment()).isEqualTo("Equip A");
        verify(maintenanceRecordRepository).save(any());
    }

    @Test
    @DisplayName("updateRecord com ID existente deve atualizar campos")
    void updateRecord_comIdExistente_atualizaCampos() {
        var id = UUID.randomUUID();
        var existing = buildRecord("Equip A");
        existing.setId(id);
        when(maintenanceRecordRepository.findById(id)).thenReturn(Optional.of(existing));
        when(maintenanceRecordRepository.save(any())).thenReturn(existing);

        var request = new MaintenanceRequest("Equip B", "Corretiva",
            LocalDate.of(2026, 4, 2), LocalDate.of(2026, 8, 1), "Tecnico 2", "Notas 2");

        var result = maintenanceService.updateRecord(id, request);

        assertThat(result.getEquipment()).isEqualTo("Equip B");
        assertThat(result.getType()).isEqualTo("Corretiva");
        verify(maintenanceRecordRepository).save(existing);
    }

    @Test
    @DisplayName("updateRecord com ID inexistente deve lançar ResourceNotFoundException")
    void updateRecord_comIdInexistente_lancaException() {
        var id = UUID.randomUUID();
        when(maintenanceRecordRepository.findById(id)).thenReturn(Optional.empty());

        var request = new MaintenanceRequest("Equip A", "Preventiva",
            LocalDate.of(2026, 4, 1), null, null, null);

        assertThatThrownBy(() -> maintenanceService.updateRecord(id, request))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleteRecord com ID existente deve excluir")
    void deleteRecord_comIdExistente_exclui() {
        var id = UUID.randomUUID();
        when(maintenanceRecordRepository.existsById(id)).thenReturn(true);

        maintenanceService.deleteRecord(id);

        verify(maintenanceRecordRepository).deleteById(id);
    }

    @Test
    @DisplayName("deleteRecord com ID inexistente deve lançar ResourceNotFoundException")
    void deleteRecord_comIdInexistente_lancaException() {
        var id = UUID.randomUUID();
        when(maintenanceRecordRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> maintenanceService.deleteRecord(id))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getPendingMaintenances deve retornar lista do repository")
    void getPendingMaintenances_retornaListaDoRepository() {
        var records = List.of(buildRecord("Equip A"));
        when(maintenanceRecordRepository.findPendingMaintenances()).thenReturn(records);

        var result = maintenanceService.getPendingMaintenances();

        assertThat(result).hasSize(1);
        verify(maintenanceRecordRepository).findPendingMaintenances();
    }

    @Test
    @DisplayName("createRecord com nextDate anterior ou igual a date deve lançar BusinessException")
    void createRecord_comNextDateAnterior_lancaException() {
        var request = new MaintenanceRequest("Equip A", "Preventiva",
            LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 5), null, null);

        assertThatThrownBy(() -> maintenanceService.createRecord(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("posterior");

        verify(maintenanceRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRecord com nextDate nulo não deve lançar exceção")
    void createRecord_comNextDateNulo_naoLancaException() {
        var request = new MaintenanceRequest("Equip A", "Preventiva",
            LocalDate.of(2026, 4, 1), null, null, null);
        when(maintenanceRecordRepository.save(any())).thenReturn(buildRecord("Equip A"));

        var result = maintenanceService.createRecord(request);

        assertThat(result).isNotNull();
    }

    private MaintenanceRecord buildRecord(String equipment) {
        return MaintenanceRecord.builder()
            .id(UUID.randomUUID())
            .equipment(equipment)
            .type("Preventiva")
            .date(LocalDate.of(2026, 4, 1))
            .build();
    }
}
