package com.bioqc.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reagent_lot_id", nullable = false)
    @JsonIgnore
    private ReagentLot reagentLot;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Double quantity;

    private String responsible;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Estoque anterior (legado pre-V14, Double). MANTIDO como deprecated read-only
     * para preservar historico de movimentos pre-refator-v3. Movimentos pos-V14
     * setam este campo como NULL e gravam {@link #previousUnitsInStock} +
     * {@link #previousUnitsInUse}.
     */
    @Column(name = "previous_stock")
    private Double previousStock;

    /**
     * Refator v3: snapshot de {@code unitsInStock} antes do movimento. NULL em
     * movimentos pre-V14 (legados). Frontend escolhe qual exibir via flag {@code isLegacy}.
     */
    @Column(name = "previous_units_in_stock")
    private Integer previousUnitsInStock;

    /**
     * Refator v3: snapshot de {@code unitsInUse} antes do movimento. NULL em movimentos
     * pre-V14.
     */
    @Column(name = "previous_units_in_use")
    private Integer previousUnitsInUse;

    /**
     * Motivo do movimento. Obrigatorio em AJUSTE; em FECHAMENTO usa default
     * {@code REVERSAO_ABERTURA} se nao enviado; em CONSUMO de lote {@code vencido}
     * tambem obrigatorio (descarte registrado). Valida contra {@link MovementReason}.
     */
    @Column(length = 32)
    private String reason;

    /**
     * Refator v3.1 (V15): data DECLARADA pelo operador para o evento real do
     * movimento — em contraste com {@link #createdAt}, que carimba o instante
     * do sistema no momento do registro.
     *
     * <p>Coexistencia semantica:</p>
     * <ul>
     *   <li>{@code eventDate}: quando o operador AFIRMA que o evento ocorreu
     *       (abertura/fim de uso). Pode ser passado, igual ou anterior a
     *       {@code createdAt}. Nunca futuro.</li>
     *   <li>{@code createdAt}: quando o sistema PERSISTIU o registro. Carimbado
     *       pelo {@code @CreationTimestamp}. Imutavel.</li>
     * </ul>
     *
     * <p>Comportamento por tipo (validacao no service):</p>
     * <ul>
     *   <li>ABERTURA: se preenchido, sincroniza {@code lot.openedDate} na primeira
     *       abertura (audit {@code REAGENT_OPENED_DATE_DERIVED}). Se ausente,
     *       default = hoje (compatibilidade com v3).</li>
     *   <li>CONSUMO ("Final de Uso"): rastreabilidade ANVISA RDC 302 art. 49 da
     *       data real do uso/descarte. Frontend usa {@code createdAt} como
     *       fallback quando NULL (movimento pre-V15 ou registro sem data).</li>
     *   <li>ENTRADA/FECHAMENTO/AJUSTE: persistido se enviado, sem efeito colateral.</li>
     * </ul>
     *
     * <p>NULL e estado valido — movimentos pre-V15 nao tem essa coluna preenchida.</p>
     */
    @Column(name = "event_date")
    private LocalDate eventDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
