package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.entity.ReportRun;
import com.bioqc.repository.ReportRunRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ReportRunServiceTest {

    @InjectMocks
    private ReportRunService service;

    @Mock
    private ReportRunRepository repository;

    @Test
    @DisplayName("recordSuccess grava status SUCCESS + username")
    void recordSuccessGravaStatusEUsername() {
        Authentication auth = new UsernamePasswordAuthenticationToken("ana", "p");

        service.recordSuccess("QC_PDF", "bioquimica", "mes", 4, 2026,
            "BIO-202604-000001", "hash", 1234L, 42L, auth);

        ArgumentCaptor<ReportRun> captor = ArgumentCaptor.forClass(ReportRun.class);
        verify(repository).save(captor.capture());
        ReportRun saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getUsername()).isEqualTo("ana");
        assertThat(saved.getReportNumber()).isEqualTo("BIO-202604-000001");
        assertThat(saved.getSizeBytes()).isEqualTo(1234L);
    }

    @Test
    @DisplayName("recordFailure trunca errorMessage longa")
    void recordFailureTruncaErrorMessage() {
        String longMessage = "x".repeat(10_000);

        service.recordFailure("QC_PDF", null, null, null, null, 100L, longMessage, null);

        ArgumentCaptor<ReportRun> captor = ArgumentCaptor.forClass(ReportRun.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILURE");
        assertThat(captor.getValue().getErrorMessage()).hasSize(4_000);
    }

    @Test
    @DisplayName("history aplica limite com clamp (min 1, max 200)")
    void historyClampaLimit() {
        when(repository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(List.of());

        service.history(0);    // vira 20
        service.history(500);  // vira 200

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(repository, org.mockito.Mockito.times(2))
            .findAllByOrderByCreatedAtDesc(captor.capture());
        List<PageRequest> calls = captor.getAllValues();
        assertThat(calls.get(0).getPageSize()).isEqualTo(20);
        assertThat(calls.get(1).getPageSize()).isEqualTo(200);
    }
}
