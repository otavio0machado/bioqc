package com.bioqc.service.reports.v2.generator.impl;

import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper para invocar generators subordinados do pacote regulatorio em
 * transacao isolada. Cada chamada executa em uma {@code REQUIRES_NEW} — se
 * uma secao falhar (ex.: ManutencaoKpi com SQLException), a transacao outer
 * nao entra em {@code current transaction is aborted} e as demais secoes
 * continuam gerando normalmente.
 *
 * <p>Desenho: precisa ser um bean separado para o proxy {@code @Transactional}
 * do Spring funcionar (auto-invocacao nao ativa AOP).
 *
 * <p>Comportamento: captura {@link RuntimeException} dentro da transacao e
 * loga um WARN com o nome da secao. A transacao REQUIRES_NEW rolaria de
 * qualquer forma se a excecao subisse — aqui engolimos a excecao apos
 * registro para que o outer continue com {@link Optional#empty()}.
 */
@Component
public class SubordinateInvocation {

    private static final Logger LOG = LoggerFactory.getLogger(SubordinateInvocation.class);

    /**
     * Executa {@code task} em transacao nova. Se lancar, loga e retorna
     * {@link Optional#empty()}. Nunca propaga excecao.
     *
     * <p>Importante: a transacao REQUIRES_NEW executa independentemente;
     * o rollback fica confinado a essa transacao, preservando a outer.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<T> runIsolated(String sectionName, Supplier<T> task) {
        try {
            T result = task.get();
            return Optional.ofNullable(result);
        } catch (RuntimeException ex) {
            LOG.warn("Subordinate section '{}' failed — will be omitted from package", sectionName, ex);
            // Marcar a transacao como rollback-only para garantir que qualquer
            // escrita parcial nessa tx subordinada seja desfeita. Como estamos
            // em REQUIRES_NEW, isso NAO afeta a outer.
            org.springframework.transaction.interceptor.TransactionAspectSupport
                .currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }
}
