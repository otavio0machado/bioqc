package com.bioqc.scheduler;

import com.bioqc.entity.ReagentLot;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.service.ReagentService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReagentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReagentExpiryScheduler.class);

    private final ReagentLotRepository reagentLotRepository;
    private final ReagentService reagentService;

    public ReagentExpiryScheduler(
        ReagentLotRepository reagentLotRepository,
        ReagentService reagentService
    ) {
        this.reagentLotRepository = reagentLotRepository;
        this.reagentService = reagentService;
    }

    /**
     * Reclassifica diariamente os lotes vencidos.
     *
     * Regra de derivacao (refator-v3 — ver {@link ReagentService#deriveStatus}):
     *  - expiryDate &lt; hoje (qualquer estoque) → {@code vencido} (terminal de validade)
     *  - unitsInUse &gt; 0 e expiry futura → {@code em_uso}
     *  - unitsInStock &gt; 0 e expiry futura → {@code em_estoque}
     *  - zero/zero → preserva status atual (nao ha terminal automatico em v3)
     *
     * <p>v3: scheduler NAO toca lote {@code inativo} (terminal manual — decisao 1.1).
     * A query {@code findExpiredNeedingReclassification} ja filtra
     * {@code status NOT IN ('vencido','inativo')}; alem disso o service
     * {@link ReagentService#applyDerivedStatusFromScheduler} faz early return em
     * {@code inativo} como defesa em profundidade.</p>
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void markExpiredLots() {
        LocalDate today = LocalDate.now();
        List<ReagentLot> candidates = reagentLotRepository.findExpiredNeedingReclassification(today);

        if (candidates.isEmpty()) {
            log.debug("Nenhum lote expirado encontrado para reclassificar.");
            return;
        }

        List<ReagentLot> updated = new ArrayList<>();
        for (ReagentLot lot : candidates) {
            if (reagentService.applyDerivedStatusFromScheduler(lot, today)) {
                updated.add(lot);
            }
        }

        if (updated.isEmpty()) {
            log.debug("Scheduler: {} candidato(s) vencido(s), 0 mudancas.", candidates.size());
            return;
        }

        reagentLotRepository.saveAll(updated);
        log.info("Auto-vencimento: {} lote(s) reclassificado(s) para 'vencido' entre {} candidato(s).",
            updated.size(), candidates.size());
    }
}
