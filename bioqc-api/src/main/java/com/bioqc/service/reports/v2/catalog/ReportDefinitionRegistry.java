package com.bioqc.service.reports.v2.catalog;

import com.bioqc.service.reports.v2.ReportCodeNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Catalogo estatico dos {@link ReportDefinition}s conhecidos.
 *
 * <p>Alem de resolver definitions por codigo, centraliza a logica de autorizacao
 * por role — a UI usa {@link #forUserRoles(Set)} para montar o menu de
 * relatorios disponiveis e o backend usa {@link #canAccess(ReportCode, Set)}
 * como guarda antes de delegar ao generator.
 *
 * <p>As categorias de reagente aceitas (usadas em filtros
 * {@code STRING_ENUM_MULTI} do relatorio de reagentes) sao hardcoded aqui
 * — consumidas pelo frontend como allowedValues.
 */
@Component
public class ReportDefinitionRegistry {

    /**
     * Categorias de reagente aceitas em filtros STRING_ENUM_MULTI dos relatorios v2.
     *
     * <p>MUST mirror {@code bioqc-web/src/components/proin/reagentes/constants.ts}
     * (CATEGORIES) e {@link com.bioqc.service.ReagentService#ALLOWED_CATEGORIES}.
     * Drift gera filtros de relatorio que nao casam com as categorias persistidas (rows
     * gravadas com "Bioquímica" + acento ficam invisiveis ao filtrar por "Bioquimica" sem
     * acento). Refator-reagentes-v2 G-01 alinhou as 3 fontes ao formato com acentos.
     */
    public static final List<String> REAGENT_CATEGORIES = List.of(
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

    /** Areas do laboratorio (compartilhado entre varias definitions). */
    public static final List<String> AREAS = List.of(
        "bioquimica", "hematologia", "imunologia", "parasitologia",
        "microbiologia", "uroanalise"
    );

    /** Tipos de periodo aceitos. */
    public static final List<String> PERIOD_TYPES = List.of(
        "current-month", "specific-month", "year", "date-range"
    );

    public static final ReportDefinition CQ_OPERATIONAL_V2_DEFINITION = buildCqOperationalV2();
    public static final ReportDefinition WESTGARD_DEEPDIVE_DEFINITION = buildWestgardDeepdive();
    public static final ReportDefinition REAGENTES_RASTREABILIDADE_DEFINITION = buildReagentesRastreabilidade();
    public static final ReportDefinition MANUTENCAO_KPI_DEFINITION = buildManutencaoKpi();
    public static final ReportDefinition CALIBRACAO_PREPOST_DEFINITION = buildCalibracaoPrePost();
    public static final ReportDefinition MULTI_AREA_CONSOLIDADO_DEFINITION = buildMultiAreaConsolidado();
    public static final ReportDefinition REGULATORIO_PACOTE_DEFINITION = buildRegulatorioPacote();

    private final Map<ReportCode, ReportDefinition> definitions;

    public ReportDefinitionRegistry() {
        Map<ReportCode, ReportDefinition> map = new EnumMap<>(ReportCode.class);
        map.put(ReportCode.CQ_OPERATIONAL_V2, CQ_OPERATIONAL_V2_DEFINITION);
        map.put(ReportCode.WESTGARD_DEEPDIVE, WESTGARD_DEEPDIVE_DEFINITION);
        map.put(ReportCode.REAGENTES_RASTREABILIDADE, REAGENTES_RASTREABILIDADE_DEFINITION);
        map.put(ReportCode.MANUTENCAO_KPI, MANUTENCAO_KPI_DEFINITION);
        map.put(ReportCode.CALIBRACAO_PREPOST, CALIBRACAO_PREPOST_DEFINITION);
        map.put(ReportCode.MULTI_AREA_CONSOLIDADO, MULTI_AREA_CONSOLIDADO_DEFINITION);
        map.put(ReportCode.REGULATORIO_PACOTE, REGULATORIO_PACOTE_DEFINITION);
        this.definitions = Collections.unmodifiableMap(map);
    }

    public ReportDefinition resolve(ReportCode code) {
        ReportDefinition def = definitions.get(code);
        if (def == null) {
            throw new ReportCodeNotFoundException("Codigo de relatorio desconhecido: " + code);
        }
        return def;
    }

    public ReportDefinition resolveOrNull(ReportCode code) {
        return definitions.get(code);
    }

    public Collection<ReportDefinition> all() {
        return definitions.values();
    }

    public List<ReportDefinition> forUserRoles(Set<String> roles) {
        Set<String> normalizedRoles = roles == null ? Set.of() : roles;
        return definitions.values().stream()
            .filter(def -> def.roleAccess().stream().anyMatch(normalizedRoles::contains))
            .collect(Collectors.toUnmodifiableList());
    }

    public boolean canAccess(ReportCode code, Set<String> roles) {
        ReportDefinition def = resolveOrNull(code);
        if (def == null || roles == null) return false;
        return def.roleAccess().stream().anyMatch(roles::contains);
    }

    // ---------- Factories ----------

    private static ReportFilterField areaField(boolean required) {
        return new ReportFilterField(
            "area", ReportFilterFieldType.STRING_ENUM, required, AREAS,
            "Area", "Area do laboratorio a filtrar"
        );
    }

    private static ReportFilterField periodTypeField() {
        return new ReportFilterField(
            "periodType", ReportFilterFieldType.STRING_ENUM, true, PERIOD_TYPES,
            "Tipo de periodo", "Define como o intervalo de datas e calculado"
        );
    }

    private static ReportFilterField monthField() {
        return new ReportFilterField(
            "month", ReportFilterFieldType.INTEGER, false, null,
            "Mes", "Obrigatorio quando periodType=specific-month (1-12)"
        );
    }

    private static ReportFilterField yearField() {
        return new ReportFilterField(
            "year", ReportFilterFieldType.INTEGER, false, null,
            "Ano", "Obrigatorio em specific-month e year (2000-2100)"
        );
    }

    private static ReportFilterField dateFromField() {
        return new ReportFilterField(
            "dateFrom", ReportFilterFieldType.DATE, false, null,
            "Data inicial", "Obrigatorio quando periodType=date-range (ISO yyyy-MM-dd)"
        );
    }

    private static ReportFilterField dateToField() {
        return new ReportFilterField(
            "dateTo", ReportFilterFieldType.DATE, false, null,
            "Data final", "Obrigatorio quando periodType=date-range (ISO yyyy-MM-dd)"
        );
    }

    private static ReportFilterField includeAiCommentary() {
        return new ReportFilterField(
            "includeAiCommentary", ReportFilterFieldType.BOOLEAN, false, null,
            "Incluir comentario IA", "Adiciona analise automatica ao laudo"
        );
    }

    private static ReportFilterField includeComparison() {
        return new ReportFilterField(
            "includeComparison", ReportFilterFieldType.BOOLEAN, false, null,
            "Incluir comparativo", "Adiciona comparativo com periodo anterior"
        );
    }

    private static ReportDefinition buildCqOperationalV2() {
        List<ReportFilterField> fields = List.of(
            areaField(true), periodTypeField(), monthField(), yearField(),
            dateFromField(), dateToField(),
            new ReportFilterField(
                "examIds", ReportFilterFieldType.UUID_LIST, false, null,
                "Exames", "Opcional - filtra apenas pelos exames informados"
            ),
            new ReportFilterField(
                "includeDailyHistory", ReportFilterFieldType.BOOLEAN, false, null,
                "Incluir historico diario",
                "Quando ligado (default), adiciona uma secao no fim do PDF com cabecalho "
                + "de cada dia que teve registros + tabela cronologica completa daquele dia + "
                + "sub-resumo (total, aprovados, alertas, reprovados). Tambem entra no "
                + "REGULATORIO_PACOTE."
            ),
            includeAiCommentary(), includeComparison()
        );
        return new ReportDefinition(
            ReportCode.CQ_OPERATIONAL_V2,
            "Relatorio Operacional de CQ",
            "Controle de qualidade com estatisticas, Westgard e registros do periodo",
            "Panorama completo do controle interno da qualidade com L-J, estatisticas e pos-calibracao",
            "clipboard-document-check",
            ReportCategory.CONTROLE_QUALIDADE,
            Set.of(ReportFormat.PDF),
            new ReportFilterSpec(fields),
            Set.of("ADMIN", "VIGILANCIA_SANITARIA", "FUNCIONARIO"),
            false, true, true,
            1825,
            "RDC ANVISA 786/2023 (Controle Interno da Qualidade em laboratorio clinico)"
        );
    }

    private static ReportDefinition buildWestgardDeepdive() {
        List<ReportFilterField> fields = List.of(
            areaField(true), periodTypeField(), monthField(), yearField(),
            dateFromField(), dateToField(),
            new ReportFilterField(
                "rules", ReportFilterFieldType.STRING_ENUM_MULTI, false,
                List.of("1-2s", "1-3s", "2-2s", "R-4s", "4-1s", "10x"),
                "Regras Westgard", "Filtra apenas pelas regras selecionadas"
            ),
            new ReportFilterField(
                "severity", ReportFilterFieldType.STRING_ENUM, false,
                List.of("ADVERTENCIA", "REJEICAO"),
                "Severidade", "ADVERTENCIA (1-2s) ou REJEICAO (demais)"
            ),
            new ReportFilterField(
                "detailEachExam", ReportFilterFieldType.BOOLEAN, false, null,
                "Detalhar cada exame",
                "Quando ligado (default), gera uma secao por exame com violacoes (cards, "
                + "regras, historico cronologico)"
            ),
            includeAiCommentary()
        );
        return new ReportDefinition(
            ReportCode.WESTGARD_DEEPDIVE,
            "Analise Profunda de Westgard",
            "Deep dive em violacoes Westgard com heatmap temporal",
            "Ranking de regras, exames problematicos e padroes temporais",
            "exclamation-triangle",
            ReportCategory.WESTGARD,
            Set.of(ReportFormat.PDF),
            new ReportFilterSpec(fields),
            Set.of("ADMIN", "VIGILANCIA_SANITARIA", "FUNCIONARIO"),
            false, true, true,
            1825,
            "RDC ANVISA 786/2023 (Controle Interno da Qualidade em laboratorio clinico)"
        );
    }

    private static ReportDefinition buildReagentesRastreabilidade() {
        List<ReportFilterField> fields = List.of(
            new ReportFilterField(
                "categories", ReportFilterFieldType.STRING_ENUM_MULTI, false,
                REAGENT_CATEGORIES,
                "Categorias", "Filtra por categorias de reagente"
            ),
            periodTypeField(), monthField(), yearField(),
            dateFromField(), dateToField(),
            new ReportFilterField(
                "includeInactive", ReportFilterFieldType.BOOLEAN, false, null,
                "Incluir inativos", "Inclui lotes historicos no relatorio"
            ),
            new ReportFilterField(
                "expiryHorizonDays", ReportFilterFieldType.INTEGER, false, null,
                "Horizonte de vencimento (dias)", "Default 90 dias para secao de vencimentos proximos"
            ),
            new ReportFilterField(
                "detailEachLot", ReportFilterFieldType.BOOLEAN, false, null,
                "Detalhar cada lote",
                "Quando ligado (default), gera uma secao por etiqueta com todas as informacoes "
                + "(identificacao, validade, estoque, movimentacoes, uso em CQ, rastreabilidade)"
            ),
            includeAiCommentary()
        );
        return new ReportDefinition(
            ReportCode.REAGENTES_RASTREABILIDADE,
            "Rastreabilidade de Reagentes",
            "Rastreabilidade completa de lotes, movimentacoes e vencimentos",
            "Lotes vigentes, vencimentos proximos, consumo e movimentacoes",
            "beaker",
            ReportCategory.REAGENTES,
            Set.of(ReportFormat.PDF),
            new ReportFilterSpec(fields),
            Set.of("ADMIN", "VIGILANCIA_SANITARIA", "FUNCIONARIO"),
            false, true, true,
            1825,
            "RDC ANVISA 222/2018 (Boas Praticas em Servicos de Saude) — rastreabilidade de insumos"
        );
    }

    private static ReportDefinition buildManutencaoKpi() {
        List<ReportFilterField> fields = List.of(
            new ReportFilterField(
                "equipment", ReportFilterFieldType.STRING, false, null,
                "Equipamento", "Nome do equipamento (string livre)"
            ),
            new ReportFilterField(
                "maintenanceType", ReportFilterFieldType.STRING_ENUM, false,
                List.of("Preventiva", "Corretiva", "Calibracao"),
                "Tipo de manutencao", "Filtra pelo tipo"
            ),
            periodTypeField(), monthField(), yearField(),
            dateFromField(), dateToField(),
            new ReportFilterField(
                "detailEachEquipment", ReportFilterFieldType.BOOLEAN, false, null,
                "Detalhar cada equipamento",
                "Quando ligado (default), gera uma secao por equipamento com historico, MTBF, "
                + "tecnicos, proxima manutencao e atrasadas"
            ),
            includeAiCommentary(), includeComparison()
        );
        return new ReportDefinition(
            ReportCode.MANUTENCAO_KPI,
            "KPIs de Manutencao",
            "KPIs de manutencao, MTBF e preventivas/corretivas",
            "MTBF, ratio preventiva/corretiva, atrasadas e proximas",
            "wrench-screwdriver",
            ReportCategory.MANUTENCAO,
            Set.of(ReportFormat.PDF),
            new ReportFilterSpec(fields),
            Set.of("ADMIN", "VIGILANCIA_SANITARIA", "FUNCIONARIO"),
            false, true, true,
            1825,
            "RDC ANVISA 222/2018 (Boas Praticas em Servicos de Saude) — manutencao de equipamentos"
        );
    }

    private static ReportDefinition buildCalibracaoPrePost() {
        List<ReportFilterField> fields = List.of(
            areaField(false),
            new ReportFilterField(
                "equipment", ReportFilterFieldType.STRING, false, null,
                "Equipamento", "Nome do equipamento (string livre)"
            ),
            periodTypeField(), monthField(), yearField(),
            dateFromField(), dateToField(),
            new ReportFilterField(
                "detailEachExam", ReportFilterFieldType.BOOLEAN, false, null,
                "Detalhar cada exame",
                "Quando ligado (default), gera uma secao por exame com todas as calibracoes "
                + "do periodo (cards de eficacia + tabela cronologica completa)"
            ),
            includeAiCommentary()
        );
        return new ReportDefinition(
            ReportCode.CALIBRACAO_PREPOST,
            "Calibracao Pre/Pos",
            "Analise de eficacia das calibracoes (CV antes/depois)",
            "Delta de CV, calibracoes eficazes e improdutivas",
            "adjustments-horizontal",
            ReportCategory.CALIBRACAO,
            Set.of(ReportFormat.PDF),
            new ReportFilterSpec(fields),
            Set.of("ADMIN", "VIGILANCIA_SANITARIA", "FUNCIONARIO"),
            false, true, true,
            1825,
            "RDC ANVISA 786/2023 (rastreabilidade metrologica e calibracoes)"
        );
    }

    private static ReportDefinition buildMultiAreaConsolidado() {
        List<ReportFilterField> fields = List.of(
            new ReportFilterField(
                "areas", ReportFilterFieldType.STRING_ENUM_MULTI, true, AREAS,
                "Areas", "Selecione 2+ areas para consolidar"
            ),
            periodTypeField(), monthField(), yearField(),
            dateFromField(), dateToField(),
            new ReportFilterField(
                "detailEachArea", ReportFilterFieldType.BOOLEAN, false, null,
                "Detalhar cada area",
                "Quando ligado (default), gera uma secao por area com cards, top exames "
                + "problematicos e violacoes Westgard"
            ),
            includeAiCommentary()
        );
        return new ReportDefinition(
            ReportCode.MULTI_AREA_CONSOLIDADO,
            "Relatorio Consolidado do Laboratorio",
            "Visao executiva cross-area do laboratorio",
            "Consolidacao executiva — uma pagina gerencial do mes",
            "rectangle-stack",
            ReportCategory.CONSOLIDADO,
            Set.of(ReportFormat.PDF),
            new ReportFilterSpec(fields),
            Set.of("ADMIN", "VIGILANCIA_SANITARIA"),
            false, true, true,
            1825,
            "ISO 15189:2022 (analise critica pela direcao)"
        );
    }

    private static ReportDefinition buildRegulatorioPacote() {
        List<ReportFilterField> fields = List.of(
            periodTypeField(), monthField(), yearField(),
            new ReportFilterField(
                "areas", ReportFilterFieldType.STRING_ENUM_MULTI, false, AREAS,
                "Areas", "Areas a incluir no pacote (default: todas)"
            )
            // Pacote regulatorio NAO aceita includeAiCommentary — ver FilterValidator (guard).
        );
        return new ReportDefinition(
            ReportCode.REGULATORIO_PACOTE,
            "Pacote Regulatorio",
            "Pacote consolidado para entrega a vigilancia sanitaria",
            "Merge completo de todos os relatorios anteriores com indice e declaracao",
            "document-text",
            ReportCategory.REGULATORIO,
            Set.of(ReportFormat.PDF),
            new ReportFilterSpec(fields),
            Set.of("ADMIN", "VIGILANCIA_SANITARIA"),
            true, false, false,
            // Retencao regulatoria: 10 anos (3650 dias) — decisao orquestracao.
            3650,
            "RDC ANVISA 786/2023 + ISO 15189:2022 — pacote entregavel a vigilancia sanitaria"
        );
    }
}
