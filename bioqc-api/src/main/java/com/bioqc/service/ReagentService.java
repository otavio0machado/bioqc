package com.bioqc.service;

import com.bioqc.dto.request.ArchiveReagentLotRequest;
import com.bioqc.dto.request.DeleteReagentLotRequest;
import com.bioqc.dto.request.ReagentLotRequest;
import com.bioqc.dto.request.StockMovementRequest;
import com.bioqc.dto.request.UnarchiveReagentLotRequest;
import com.bioqc.dto.response.ReagentLabelSummary;
import com.bioqc.dto.response.ReagentLotResponse;
import com.bioqc.dto.response.ResponsibleSummary;
import com.bioqc.entity.MovementReason;
import com.bioqc.entity.MovementType;
import com.bioqc.entity.ReagentLot;
import com.bioqc.entity.ReagentStatus;
import com.bioqc.entity.Role;
import com.bioqc.entity.StockMovement;
import com.bioqc.entity.User;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.repository.StockMovementRepository;
import com.bioqc.repository.UserRepository;
import com.bioqc.util.ResponseMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReagentService {

    /** Janela para considerar um lote "ativo em CQ" (rastreabilidade Fase 3). */
    private static final int QC_ACTIVE_WINDOW_DAYS = 30;

    /**
     * Lista canonica de categorias de reagente.
     *
     * <p>MUST mirror {@code bioqc-web/src/components/proin/reagentes/constants.ts}
     * (CATEGORIES). Drift entre as duas listas quebra cadastro com 400. Se mudar aqui,
     * mude la — e tambem em {@link com.bioqc.service.reports.v2.catalog.ReportDefinitionRegistry#REAGENT_CATEGORIES}.
     */
    static final List<String> ALLOWED_CATEGORIES = List.of(
        "Bioquímica",
        "Hematologia",
        "Imunologia",
        "Parasitologia",
        "Microbiologia",
        "Uroanálise",
        "Kit Diagnóstico",
        "Controle CQ",
        "Calibrador",
        "Geral"
    );

    /**
     * Lista canonica de temperaturas de armazenamento.
     */
    static final List<String> ALLOWED_STORAGE_TEMPS = List.of(
        "2-8°C",
        "15-25°C (Ambiente)",
        "-20°C",
        "-80°C"
    );

    /** Roles elegiveis a serem responsavel por movimento/arquivamento (decisao 1.5). */
    public static final Set<Role> RESPONSIBLE_ROLES = Set.of(Role.ADMIN, Role.FUNCIONARIO);

    // Acoes de auditoria — refator v3.
    public static final String AUDIT_ACTION_STATUS_DERIVED = "REAGENT_STATUS_DERIVED";
    public static final String AUDIT_ACTION_MOVEMENT_BLOCKED = "REAGENT_MOVEMENT_BLOCKED";
    public static final String AUDIT_ACTION_LOT_ARCHIVED = "REAGENT_LOT_ARCHIVED";
    public static final String AUDIT_ACTION_LOT_UNARCHIVED = "REAGENT_LOT_UNARCHIVED";
    public static final String AUDIT_ACTION_LOT_DELETED = "REAGENT_LOT_DELETED";
    public static final String AUDIT_ACTION_DELETE_BLOCKED = "REAGENT_DELETE_BLOCKED";

    /**
     * Action distinta para backfill administrativo de {@code openedDate} (audit ressalva 1.7 v2).
     *
     * <p>v3: PRESERVADA. Disparada SOMENTE em {@code updateLot} administrativo
     * (admin marcou {@code em_uso} sem informar a data de abertura). Compatibilidade
     * com audit_log historico v2 — auditor externo continua filtrando por este nome.</p>
     */
    public static final String AUDIT_ACTION_OPENED_DATE_BACKFILLED = "REAGENT_OPENED_DATE_BACKFILLED";

    /**
     * v3: nova action distinta para gravar {@code openedDate=today} quando
     * {@code ABERTURA} dispara em lote com {@code openedDate=null} (primeira abertura).
     *
     * <p>Action separada por orchestrator: ABERTURA (movimento operacional) e
     * semanticamente diferente do backfill administrativo do v2. Os dois nomes coexistem
     * no audit_log para separar trigger natural (movement) de UPDATE administrativo.</p>
     */
    public static final String AUDIT_ACTION_OPENED_DATE_DERIVED = "REAGENT_OPENED_DATE_DERIVED";

    public static final String AUDIT_TRIGGER_CREATE_LOT = "createLot";
    public static final String AUDIT_TRIGGER_UPDATE_LOT = "updateLot";
    public static final String AUDIT_TRIGGER_MOVEMENT = "movement";
    public static final String AUDIT_TRIGGER_SCHEDULER = "scheduler";
    public static final String AUDIT_TRIGGER_ABERTURA = "abertura";
    public static final String AUDIT_TRIGGER_FECHAMENTO = "fechamento";
    public static final String AUDIT_TRIGGER_CONSUMO = "consumo";
    public static final String AUDIT_TRIGGER_ENTRADA = "entrada";
    public static final String AUDIT_TRIGGER_AJUSTE = "ajuste";
    public static final String AUDIT_TRIGGER_UNARCHIVE = "unarchive";

    private final ReagentLotRepository reagentLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final QcRecordRepository qcRecordRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ReagentService(
        ReagentLotRepository reagentLotRepository,
        StockMovementRepository stockMovementRepository,
        QcRecordRepository qcRecordRepository,
        UserRepository userRepository,
        AuditService auditService
    ) {
        this.reagentLotRepository = reagentLotRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.qcRecordRepository = qcRecordRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ReagentLotResponse> getLots(String category, String status) {
        String normalizedCategory = (category == null || category.isBlank()) ? null : category;
        String normalizedStatus = (status == null || status.isBlank()) ? null : ReagentStatus.normalize(status);
        if (normalizedStatus != null && !ReagentStatus.isValid(normalizedStatus)) {
            // Defesa anti-status-legado v2/v3: rejeita explicitamente valores antigos.
            throw new BusinessException(
                "Status legado nao suportado. Use: " + ReagentStatus.humanList());
        }

        List<ReagentLot> lots = reagentLotRepository.findByFilters(normalizedCategory, normalizedStatus);

        Set<String> activeInQc = lotNumbersUsedInQcRecently(lots);
        Set<String> ambiguousLotNumbers = lotNumbersWithCollision(lots);

        return lots.stream()
            .map(lot -> {
                boolean inQc = lot.getLotNumber() != null
                    && activeInQc.contains(lot.getLotNumber().toLowerCase());
                boolean ambiguous = lot.getLotNumber() != null
                    && ambiguousLotNumbers.contains(lot.getLotNumber().toLowerCase());
                return ResponseMapper.toReagentLotResponse(lot, inQc && !ambiguous);
            })
            .toList();
    }

    private Set<String> lotNumbersUsedInQcRecently(List<ReagentLot> lots) {
        Set<String> lotNumbersLower = lots.stream()
            .map(ReagentLot::getLotNumber)
            .filter(ln -> ln != null && !ln.isBlank())
            .map(ln -> ln.trim().toLowerCase())
            .filter(ln -> !ln.isEmpty())
            .collect(Collectors.toCollection(HashSet::new));
        if (lotNumbersLower.isEmpty()) {
            return Collections.emptySet();
        }
        LocalDate since = LocalDate.now().minusDays(QC_ACTIVE_WINDOW_DAYS);
        return new HashSet<>(qcRecordRepository.findActiveLotNumbersSince(lotNumbersLower, since));
    }

    private Set<String> lotNumbersWithCollision(List<ReagentLot> lots) {
        Map<String, Integer> counts = new HashMap<>();
        for (ReagentLot lot : lots) {
            String ln = lot.getLotNumber();
            if (ln == null || ln.isBlank()) continue;
            String key = ln.trim().toLowerCase();
            if (key.isEmpty()) continue;
            counts.merge(key, 1, Integer::sum);
        }
        Set<String> ambiguous = new HashSet<>();
        for (var e : counts.entrySet()) {
            if (e.getValue() > 1) ambiguous.add(e.getKey());
        }
        return ambiguous;
    }

    @Transactional
    public ReagentLot createLot(ReagentLotRequest request) {
        validateLotDates(request);
        validateCategoryAndTemp(request);
        validateStatusForCreateOrUpdate(request.status());
        if (!reagentLotRepository
                .findByLotNumberAndManufacturer(request.lotNumber(), request.manufacturer())
                .isEmpty()) {
            throw new BusinessException("Já existe um lote com este número e fabricante");
        }
        String status = resolveStatus(request.status(), ReagentStatus.EM_ESTOQUE);
        String label = request.label() == null ? null : request.label().trim();
        Integer unitsInStock = request.unitsInStock() == null ? 0 : request.unitsInStock();
        Integer unitsInUse = request.unitsInUse() == null ? 0 : request.unitsInUse();
        ReagentLot lot = ReagentLot.builder()
            .name(label)
            .lotNumber(request.lotNumber())
            .manufacturer(request.manufacturer())
            .category(request.category())
            .expiryDate(request.expiryDate())
            .unitsInStock(unitsInStock)
            .unitsInUse(unitsInUse)
            .storageTemp(request.storageTemp())
            .status(status)
            .location(request.location())
            .supplier(request.supplier())
            .receivedDate(request.receivedDate())
            .openedDate(request.openedDate())
            .needsStockReview(false)
            .build();
        // Forcing rule (heranca v2): expiry < hoje sempre forca vencido.
        LocalDate today = LocalDate.now();
        if (request.expiryDate() != null && request.expiryDate().isBefore(today)) {
            lot.setStatus(ReagentStatus.VENCIDO);
        } else if (ReagentStatus.EM_USO.equals(status) && lot.getOpenedDate() == null) {
            // Cadastro com em_uso sem openedDate forca openedDate=today (heranca v2).
            lot.setOpenedDate(today);
        }
        applyDerivedStatus(lot, today, AUDIT_TRIGGER_CREATE_LOT);
        try {
            return reagentLotRepository.save(lot);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Já existe um lote com este número e fabricante");
        }
    }

    @Transactional
    public ReagentLot updateLot(UUID id, ReagentLotRequest request) {
        ReagentLot lot = reagentLotRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lote de reagente não encontrado"));
        validateLotDates(request);
        validateCategoryAndTemp(request);
        validateStatusForCreateOrUpdate(request.status());
        // Lote inativo nao pode ser editado diretamente (forca uso de unarchive).
        if (ReagentStatus.INATIVO.equals(lot.getStatus())) {
            throw new BusinessException(
                "Lote arquivado nao pode ser editado diretamente — reative com POST /unarchive");
        }
        // Reverifica unicidade (lotNumber, manufacturer).
        List<ReagentLot> conflicts = reagentLotRepository.findByLotNumberAndManufacturer(
            request.lotNumber(), request.manufacturer());
        boolean conflict = conflicts.stream().anyMatch(other -> !other.getId().equals(id));
        if (conflict) {
            throw new BusinessException("Já existe um lote com este número e fabricante");
        }

        String label = request.label() == null ? null : request.label().trim();
        lot.setName(label);
        lot.setLotNumber(request.lotNumber());
        lot.setManufacturer(request.manufacturer());
        lot.setCategory(request.category());
        lot.setExpiryDate(request.expiryDate());
        lot.setUnitsInStock(request.unitsInStock() == null ? 0 : request.unitsInStock());
        lot.setUnitsInUse(request.unitsInUse() == null ? 0 : request.unitsInUse());
        lot.setStorageTemp(request.storageTemp());
        if (request.status() != null && !request.status().isBlank()) {
            lot.setStatus(resolveStatus(request.status(), lot.getStatus()));
        }
        if (request.openedDate() != null) {
            lot.setOpenedDate(request.openedDate());
        }
        lot.setLocation(request.location());
        lot.setSupplier(request.supplier());
        lot.setReceivedDate(request.receivedDate());
        LocalDate today = LocalDate.now();
        if (request.expiryDate() != null && request.expiryDate().isBefore(today)) {
            lot.setStatus(ReagentStatus.VENCIDO);
        } else {
            // Backfill administrativo: emite REAGENT_OPENED_DATE_BACKFILLED se admin pediu em_uso.
            applyOpenedDateOnUseTransition(lot, today, AUDIT_TRIGGER_UPDATE_LOT);
        }
        applyDerivedStatus(lot, today, AUDIT_TRIGGER_UPDATE_LOT);
        try {
            return reagentLotRepository.save(lot);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Já existe um lote com este número e fabricante");
        }
    }

    /**
     * Hard delete v3: cascade de stock_movements via JPA, ADMIN-only via controller,
     * confirmacao por digitacao do {@code lotNumber}, audit
     * {@code REAGENT_LOT_DELETED} com snapshot enumerativo (audit ressalva 1.2 / H).
     */
    @Transactional
    public void deleteLot(UUID id, DeleteReagentLotRequest request) {
        ReagentLot lot = reagentLotRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lote de reagente não encontrado"));

        if (request == null || request.confirmLotNumber() == null) {
            throw new BusinessException("Confirmacao do lote (confirmLotNumber) e obrigatoria");
        }
        String confirmed = request.confirmLotNumber().trim();
        String stored = lot.getLotNumber() == null ? "" : lot.getLotNumber().trim();
        if (!confirmed.equals(stored)) {
            throw new BusinessException("Confirmacao do lote nao confere");
        }

        boolean usedInQc = hasOperationalQcUsage(lot);
        if (usedInQc) {
            // Bloqueio v3 — protege historico ANVISA (audit ressalva 1.2).
            Map<String, Object> details = new HashMap<>();
            details.put("reason", "used_in_qc_recently");
            details.put("lotNumber", lot.getLotNumber());
            auditService.log(AUDIT_ACTION_DELETE_BLOCKED, "ReagentLot", lot.getId(), details);
            throw new BusinessException(
                "Lote utilizado em CQ recente nao pode ser apagado. Use POST /archive em vez disso.");
        }

        // Snapshot enumerativo de movimentos ANTES do delete fisico (audit ressalva 1.2).
        // audit_log.details e tipo `json` (nao `jsonb`) — Map preserva shape.
        List<StockMovement> movements = stockMovementRepository
            .findByReagentLotIdOrderByCreatedAtDesc(id);
        long movementsCount = movements.size();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("lot", buildLotSnapshot(lot));
        snapshot.put("movements", movements.stream()
            .map(this::buildMovementSnapshot)
            .toList());
        snapshot.put("movementsCount", movementsCount);
        snapshot.put("deletedBy", currentUsernameOrNull());
        snapshot.put("deletedAt", Instant.now().toString());

        // Audit ANTES do delete fisico (audit_log nao tem FK em reagent_lots — sobrevive).
        auditService.log(AUDIT_ACTION_LOT_DELETED, "ReagentLot", lot.getId(), snapshot);

        // Cascade JPA ALL + orphanRemoval=true cobre stock_movements (FK SQL nao tem
        // ON DELETE CASCADE, mas JPA garante via ReagentLot.movements).
        reagentLotRepository.deleteById(id);
    }

    private Map<String, Object> buildLotSnapshot(ReagentLot lot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", lot.getId() != null ? lot.getId().toString() : null);
        data.put("label", lot.getName());
        data.put("lotNumber", lot.getLotNumber());
        data.put("manufacturer", lot.getManufacturer());
        data.put("category", lot.getCategory());
        data.put("expiryDate", lot.getExpiryDate() != null ? lot.getExpiryDate().toString() : null);
        data.put("status", lot.getStatus());
        data.put("unitsInStock", lot.getUnitsInStock());
        data.put("unitsInUse", lot.getUnitsInUse());
        data.put("archivedAt", lot.getArchivedAt() != null ? lot.getArchivedAt().toString() : null);
        data.put("archivedBy", lot.getArchivedBy());
        data.put("needsStockReview", Boolean.TRUE.equals(lot.getNeedsStockReview()));
        data.put("location", lot.getLocation());
        data.put("supplier", lot.getSupplier());
        data.put("receivedDate", lot.getReceivedDate() != null ? lot.getReceivedDate().toString() : null);
        data.put("openedDate", lot.getOpenedDate() != null ? lot.getOpenedDate().toString() : null);
        data.put("storageTemp", lot.getStorageTemp());
        return data;
    }

    private Map<String, Object> buildMovementSnapshot(StockMovement m) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", m.getId() != null ? m.getId().toString() : null);
        data.put("type", m.getType());
        data.put("quantity", m.getQuantity());
        data.put("responsible", m.getResponsible());
        data.put("reason", m.getReason());
        data.put("notes", m.getNotes());
        data.put("previousStock", m.getPreviousStock());
        data.put("previousUnitsInStock", m.getPreviousUnitsInStock());
        data.put("previousUnitsInUse", m.getPreviousUnitsInUse());
        data.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        return data;
    }

    private String currentUsernameOrNull() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
            if (auth == null) return null;
            return auth.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Arquiva o lote (status='inativo'). Permissao do controller: ADMIN ou FUNCIONARIO.
     *
     * <p>Validacoes:</p>
     * <ul>
     *   <li>Lote ja {@code inativo} → 400.</li>
     *   <li>{@code archivedAt &gt; today} → 400.</li>
     *   <li>{@code archivedBy} deve existir em users ativos com role elegivel
     *       (filtra por <strong>username</strong>, audit ressalva 1.1).</li>
     * </ul>
     */
    @Transactional
    public ReagentLot archiveLot(UUID id, ArchiveReagentLotRequest request) {
        ReagentLot lot = reagentLotRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lote de reagente não encontrado"));

        if (ReagentStatus.INATIVO.equals(lot.getStatus())) {
            throw new BusinessException(
                "Lote ja arquivado em " + (lot.getArchivedAt() != null ? lot.getArchivedAt() : "data nao registrada"));
        }
        LocalDate today = LocalDate.now();
        if (request.archivedAt().isAfter(today)) {
            throw new BusinessException("archivedAt nao pode ser data futura");
        }
        String username = request.archivedBy() == null ? "" : request.archivedBy().trim();
        if (username.isEmpty()) {
            throw new BusinessException("archivedBy obrigatorio");
        }
        // Auditor 1.1 — match estavel por USERNAME, NAO name.
        if (!userRepository.existsActiveResponsibleByUsername(username, RESPONSIBLE_ROLES)) {
            throw new BusinessException(
                "Responsavel '" + username + "' nao encontrado ou inativo");
        }

        String fromStatus = lot.getStatus();
        Integer unitsInStockAtArchive = lot.getUnitsInStock() == null ? 0 : lot.getUnitsInStock();
        Integer unitsInUseAtArchive = lot.getUnitsInUse() == null ? 0 : lot.getUnitsInUse();

        lot.setStatus(ReagentStatus.INATIVO);
        lot.setArchivedAt(request.archivedAt());
        lot.setArchivedBy(username);
        lot.setNeedsStockReview(false); // ato de arquivar resolve revisao pendente
        ReagentLot saved = reagentLotRepository.save(lot);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("archivedAt", request.archivedAt().toString());
        details.put("archivedBy", username);
        details.put("fromStatus", fromStatus);
        details.put("toStatus", ReagentStatus.INATIVO);
        details.put("unitsInStockAtArchive", unitsInStockAtArchive);
        details.put("unitsInUseAtArchive", unitsInUseAtArchive);
        details.put("expiryDate", lot.getExpiryDate() != null ? lot.getExpiryDate().toString() : null);
        auditService.log(AUDIT_ACTION_LOT_ARCHIVED, "ReagentLot", lot.getId(), details);

        return saved;
    }

    /**
     * Reativa lote inativo. Re-aplica regra ternaria (decisao 1.7). archivedAt/archivedBy
     * PRESERVADOS para historico imutavel.
     */
    @Transactional
    public ReagentLot unarchiveLot(UUID id, UnarchiveReagentLotRequest request) {
        ReagentLot lot = reagentLotRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lote de reagente não encontrado"));

        if (!ReagentStatus.INATIVO.equals(lot.getStatus())) {
            throw new BusinessException("Lote nao esta arquivado");
        }
        String fromStatus = ReagentStatus.INATIVO;
        LocalDate today = LocalDate.now();
        // Re-deriva sem confiar em deriveStatus (que tem early-return em inativo).
        String derived = deriveStatusForUnarchive(lot, today);
        lot.setStatus(derived);
        // archivedAt e archivedBy PRESERVADOS — nao zera (historico imutavel).
        ReagentLot saved = reagentLotRepository.save(lot);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", request != null ? request.reason() : null);
        details.put("fromStatus", fromStatus);
        details.put("toStatus", derived);
        details.put("archivedAtPreserved", lot.getArchivedAt() != null ? lot.getArchivedAt().toString() : null);
        details.put("archivedByPreserved", lot.getArchivedBy());
        details.put("unitsInStock", lot.getUnitsInStock());
        details.put("unitsInUse", lot.getUnitsInUse());
        details.put("expiryDate", lot.getExpiryDate() != null ? lot.getExpiryDate().toString() : null);
        details.put("trigger", AUDIT_TRIGGER_UNARCHIVE);
        auditService.log(AUDIT_ACTION_LOT_UNARCHIVED, "ReagentLot", lot.getId(), details);

        return saved;
    }

    /**
     * Re-derivacao explicita usada por unarchive (decisao 1.7). Aplica regra ternaria
     * sem early-return de inativo:
     * <ol>
     *   <li>expiry &lt; today → vencido</li>
     *   <li>unitsInUse &gt; 0 → em_uso</li>
     *   <li>unitsInStock &gt; 0 → em_estoque</li>
     *   <li>zero/zero → em_estoque (estoque zero deixou de ser terminal automatico)</li>
     * </ol>
     */
    private String deriveStatusForUnarchive(ReagentLot lot, LocalDate today) {
        LocalDate expiry = lot.getExpiryDate();
        if (expiry != null && expiry.isBefore(today)) {
            return ReagentStatus.VENCIDO;
        }
        int inUse = lot.getUnitsInUse() == null ? 0 : lot.getUnitsInUse();
        if (inUse > 0) {
            return ReagentStatus.EM_USO;
        }
        // unitsInStock > 0 OU zero/zero — em_estoque em ambos os casos
        return ReagentStatus.EM_ESTOQUE;
    }

    /**
     * Lista usuarios elegiveis a serem responsavel por movimento/arquivamento.
     * Endpoint expoe via {@code GET /api/users/responsibles}.
     */
    @Transactional(readOnly = true)
    public List<ResponsibleSummary> getResponsibles() {
        return userRepository.findActiveResponsibles(RESPONSIBLE_ROLES).stream()
            .map(this::toResponsibleSummary)
            .toList();
    }

    private ResponsibleSummary toResponsibleSummary(User user) {
        return new ResponsibleSummary(
            user.getId(),
            user.getName(),
            user.getUsername(),
            user.getRole() != null ? user.getRole().name() : null
        );
    }

    private boolean hasOperationalQcUsage(ReagentLot lot) {
        String lotNumber = lot.getLotNumber();
        return lotNumber != null
            && !lotNumber.isBlank()
            && qcRecordRepository.existsByLotNumberOperational(lotNumber);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getMovements(UUID lotId) {
        return stockMovementRepository.findByReagentLotIdOrderByCreatedAtDesc(lotId);
    }

    /**
     * Cria movimento de estoque com regras v3 (5 tipos de escrita).
     * Bloqueios por status seguem matriz do contrato 5.7.
     */
    @Transactional
    public StockMovement createMovement(UUID lotId, StockMovementRequest request) {
        ReagentLot lot = reagentLotRepository.findById(lotId)
            .orElseThrow(() -> new ResourceNotFoundException("Lote de reagente não encontrado"));

        String type = MovementType.normalize(request.type());
        if (MovementType.SAIDA.equals(type)) {
            throw new BusinessException(
                "Tipo SAIDA descontinuado em v3. Use CONSUMO para registrar uso/descarte ou AJUSTE para correcao de inventario.");
        }
        if (!MovementType.isValidWrite(type)) {
            throw new BusinessException(
                "Tipo de movimentação inválido. Valores aceitos: " + MovementType.humanListWrite());
        }

        Integer prevStock = lot.getUnitsInStock() == null ? 0 : lot.getUnitsInStock();
        Integer prevUse = lot.getUnitsInUse() == null ? 0 : lot.getUnitsInUse();
        double quantity = request.quantity() == null ? 0D : request.quantity();
        String currentStatus = lot.getStatus();

        // Quantity unitario obrigatorio em ABERTURA/FECHAMENTO (audit ressalva E).
        if ((MovementType.ABERTURA.equals(type) || MovementType.FECHAMENTO.equals(type))
            && Double.compare(quantity, 1D) != 0) {
            throw new BusinessException(
                "ABERTURA e FECHAMENTO operam unitariamente (quantity=1 implícito). Recebido: " + quantity + ".");
        }

        // v3.1 — eventDate (data declarada pelo operador) nunca pode ser futura.
        // Validacao precoce, antes do branch por tipo, para mensagem unificada.
        LocalDate requestedEventDate = request.eventDate();
        if (requestedEventDate != null && requestedEventDate.isAfter(LocalDate.now())) {
            if (MovementType.ABERTURA.equals(type)) {
                throw new BusinessException("Data de abertura não pode ser futura.");
            }
            if (MovementType.CONSUMO.equals(type)) {
                throw new BusinessException("Data de fim de uso não pode ser futura.");
            }
            throw new BusinessException("Data do evento não pode ser futura.");
        }

        String reason = MovementReason.normalize(request.reason());
        String trigger;

        switch (type) {
            case MovementType.ENTRADA -> {
                if (ReagentStatus.INATIVO.equals(currentStatus)
                    || ReagentStatus.VENCIDO.equals(currentStatus)) {
                    auditMovementBlocked(lot, type, currentStatus);
                    throw new BusinessException(
                        "Lote " + currentStatus + " nao aceita ENTRADA.");
                }
                if (quantity <= 0) {
                    throw new BusinessException("ENTRADA exige quantity > 0.");
                }
                lot.setUnitsInStock(prevStock + (int) quantity);
                trigger = AUDIT_TRIGGER_ENTRADA;
            }
            case MovementType.ABERTURA -> {
                if (ReagentStatus.INATIVO.equals(currentStatus)
                    || ReagentStatus.VENCIDO.equals(currentStatus)) {
                    auditMovementBlocked(lot, type, currentStatus);
                    throw new BusinessException(
                        "Lote " + currentStatus + " nao aceita ABERTURA.");
                }
                if (prevStock < 1) {
                    throw new BusinessException("Sem unidades fechadas para abrir.");
                }
                lot.setUnitsInStock(prevStock - 1);
                lot.setUnitsInUse(prevUse + 1);
                trigger = AUDIT_TRIGGER_ABERTURA;
                // ABERTURA limpa needsStockReview (decisao 1.12).
                lot.setNeedsStockReview(false);
            }
            case MovementType.FECHAMENTO -> {
                if (ReagentStatus.INATIVO.equals(currentStatus)
                    || ReagentStatus.VENCIDO.equals(currentStatus)) {
                    auditMovementBlocked(lot, type, currentStatus);
                    throw new BusinessException(
                        "Lote " + currentStatus + " nao aceita FECHAMENTO.");
                }
                if (prevUse < 1) {
                    throw new BusinessException("Sem unidades em uso para retornar ao estoque.");
                }
                lot.setUnitsInUse(prevUse - 1);
                lot.setUnitsInStock(prevStock + 1);
                if (reason == null || reason.isBlank()) {
                    reason = MovementReason.REVERSAO_ABERTURA;
                }
                trigger = AUDIT_TRIGGER_FECHAMENTO;
            }
            case MovementType.CONSUMO -> {
                if (ReagentStatus.INATIVO.equals(currentStatus)) {
                    auditMovementBlocked(lot, type, currentStatus);
                    throw new BusinessException("Lote inativo nao aceita CONSUMO.");
                }
                if (quantity <= 0) {
                    throw new BusinessException("CONSUMO exige quantity > 0.");
                }
                if (prevUse < (int) quantity) {
                    throw new BusinessException("Estoque em uso insuficiente para CONSUMO.");
                }
                // CONSUMO em vencido exige reason (descarte registrado — audit recomendacao F).
                if (ReagentStatus.VENCIDO.equals(currentStatus)
                    && (reason == null || reason.isBlank())) {
                    throw new BusinessException(
                        "CONSUMO em lote vencido exige reason (descarte). Valores aceitos: VENCIMENTO, OUTRO.");
                }
                lot.setUnitsInUse(prevUse - (int) quantity);
                trigger = AUDIT_TRIGGER_CONSUMO;
            }
            case MovementType.AJUSTE -> {
                // AJUSTE permitido em todos os status, INCLUSIVE inativo.
                if (reason == null || reason.isBlank()) {
                    throw new BusinessException(
                        "AJUSTE exige reason. Valores aceitos: " + MovementReason.humanList());
                }
                if (request.targetUnitsInStock() == null || request.targetUnitsInUse() == null) {
                    throw new BusinessException(
                        "AJUSTE exige targetUnitsInStock e targetUnitsInUse.");
                }
                if (request.targetUnitsInStock() < 0 || request.targetUnitsInUse() < 0) {
                    throw new BusinessException("AJUSTE exige unidades >= 0.");
                }
                lot.setUnitsInStock(request.targetUnitsInStock());
                lot.setUnitsInUse(request.targetUnitsInUse());
                lot.setNeedsStockReview(false); // AJUSTE limpa flag (revisao explicita)
                trigger = AUDIT_TRIGGER_AJUSTE;
                // forca quantity=0 para historico legivel (audit ressalva contrato 2.4).
                quantity = 0D;
            }
            default -> throw new BusinessException("Tipo de movimentação inválido");
        }

        if (reason != null && !reason.isBlank() && !MovementReason.isValid(reason)) {
            throw new BusinessException(
                "Motivo de movimentação inválido. Valores aceitos: " + MovementReason.humanList());
        }

        LocalDate today = LocalDate.now();

        // v3.1 — Resolucao da data efetiva do movimento. A primeira abertura
        // de um lote sincroniza lot.openedDate; aberturas subsequentes (lote ja
        // tem openedDate) NAO sobrescrevem (preserva primeira abertura), mas
        // ainda assim gravam a data declarada em movement.eventDate.
        LocalDate effectiveEventDate;
        if (MovementType.ABERTURA.equals(type)) {
            // Em ABERTURA, sempre temos uma data — operador informou ou default = hoje.
            effectiveEventDate = requestedEventDate != null ? requestedEventDate : today;
        } else {
            // Outros tipos: respeita o que veio (NULL aceitavel).
            effectiveEventDate = requestedEventDate;
        }

        // ABERTURA: grava openedDate=effectiveEventDate se null + audit
        // REAGENT_OPENED_DATE_DERIVED (v3 / v3.1). Aberturas subsequentes em
        // lote que ja tem openedDate NAO sobrescrevem — primeira abertura
        // permanece imutavel (auditoria).
        if (MovementType.ABERTURA.equals(type) && lot.getOpenedDate() == null) {
            lot.setOpenedDate(effectiveEventDate);
            Map<String, Object> auditDetails = new LinkedHashMap<>();
            auditDetails.put("openedDate", effectiveEventDate.toString());
            auditDetails.put("trigger", AUDIT_TRIGGER_ABERTURA);
            auditDetails.put("fromStatus", currentStatus);
            auditDetails.put("toStatus", ReagentStatus.EM_USO);
            auditService.log(
                AUDIT_ACTION_OPENED_DATE_DERIVED,
                "ReagentLot",
                lot.getId(),
                auditDetails);
        }

        // Re-deriva status (apenas se nao for inativo — preserva terminal manual).
        if (!ReagentStatus.INATIVO.equals(lot.getStatus())) {
            applyDerivedStatus(lot, today, trigger);
        }

        reagentLotRepository.save(lot);

        // Quantity efetiva no historico: 1 para ABERTURA/FECHAMENTO, 0 para AJUSTE,
        // valor enviado para ENTRADA/CONSUMO.
        double historicalQuantity;
        if (MovementType.ABERTURA.equals(type) || MovementType.FECHAMENTO.equals(type)) {
            historicalQuantity = 1D;
        } else if (MovementType.AJUSTE.equals(type)) {
            historicalQuantity = 0D;
        } else {
            historicalQuantity = quantity;
        }

        StockMovement movement = StockMovement.builder()
            .reagentLot(lot)
            .type(type)
            .quantity(historicalQuantity)
            .responsible(request.responsible())
            .notes(request.notes())
            .reason(reason)
            .previousStock(null) // pos-V14 sempre NULL (campo legado read-only)
            .previousUnitsInStock(prevStock)
            .previousUnitsInUse(prevUse)
            // v3.1 — eventDate declarado pelo operador. ABERTURA sempre preenche
            // (default = today se ausente). Outros tipos: passa o que veio (NULL ok).
            .eventDate(effectiveEventDate)
            .build();
        return stockMovementRepository.save(movement);
    }

    private void auditMovementBlocked(ReagentLot lot, String movementType, String status) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", ReagentStatus.INATIVO.equals(status) ? "lote_inativo" : "lote_vencido");
        details.put("movementType", movementType);
        auditService.log(AUDIT_ACTION_MOVEMENT_BLOCKED, "ReagentLot", lot.getId(), details);
    }

    /**
     * Reverte movimento. Cobre 6 tipos (5 escrita + SAIDA legado).
     */
    @Transactional
    public void deleteMovement(UUID movementId) {
        StockMovement movement = stockMovementRepository.findById(movementId)
            .orElseThrow(() -> new ResourceNotFoundException("Movimentação não encontrada"));
        ReagentLot lot = movement.getReagentLot();
        Integer currentStock = lot.getUnitsInStock() == null ? 0 : lot.getUnitsInStock();
        Integer currentUse = lot.getUnitsInUse() == null ? 0 : lot.getUnitsInUse();

        switch (movement.getType()) {
            case MovementType.ENTRADA -> {
                int delta = movement.getQuantity() == null ? 0 : movement.getQuantity().intValue();
                int resulting = currentStock - delta;
                if (resulting < 0) {
                    throw new BusinessException(
                        "Não é possível excluir esta entrada. O estoque resultante ficaria negativo.");
                }
                lot.setUnitsInStock(resulting);
            }
            case MovementType.SAIDA -> {
                // Legado: SAIDA restaurada como if (was a CONSUMO) — adiciona ao stock fechado.
                int delta = movement.getQuantity() == null ? 0 : movement.getQuantity().intValue();
                lot.setUnitsInStock(currentStock + delta);
            }
            case MovementType.ABERTURA -> {
                // Reverter ABERTURA: subtrai 1 de unitsInUse, adiciona 1 a unitsInStock.
                if (currentUse < 1) {
                    throw new BusinessException(
                        "Não é possível reverter esta abertura. Sem unidades em uso para retornar.");
                }
                lot.setUnitsInUse(currentUse - 1);
                lot.setUnitsInStock(currentStock + 1);
            }
            case MovementType.FECHAMENTO -> {
                // Reverter FECHAMENTO: subtrai 1 de unitsInStock, adiciona 1 a unitsInUse.
                if (currentStock < 1) {
                    throw new BusinessException(
                        "Não é possível reverter este fechamento. Sem unidades fechadas para abrir.");
                }
                lot.setUnitsInStock(currentStock - 1);
                lot.setUnitsInUse(currentUse + 1);
            }
            case MovementType.CONSUMO -> {
                // Reverter CONSUMO: adiciona quantity de volta a unitsInUse.
                int delta = movement.getQuantity() == null ? 0 : movement.getQuantity().intValue();
                lot.setUnitsInUse(currentUse + delta);
            }
            case MovementType.AJUSTE -> {
                // Reverter AJUSTE: aplica previousUnitsInStock/Use (pos-V14) ou previousStock (legado).
                if (movement.getPreviousUnitsInStock() != null && movement.getPreviousUnitsInUse() != null) {
                    lot.setUnitsInStock(movement.getPreviousUnitsInStock());
                    lot.setUnitsInUse(movement.getPreviousUnitsInUse());
                } else if (movement.getPreviousStock() != null) {
                    // Movimento AJUSTE pre-V14: restaura previousStock como unitsInStock,
                    // unitsInUse=0 (heranca v2 sem distincao fechado/aberto).
                    int prev = movement.getPreviousStock().intValue();
                    if (prev < 0) {
                        throw new BusinessException(
                            "Não é possível excluir este ajuste. O estoque resultante ficaria negativo.");
                    }
                    lot.setUnitsInStock(prev);
                    lot.setUnitsInUse(0);
                } else {
                    throw new BusinessException(
                        "Não é possível reverter este ajuste. Snapshot anterior ausente.");
                }
            }
            default -> throw new BusinessException("Tipo de movimentação inválido");
        }

        reagentLotRepository.save(lot);
        stockMovementRepository.delete(movement);
    }

    @Transactional(readOnly = true)
    public List<ReagentLot> getByLotNumber(String lotNumber) {
        return reagentLotRepository.findByLotNumberIgnoreCase(lotNumber);
    }

    @Transactional(readOnly = true)
    public List<ReagentLot> getExpiringLots(int days) {
        LocalDate today = LocalDate.now();
        return reagentLotRepository.findExpiringLots(today, today.plusDays(days));
    }

    @Transactional(readOnly = true)
    public List<ReagentLabelSummary> getLabelSummaries() {
        return reagentLotRepository.findLabelSummaries().stream()
            .map(p -> new ReagentLabelSummary(
                p.getLabel(),
                p.getTotal(),
                p.getEmEstoque(),
                p.getEmUso(),
                p.getInativos(),
                p.getVencidos()
            ))
            .toList();
    }

    /**
     * Cross-field: receivedDate <= openedDate <= expiryDate (quando ambas presentes).
     */
    private void validateLotDates(ReagentLotRequest request) {
        if (request.expiryDate() != null && request.openedDate() != null
            && request.openedDate().isAfter(request.expiryDate())) {
            throw new BusinessException(
                "A data de abertura não pode ser posterior à data de validade.");
        }
        if (request.openedDate() != null && request.receivedDate() != null
            && request.receivedDate().isAfter(request.openedDate())) {
            throw new BusinessException(
                "A data de recebimento não pode ser posterior à data de abertura.");
        }
        if (request.expiryDate() != null && request.receivedDate() != null
            && request.receivedDate().isAfter(request.expiryDate())) {
            throw new BusinessException(
                "A data de recebimento não pode ser posterior à data de validade.");
        }
    }

    private void validateCategoryAndTemp(ReagentLotRequest request) {
        if (request.category() != null && !request.category().isBlank()
            && !ALLOWED_CATEGORIES.contains(request.category().trim())) {
            throw new BusinessException(
                "Categoria invalida. Valores aceitos: " + String.join(", ", ALLOWED_CATEGORIES));
        }
        if (request.storageTemp() != null && !request.storageTemp().isBlank()
            && !ALLOWED_STORAGE_TEMPS.contains(request.storageTemp().trim())) {
            throw new BusinessException(
                "Temperatura de armazenamento invalida. Valores aceitos: "
                    + String.join(", ", ALLOWED_STORAGE_TEMPS));
        }
    }

    /**
     * Recusa {@code status='inativo'} em CREATE/UPDATE — forca uso de
     * {@code POST /archive} (decisao 1.4 do contrato).
     */
    private void validateStatusForCreateOrUpdate(String requestStatus) {
        if (requestStatus == null) return;
        String normalized = ReagentStatus.normalize(requestStatus);
        if (ReagentStatus.INATIVO.equals(normalized)) {
            throw new BusinessException(
                "Status 'inativo' nao pode ser definido em CREATE/UPDATE — use POST /archive.");
        }
    }

    /**
     * Regra ternaria canonica do refator-v3 (contrato 5.1):
     *
     * <ol>
     *   <li>{@code inativo} é terminal manual — nao auto-deriva (preserva).</li>
     *   <li>{@code expiryDate < today} (qualquer estoque/abertura) — {@code vencido}</li>
     *   <li>{@code unitsInUse &gt; 0} — {@code em_uso}</li>
     *   <li>{@code unitsInStock &gt; 0 AND unitsInUse == 0} — {@code em_estoque}</li>
     *   <li>{@code zero/zero} — mantem status anterior (NAO virou terminal automatico)</li>
     * </ol>
     */
    public String deriveStatus(ReagentLot lot, LocalDate today) {
        if (lot == null) {
            return null;
        }
        // 1) Inativo é terminal manual.
        if (ReagentStatus.INATIVO.equals(lot.getStatus())) {
            return ReagentStatus.INATIVO;
        }
        LocalDate expiry = lot.getExpiryDate();
        if (expiry == null) {
            return lot.getStatus();
        }
        // 2) Validade — regra mais forte que estoque.
        if (expiry.isBefore(today)) {
            return ReagentStatus.VENCIDO;
        }
        int inUse = lot.getUnitsInUse() == null ? 0 : lot.getUnitsInUse();
        int inStock = lot.getUnitsInStock() == null ? 0 : lot.getUnitsInStock();
        // 3) Em uso tem precedencia.
        if (inUse > 0) {
            return ReagentStatus.EM_USO;
        }
        // 4) Tem estoque fechado.
        if (inStock > 0) {
            return ReagentStatus.EM_ESTOQUE;
        }
        // 5) zero/zero — mantem (nao terminal automatico).
        return lot.getStatus();
    }

    /**
     * Aplica {@link #deriveStatus} mutando o lote quando o derivado difere do atual.
     * Lote {@code inativo} nao e tocado (early return).
     */
    private void applyDerivedStatus(ReagentLot lot, LocalDate today, String trigger) {
        if (lot == null) return;
        if (ReagentStatus.INATIVO.equals(lot.getStatus())) return; // terminal manual
        String oldStatus = lot.getStatus();
        String derived = deriveStatus(lot, today);
        if (Objects.equals(oldStatus, derived)) {
            return;
        }
        lot.setStatus(derived);
        // Para CREATE e movement/scheduler, set openedDate=today se virou em_uso e null.
        if (ReagentStatus.EM_USO.equals(derived)
            && lot.getOpenedDate() == null
            && !AUDIT_TRIGGER_UPDATE_LOT.equals(trigger)
            && !AUDIT_TRIGGER_ABERTURA.equals(trigger)) {
            // ABERTURA ja gravou opened + audit DERIVED separado.
            lot.setOpenedDate(today);
        }
        recordStatusTransition(lot, oldStatus, derived, trigger);
    }

    /**
     * Backfill administrativo (audit ressalva 1.7 v2 — PRESERVADO em v3).
     * Dispara em UPDATE quando o status final SERA {@code em_uso} e {@code openedDate}
     * esta nulo.
     */
    private void applyOpenedDateOnUseTransition(ReagentLot lot, LocalDate today, String trigger) {
        if (lot == null) return;
        if (lot.getOpenedDate() != null) return;
        String fromStatus = lot.getStatus();
        LocalDate expiry = lot.getExpiryDate();
        if (expiry == null || expiry.isBefore(today)) {
            return;
        }
        int inStock = lot.getUnitsInStock() == null ? 0 : lot.getUnitsInStock();
        int inUse = lot.getUnitsInUse() == null ? 0 : lot.getUnitsInUse();
        // Se total = 0 nao marca abertura. Mantem v2.
        if (inStock + inUse <= 0) {
            return;
        }
        if (!ReagentStatus.EM_USO.equals(lot.getStatus())) {
            return;
        }
        lot.setOpenedDate(today);
        if (AUDIT_TRIGGER_UPDATE_LOT.equals(trigger)) {
            // Action distinta para UPDATE administrativo (audit 1.7 v2 preservado).
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("openedDate", today.toString());
            details.put("fromStatus", fromStatus);
            details.put("toStatus", ReagentStatus.EM_USO);
            details.put("trigger", AUDIT_TRIGGER_UPDATE_LOT);
            auditService.log(
                AUDIT_ACTION_OPENED_DATE_BACKFILLED,
                "ReagentLot",
                lot.getId(),
                details
            );
        }
    }

    /**
     * Aplicacao invocada pelo scheduler. Lote {@code inativo} NAO e tocado.
     */
    public boolean applyDerivedStatusFromScheduler(ReagentLot lot, LocalDate today) {
        if (lot == null) return false;
        if (ReagentStatus.INATIVO.equals(lot.getStatus())) return false; // terminal manual
        String oldStatus = lot.getStatus();
        String derived = deriveStatus(lot, today);
        if (Objects.equals(oldStatus, derived)) {
            return false;
        }
        lot.setStatus(derived);
        recordStatusTransition(lot, oldStatus, derived, AUDIT_TRIGGER_SCHEDULER);
        return true;
    }

    private void recordStatusTransition(ReagentLot lot, String from, String to, String trigger) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from);
        details.put("to", to);
        details.put("trigger", trigger);
        details.put("expiryDate", lot.getExpiryDate() == null ? null : lot.getExpiryDate().toString());
        details.put("unitsInStock", lot.getUnitsInStock());
        details.put("unitsInUse", lot.getUnitsInUse());
        auditService.log(
            AUDIT_ACTION_STATUS_DERIVED,
            "ReagentLot",
            lot.getId(),
            details
        );
    }

    private String resolveStatus(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = ReagentStatus.normalize(raw);
        if (!ReagentStatus.isValid(normalized)) {
            throw new BusinessException(
                "Status de lote inválido. Valores aceitos: " + ReagentStatus.humanList());
        }
        return normalized;
    }
}
