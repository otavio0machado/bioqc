package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.entity.ImportRun;
import com.bioqc.repository.ImportRunRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ImportRunServiceTest {

    @InjectMocks
    private ImportRunService service;

    @Mock
    private ImportRunRepository repository;

    @Test
    @DisplayName("record: total>0 e failure=0 -> SUCCESS")
    void recordTotalSemFalhas_statusSuccess() {
        when(repository.save(any())).thenAnswer(i -> {
            ImportRun r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        service.record("QC_RECORDS", "PARTIAL", 10, 10, 0, 500L, null,
            new UsernamePasswordAuthenticationToken("ana", "p"));

        ArgumentCaptor<ImportRun> captor = ArgumentCaptor.forClass(ImportRun.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getUsername()).isEqualTo("ana");
    }

    @Test
    @DisplayName("record: sem sucessos -> FAILURE")
    void recordSemSucessos_statusFailure() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.record("QC_RECORDS", "PARTIAL", 3, 0, 3, 50L, "erros", null);

        ArgumentCaptor<ImportRun> captor = ArgumentCaptor.forClass(ImportRun.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("record: sucesso e falha parcial -> PARTIAL")
    void recordParcial_statusPartial() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.record("QC_RECORDS", "PARTIAL", 5, 3, 2, 200L, "falha L2, L4", null);

        ArgumentCaptor<ImportRun> captor = ArgumentCaptor.forClass(ImportRun.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PARTIAL");
        assertThat(captor.getValue().getErrorSummary()).contains("falha L2");
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
