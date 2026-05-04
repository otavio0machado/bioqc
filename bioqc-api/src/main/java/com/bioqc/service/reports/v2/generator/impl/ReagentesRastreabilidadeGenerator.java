package com.bioqc.service.reports.v2.generator.impl;

import com.bioqc.entity.LabSettings;
import com.bioqc.entity.QcRecord;
import com.bioqc.entity.ReagentLot;
import com.bioqc.entity.StockMovement;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.repository.StockMovementRepository;
import com.bioqc.service.LabSettingsService;
import com.bioqc.service.ReportNumberingService;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.catalog.ReportDefinitionRegistry;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.service.reports.v2.generator.ReportFilters;
import com.bioqc.service.reports.v2.generator.ReportGenerator;
import com.bioqc.service.reports.v2.generator.ReportPreview;
import com.bioqc.service.reports.v2.generator.ai.ReportAiCommentator;
import com.bioqc.service.reports.v2.generator.chart.ChartRenderer;
import com.bioqc.service.reports.v2.generator.pdf.LabHeaderRenderer;
import com.bioqc.service.reports.v2.generator.pdf.PdfFooterRenderer;
import com.bioqc.service.reports.v2.generator.pdf.ReportV2PdfTheme;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rastreabilidade de reagentes — lotes, vencimentos, movimentos.
 *
 * Secoes pos refator-v3:
 * 1. Capa
 * 2. Resumo (total, em estoque, em uso, inativos, vencidos)
 * 3. Vencimentos proximos (30/60/90 dias)
 * 4. Vencidos com estoque (caixa vermelha) — usa (unitsInStock + unitsInUse) &gt; 0
 * 5. Comentario IA
 *
 * <p>Refator v3: card "Fora de estoque" passa a ser "Inativos". Tabelas que filtravam
 * por {@code currentStock > 0} usam soma {@code (unitsInStock + unitsInUse) > 0}. Filtro
 * {@code includeInactive} agora bate em {@code 'inativo'} (era {@code 'fora_de_estoque'}).</p>
 *
 * <p>PDFs reports v2 ja assinados sao imutaveis — apenas geracoes futuras usam labels novos.</p>
 */
@Component
public class ReagentesRastreabilidadeGenerator implements ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ReagentesRastreabilidadeGenerator.class);

    private final ReagentLotRepository lotRepository;
    private final StockMovementRepository movementRepository;
    private final QcRecordRepository qcRecordRepository;
    private final ReportNumberingService reportNumberingService;
    private final ChartRenderer chartRenderer;
    private final LabHeaderRenderer headerRenderer;
    private final LabSettingsService labSettingsService;
    private final ReportAiCommentator aiCommentator;

    public ReagentesRastreabilidadeGenerator(
        ReagentLotRepository lotRepository,
        StockMovementRepository movementRepository,
        QcRecordRepository qcRecordRepository,
        ReportNumberingService reportNumberingService,
        ChartRenderer chartRenderer,
        LabHeaderRenderer headerRenderer,
        LabSettingsService labSettingsService,
        ReportAiCommentator aiCommentator
    ) {
        this.lotRepository = lotRepository;
        this.movementRepository = movementRepository;
        this.qcRecordRepository = qcRecordRepository;
        this.reportNumberingService = reportNumberingService;
        this.chartRenderer = chartRenderer;
        this.headerRenderer = headerRenderer;
        this.labSettingsService = labSettingsService;
        this.aiCommentator = aiCommentator;
    }

    @Override
    public ReportDefinition definition() {
        return ReportDefinitionRegistry.REAGENTES_RASTREABILIDADE_DEFINITION;
    }

    @Override
    @Transactional
    public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        String reportNumber = reportNumberingService.reserveNextNumber();
        byte[] pdfBytes = renderPdf(rf, ctx, reportNumber);
        String sha256 = reportNumberingService.sha256Hex(pdfBytes);
        reportNumberingService.registerGeneration(reportNumber, "reagentes", "PDF", rf.periodLabel,
            pdfBytes, ctx == null ? null : ctx.userId());
        return new ReportArtifact(pdfBytes, "application/pdf", reportNumber + ".pdf", 0,
            pdfBytes.length, reportNumber, sha256, rf.periodLabel);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        List<ReagentLot> all = filteredLots(rf);
        StringBuilder html = new StringBuilder();
        html.append("<section><h1 style=\"color:#14532d\">Rastreabilidade de Reagentes</h1>");
        html.append("<p>Lotes no recorte: <strong>").append(all.size()).append("</strong></p>");
        html.append("</section>");
        return new ReportPreview(html.toString(),
            all.isEmpty() ? List.of("Nenhum lote encontrado.") : List.of(), rf.periodLabel);
    }

    private byte[] renderPdf(Resolved rf, GenerationContext ctx, String reportNumber) {
        LabSettings settings = ctx != null && ctx.labSettings() != null ? ctx.labSettings() : labSettingsService.getOrCreateSingleton();
        String respName = settings == null ? "" : settings.getResponsibleName();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36F, 36F, 40F, 54F);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PdfFooterRenderer(reportNumber, respName));
            doc.open();
            ReportArtifact headerArtifact = new ReportArtifact(
                new byte[] { 0x25, 0x50 }, "application/pdf", reportNumber + ".pdf", 1, 2L,
                reportNumber, "0000000000000000000000000000000000000000000000000000000000000000",
                rf.periodLabel);
            headerRenderer.render(doc, writer, settings, definition(), headerArtifact);

            List<ReagentLot> all = filteredLots(rf);
            LocalDate today = LocalDate.now();
            long emEstoque = all.stream().filter(l -> "em_estoque".equalsIgnoreCase(l.getStatus())).count();
            long emUso = all.stream().filter(l -> "em_uso".equalsIgnoreCase(l.getStatus())).count();
            long inativos = all.stream().filter(l -> "inativo".equalsIgnoreCase(l.getStatus())).count();
            long vencidos = all.stream().filter(l -> "vencido".equalsIgnoreCase(l.getStatus())).count();
            long vencidosComEstoque = all.stream()
                .filter(l -> l.getExpiryDate() != null && l.getExpiryDate().isBefore(today)
                    && totalUnits(l) > 0)
                .count();

            doc.add(ReportV2PdfTheme.section("Resumo"));
            PdfPTable cards = new PdfPTable(new float[] {1, 1, 1, 1, 1});
            cards.setWidthPercentage(100F); cards.setSpacingAfter(6F);
            cards.addCell(summaryCell("Total", String.valueOf(all.size()), ReportV2PdfTheme.BRAND_PRIMARY));
            cards.addCell(summaryCell("Em estoque", String.valueOf(emEstoque), ReportV2PdfTheme.STATUS_APROVADO));
            cards.addCell(summaryCell("Em uso", String.valueOf(emUso), ReportV2PdfTheme.BRAND_PRIMARY));
            cards.addCell(summaryCell("Inativos", String.valueOf(inativos), ReportV2PdfTheme.MUTED));
            cards.addCell(summaryCell("Vencidos", String.valueOf(vencidos), ReportV2PdfTheme.STATUS_REPROVADO));
            doc.add(cards);

            // Vencimentos proximos
            int horizon = rf.expiryHorizonDays > 0 ? rf.expiryHorizonDays : 90;
            List<ReagentLot> expiring = all.stream()
                .filter(l -> l.getExpiryDate() != null
                    && !l.getExpiryDate().isBefore(today)
                    && !l.getExpiryDate().isAfter(today.plusDays(horizon)))
                .sorted(Comparator.comparing(ReagentLot::getExpiryDate))
                .collect(Collectors.toList());
            if (!expiring.isEmpty()) {
                doc.add(ReportV2PdfTheme.section("Vencimentos proximos (" + horizon + " dias)"));
                PdfPTable t = ReportV2PdfTheme.table(new float[] {2.2F, 1.4F, 1.8F, 1.3F, 1.1F, 1.1F, 1.2F});
                ReportV2PdfTheme.headerRow(t, "Nome", "Lote", "Categoria", "Vence", "Em estoque", "Em uso", "Status");
                boolean alt = false;
                for (ReagentLot l : expiring) {
                    ReportV2PdfTheme.bodyRow(t, alt,
                        ReportV2PdfTheme.safe(l.getName()),
                        ReportV2PdfTheme.safe(l.getLotNumber()),
                        ReportV2PdfTheme.safe(l.getCategory()),
                        ReportV2PdfTheme.formatDate(l.getExpiryDate()),
                        String.valueOf(l.getUnitsInStock() == null ? 0 : l.getUnitsInStock()),
                        String.valueOf(l.getUnitsInUse() == null ? 0 : l.getUnitsInUse()),
                        ReportV2PdfTheme.safe(l.getStatus())
                    );
                    alt = !alt;
                }
                doc.add(t);
            }

            // Vencidos com estoque (caixa vermelha)
            if (vencidosComEstoque > 0) {
                doc.add(ReportV2PdfTheme.section("ATENCAO: Vencidos com estoque remanescente"));
                PdfPTable wrap = new PdfPTable(1);
                wrap.setWidthPercentage(100F);
                wrap.addCell(ReportV2PdfTheme.calloutBox(
                    vencidosComEstoque + " lote(s) vencido(s) com estoque - risco regulatorio. "
                    + "Separar e descartar conforme POP de residuos.",
                    ReportV2PdfTheme.ALERT_BG, ReportV2PdfTheme.STATUS_REPROVADO));
                doc.add(wrap);
                PdfPTable t = ReportV2PdfTheme.table(new float[] {2.2F, 1.4F, 1.4F, 1.1F, 1.1F});
                ReportV2PdfTheme.headerRow(t, "Nome", "Lote", "Venceu em", "Em estoque", "Em uso");
                boolean alt = false;
                for (ReagentLot l : all) {
                    if (l.getExpiryDate() == null || !l.getExpiryDate().isBefore(today)) continue;
                    if (totalUnits(l) <= 0) continue;
                    ReportV2PdfTheme.bodyRow(t, alt,
                        ReportV2PdfTheme.safe(l.getName()),
                        ReportV2PdfTheme.safe(l.getLotNumber()),
                        ReportV2PdfTheme.formatDate(l.getExpiryDate()),
                        String.valueOf(l.getUnitsInStock() == null ? 0 : l.getUnitsInStock()),
                        String.valueOf(l.getUnitsInUse() == null ? 0 : l.getUnitsInUse())
                    );
                    alt = !alt;
                }
                doc.add(t);
            }

            // Secao removida em refator-v2 (audit §1.11). Para regenerar consumo, derivar de
            // StockMovement agregando SAIDA por categoria/janela 30/60/90 dias — issue separada.

            // Detalhamento por etiqueta — uma secao completa por lote
            if (rf.detailEachLot && !all.isEmpty()) {
                doc.newPage();
                doc.add(ReportV2PdfTheme.section("Detalhamento por etiqueta"));
                Paragraph intro = new Paragraph(
                    "Cada lote abaixo aparece em sua propria secao com identificacao, validade, "
                    + "estoque, movimentacoes registradas, uso em CQ e status de rastreabilidade.",
                    ReportV2PdfTheme.META_FONT);
                intro.setSpacingAfter(8F);
                doc.add(intro);

                // Ordenar: ativos primeiro (em_estoque, em_uso), depois vencidos com estoque,
                // depois vencidos sem estoque, depois inativos. Dentro de cada bucket, por nome.
                List<ReagentLot> sortedLots = all.stream()
                    .sorted(Comparator
                        .<ReagentLot>comparingInt(l -> sortRank(l, today))
                        .thenComparing(l -> safeLower(l.getName()))
                        .thenComparing(l -> safeLower(l.getLotNumber()))
                    )
                    .collect(Collectors.toList());

                boolean first = true;
                for (ReagentLot lot : sortedLots) {
                    if (!first) doc.newPage();
                    first = false;
                    renderLotDetail(doc, lot, today);
                }
            }

            // Comentario IA
            if (rf.includeAiCommentary) {
                doc.add(ReportV2PdfTheme.section("Analise executiva"));
                String structured = "Total lotes: " + all.size()
                    + "\nEm estoque: " + emEstoque
                    + "\nEm uso: " + emUso
                    + "\nInativos: " + inativos
                    + "\nVencidos: " + vencidos
                    + "\nVencidos com estoque: " + vencidosComEstoque;
                String commentary = aiCommentator.commentary(ReportCode.REAGENTES_RASTREABILIDADE, structured, ctx);
                PdfPTable wrap = new PdfPTable(1);
                wrap.setWidthPercentage(100F);
                PdfPCell cell = new PdfPCell(new Phrase(commentary, ReportV2PdfTheme.AI_FONT));
                cell.setBackgroundColor(ReportV2PdfTheme.BRAND_LIGHT);
                cell.setBorderColor(ReportV2PdfTheme.BRAND_DARK);
                cell.setPadding(10F);
                wrap.addCell(cell);
                doc.add(wrap);
            }

            doc.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new IllegalStateException("Falha ao gerar PDF reagentes", ex);
        }
    }

    private List<ReagentLot> filteredLots(Resolved rf) {
        // Refator-v3: o terminal manual e {@code 'inativo'} (era {@code 'fora_de_estoque'}
        // em v2). O filtro {@code includeInactive} mantem nome historico no contrato do
        // report: quando false, oculta lotes terminais ({@code inativo}) do PDF; quando
        // true, inclui todos.
        List<ReagentLot> all = lotRepository.findAll();
        return all.stream()
            .filter(l -> rf.categories == null || rf.categories.isEmpty()
                || (l.getCategory() != null && rf.categories.contains(l.getCategory())))
            .filter(l -> rf.includeInactive || !"inativo".equalsIgnoreCase(l.getStatus()))
            .collect(Collectors.toList());
    }

    /** Soma {@code unitsInStock + unitsInUse} com tolerancia a null (refator v3). */
    private static int totalUnits(ReagentLot l) {
        int stock = l.getUnitsInStock() == null ? 0 : l.getUnitsInStock();
        int use = l.getUnitsInUse() == null ? 0 : l.getUnitsInUse();
        return stock + use;
    }

    private Resolved resolve(ReportFilters filters) {
        Resolved r = new Resolved();
        r.categories = filters.getStringList("categories").orElse(null);
        r.includeInactive = filters.getBoolean("includeInactive").orElse(false);
        r.expiryHorizonDays = filters.getInteger("expiryHorizonDays").orElse(90);
        r.includeAiCommentary = filters.getBoolean("includeAiCommentary").orElse(false);
        // Default true: usuario pediu detalhamento por etiqueta com TODAS as informacoes.
        r.detailEachLot = filters.getBoolean("detailEachLot").orElse(true);
        r.periodLabel = "Recorte atual";
        return r;
    }

    /**
     * Ordem de relevancia para apresentacao no PDF:
     * 0 = vencidos com estoque (acao urgente — caixa vermelha em cima)
     * 1 = em uso (lote aberto)
     * 2 = em estoque (cadastrado, nao aberto)
     * 3 = vencidos sem estoque (acabou e expirou — historico)
     * 4 = inativos (descartados/arquivados)
     * 5 = qualquer outro estado
     */
    private static int sortRank(ReagentLot l, LocalDate today) {
        boolean expired = l.getExpiryDate() != null && l.getExpiryDate().isBefore(today);
        int total = (l.getUnitsInStock() == null ? 0 : l.getUnitsInStock())
                  + (l.getUnitsInUse() == null ? 0 : l.getUnitsInUse());
        String s = l.getStatus() == null ? "" : l.getStatus().toLowerCase(Locale.ROOT);
        if (expired && total > 0) return 0;
        if ("em_uso".equals(s)) return 1;
        if ("em_estoque".equals(s)) return 2;
        if ("vencido".equals(s) || expired) return 3;
        if ("inativo".equals(s)) return 4;
        return 5;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * Renderiza UMA secao completa por etiqueta (lote). Inclui:
     * - Cabecalho com nome + status + lote + fabricante
     * - Bloco "Identificacao" (categoria, fornecedor, localizacao, temperatura)
     * - Bloco "Validade & Estoque" (cards: validade, dias restantes, em estoque, em uso)
     * - Bloco "Datas chave" (criado, atualizado, recebido, aberto, arquivado)
     * - Bloco "Rastreabilidade" (campos faltantes ou completo)
     * - Tabela "Movimentacoes registradas" cronologica
     * - Tabela "Uso em CQ" (registros que usaram este lotNumber)
     */
    private void renderLotDetail(Document doc, ReagentLot lot, LocalDate today) throws DocumentException {
        // Cabecalho da secao da etiqueta
        Paragraph h = new Paragraph(
            ReportV2PdfTheme.safe(lot.getName()) + "  ",
            com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA_BOLD, 14, ReportV2PdfTheme.BRAND_DARK));
        h.add(new com.lowagie.text.Chunk("[" + ReportV2PdfTheme.safe(lot.getLotNumber()) + "]",
            ReportV2PdfTheme.META_FONT));
        h.setSpacingBefore(4F);
        h.setSpacingAfter(4F);
        doc.add(h);

        Paragraph statusLine = new Paragraph();
        statusLine.add(new com.lowagie.text.Chunk("Status: ", ReportV2PdfTheme.BODY_BOLD_FONT));
        statusLine.add(new com.lowagie.text.Chunk(
            humanStatus(lot.getStatus()),
            com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA_BOLD, 9,
                ReportV2PdfTheme.colorForStatus(lot.getStatus()))));
        statusLine.add(new com.lowagie.text.Chunk("    Fabricante: " + ReportV2PdfTheme.safe(lot.getManufacturer()),
            ReportV2PdfTheme.BODY_FONT));
        statusLine.setSpacingAfter(8F);
        doc.add(statusLine);

        // Identificacao
        doc.add(ReportV2PdfTheme.subsection("Identificacao"));
        PdfPTable id = ReportV2PdfTheme.table(new float[] {1, 2, 1, 2});
        ReportV2PdfTheme.bodyRow(id, false, "Categoria", ReportV2PdfTheme.safe(lot.getCategory()),
            "Fornecedor", ReportV2PdfTheme.safe(lot.getSupplier()));
        ReportV2PdfTheme.bodyRow(id, true, "Localizacao", ReportV2PdfTheme.safe(lot.getLocation()),
            "Temperatura", ReportV2PdfTheme.safe(lot.getStorageTemp()));
        doc.add(id);

        // Validade & Estoque (4 cards)
        doc.add(ReportV2PdfTheme.subsection("Validade & Estoque"));
        long daysLeft = lot.getExpiryDate() == null ? Long.MIN_VALUE
            : java.time.temporal.ChronoUnit.DAYS.between(today, lot.getExpiryDate());
        PdfPTable cards = new PdfPTable(new float[] {1, 1, 1, 1});
        cards.setWidthPercentage(100F);
        cards.setSpacingAfter(6F);
        cards.addCell(summaryCell("Validade", ReportV2PdfTheme.formatDate(lot.getExpiryDate()),
            daysLeft < 0 ? ReportV2PdfTheme.STATUS_REPROVADO : ReportV2PdfTheme.BRAND_PRIMARY));
        cards.addCell(summaryCell("Dias restantes",
            daysLeft == Long.MIN_VALUE ? "n/d" : (daysLeft < 0 ? "VENCIDO ha " + (-daysLeft) + " dias" : daysLeft + " dias"),
            daysLeft < 0 ? ReportV2PdfTheme.STATUS_REPROVADO
                : (daysLeft <= 30 ? ReportV2PdfTheme.STATUS_ALERTA : ReportV2PdfTheme.STATUS_APROVADO)));
        cards.addCell(summaryCell("Em estoque",
            String.valueOf(lot.getUnitsInStock() == null ? 0 : lot.getUnitsInStock()),
            ReportV2PdfTheme.BRAND_PRIMARY));
        cards.addCell(summaryCell("Em uso",
            String.valueOf(lot.getUnitsInUse() == null ? 0 : lot.getUnitsInUse()),
            ReportV2PdfTheme.BRAND_PRIMARY));
        doc.add(cards);

        // Datas chave
        doc.add(ReportV2PdfTheme.subsection("Datas chave"));
        PdfPTable d = ReportV2PdfTheme.table(new float[] {1, 1.5F, 1, 1.5F, 1, 1.5F});
        DateTimeFormatter dt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));
        ReportV2PdfTheme.bodyRow(d, false,
            "Recebido em", ReportV2PdfTheme.formatDate(lot.getReceivedDate()),
            "Aberto em", ReportV2PdfTheme.formatDate(lot.getOpenedDate()),
            "Arquivado em", ReportV2PdfTheme.formatDate(lot.getArchivedAt()));
        ReportV2PdfTheme.bodyRow(d, true,
            "Criado em", lot.getCreatedAt() == null ? "—" : dt.format(lot.getCreatedAt()),
            "Atualizado em", lot.getUpdatedAt() == null ? "—" : dt.format(lot.getUpdatedAt()),
            "Arquivado por", ReportV2PdfTheme.safe(lot.getArchivedBy()));
        doc.add(d);

        // Rastreabilidade
        doc.add(ReportV2PdfTheme.subsection("Rastreabilidade"));
        java.util.List<String> issues = new java.util.ArrayList<>();
        if (isBlank(lot.getManufacturer())) issues.add("fabricante");
        if (isBlank(lot.getLocation())) issues.add("localizacao");
        if (isBlank(lot.getSupplier())) issues.add("fornecedor");
        if (lot.getReceivedDate() == null) issues.add("data de recebimento");
        Paragraph traceability = new Paragraph();
        if (issues.isEmpty()) {
            traceability.add(new com.lowagie.text.Chunk("OK ", com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA_BOLD, 9, ReportV2PdfTheme.STATUS_APROVADO)));
            traceability.add(new com.lowagie.text.Chunk("Todos os campos obrigatorios cadastrados.",
                ReportV2PdfTheme.BODY_FONT));
        } else {
            traceability.add(new com.lowagie.text.Chunk("PENDENTE ", com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA_BOLD, 9, ReportV2PdfTheme.STATUS_ALERTA)));
            traceability.add(new com.lowagie.text.Chunk("Campos faltantes: " + String.join(", ", issues),
                ReportV2PdfTheme.BODY_FONT));
        }
        if (Boolean.TRUE.equals(lot.getNeedsStockReview())) {
            traceability.add(com.lowagie.text.Chunk.NEWLINE);
            traceability.add(new com.lowagie.text.Chunk("Estoque marcado para revisao operacional.",
                com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_OBLIQUE, 9,
                    ReportV2PdfTheme.STATUS_ALERTA)));
        }
        traceability.setSpacingAfter(8F);
        doc.add(traceability);

        // Movimentacoes
        doc.add(ReportV2PdfTheme.subsection("Movimentacoes registradas"));
        java.util.List<StockMovement> movs = movementRepository
            .findByReagentLotIdOrderByCreatedAtDesc(lot.getId());
        if (movs.isEmpty()) {
            Paragraph empty = new Paragraph("Sem movimentacoes registradas para este lote.",
                ReportV2PdfTheme.META_FONT);
            empty.setSpacingAfter(8F);
            doc.add(empty);
        } else {
            PdfPTable m = ReportV2PdfTheme.table(new float[] {1.2F, 1.1F, 1F, 0.8F, 1F, 1.4F, 2F});
            ReportV2PdfTheme.headerRow(m,
                "Quando", "Tipo", "Qtd", "Motivo", "Responsavel", "Estoque (antes)", "Observacoes");
            boolean alt = false;
            for (StockMovement mv : movs) {
                String when = mv.getCreatedAt() == null ? "—" : dt.format(mv.getCreatedAt());
                String prev = "stock=" + (mv.getPreviousUnitsInStock() == null ? "—" : mv.getPreviousUnitsInStock())
                    + " uso=" + (mv.getPreviousUnitsInUse() == null ? "—" : mv.getPreviousUnitsInUse());
                ReportV2PdfTheme.bodyRow(m, alt,
                    when,
                    ReportV2PdfTheme.safe(mv.getType()),
                    mv.getQuantity() == null ? "—" : ReportV2PdfTheme.formatDecimal(mv.getQuantity()),
                    ReportV2PdfTheme.safe(mv.getReason()),
                    ReportV2PdfTheme.safe(mv.getResponsible()),
                    prev,
                    truncate(ReportV2PdfTheme.safe(mv.getNotes()), 80));
                alt = !alt;
            }
            doc.add(m);
        }

        // Uso em CQ
        doc.add(ReportV2PdfTheme.subsection("Uso em CQ"));
        try {
            String lotKey = lot.getLotNumber() == null ? null : lot.getLotNumber().trim().toLowerCase(Locale.ROOT);
            java.util.List<QcRecord> qcUses = lotKey == null || lotKey.isEmpty()
                ? java.util.List.of()
                : qcRecordRepository.findAll().stream()
                    .filter(qc -> qc.getLotNumber() != null
                        && qc.getLotNumber().trim().toLowerCase(Locale.ROOT).equals(lotKey))
                    .sorted(Comparator.comparing(QcRecord::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(50)
                    .collect(Collectors.toList());
            if (qcUses.isEmpty()) {
                Paragraph empty = new Paragraph("Nenhum registro de CQ encontrado com este lote.",
                    ReportV2PdfTheme.META_FONT);
                doc.add(empty);
            } else {
                Paragraph summary = new Paragraph(
                    "Total de registros de CQ que usaram este lote: " + qcUses.size()
                    + "  (mostrando os " + Math.min(50, qcUses.size()) + " mais recentes)",
                    ReportV2PdfTheme.META_FONT);
                summary.setSpacingAfter(4F);
                doc.add(summary);
                PdfPTable q = ReportV2PdfTheme.table(new float[] {1.2F, 1.6F, 1.4F, 0.8F, 1F, 1F, 1.2F});
                ReportV2PdfTheme.headerRow(q, "Data", "Exame", "Area", "Nivel", "Valor", "Z-score", "Status");
                boolean alt = false;
                for (QcRecord qc : qcUses) {
                    ReportV2PdfTheme.bodyRow(q, alt,
                        ReportV2PdfTheme.formatDate(qc.getDate()),
                        ReportV2PdfTheme.safe(qc.getExamName()),
                        ReportV2PdfTheme.safe(qc.getArea()),
                        ReportV2PdfTheme.safe(qc.getLevel()),
                        ReportV2PdfTheme.formatDecimal(qc.getValue()),
                        ReportV2PdfTheme.formatDecimal(qc.getZScore()),
                        ReportV2PdfTheme.safe(qc.getStatus()));
                    alt = !alt;
                }
                doc.add(q);
            }
        } catch (RuntimeException ex) {
            LOG.warn("Falha ao buscar uso em CQ do lote {}", lot.getLotNumber(), ex);
            Paragraph err = new Paragraph(
                "Nao foi possivel carregar uso em CQ deste lote (consulte logs).",
                ReportV2PdfTheme.META_FONT);
            doc.add(err);
        }
    }

    private static String humanStatus(String s) {
        if (s == null) return "—";
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "em_estoque" -> "Em estoque";
            case "em_uso" -> "Em uso";
            case "vencido" -> "Vencido";
            case "inativo" -> "Inativo";
            case "fora_de_estoque" -> "Fora de estoque";
            case "quarentena" -> "Quarentena";
            case "ativo" -> "Ativo";
            default -> s;
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "—";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private PdfPCell summaryCell(String label, String value, java.awt.Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8F);
        cell.setBorderColor(ReportV2PdfTheme.BORDER);
        Paragraph l = new Paragraph(label, ReportV2PdfTheme.META_FONT);
        l.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(l);
        Paragraph v = new Paragraph(value,
            com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 14, color));
        v.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(v);
        return cell;
    }

    static final class Resolved {
        List<String> categories;
        boolean includeInactive;
        int expiryHorizonDays;
        boolean includeAiCommentary;
        boolean detailEachLot;
        String periodLabel;
    }
}
