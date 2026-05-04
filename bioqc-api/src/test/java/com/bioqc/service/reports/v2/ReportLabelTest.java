package com.bioqc.service.reports.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.bioqc.util.ReportV2Mapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportLabelTest {

    @Test
    @DisplayName("parse reconhece valores validos")
    void parseKnownValues() {
        assertThat(ReportLabel.parse("oficial_mensal")).contains(ReportLabel.OFICIAL_MENSAL);
        assertThat(ReportLabel.parse("entregue_vigilancia")).contains(ReportLabel.ENTREGUE_VIGILANCIA);
        assertThat(ReportLabel.parse("rascunho")).contains(ReportLabel.RASCUNHO);
    }

    @Test
    @DisplayName("parse e case-insensitive e ignora espacos")
    void parseCaseInsensitive() {
        assertThat(ReportLabel.parse("OFICIAL_MENSAL")).contains(ReportLabel.OFICIAL_MENSAL);
        assertThat(ReportLabel.parse("  rascunho  ")).contains(ReportLabel.RASCUNHO);
    }

    @Test
    @DisplayName("parse retorna empty para valores invalidos")
    void parseInvalid() {
        assertThat(ReportLabel.parse("bogus")).isEmpty();
        assertThat(ReportLabel.parse("")).isEmpty();
        assertThat(ReportLabel.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("allValues retorna snake_case em ordem estavel")
    void allValues() {
        assertThat(ReportLabel.allValues()).containsExactly(
            "oficial_mensal", "entregue_vigilancia", "rascunho", "revisao_interna");
    }

    @Test
    @DisplayName("parseLabels deduplica e ordena CSV")
    void parseLabelsDedupSort() {
        List<String> parsed = ReportV2Mapper.parseLabels(
            "oficial_mensal,rascunho,oficial_mensal, entregue_vigilancia ");
        assertThat(parsed).containsExactly("entregue_vigilancia", "oficial_mensal", "rascunho");
    }

    @Test
    @DisplayName("serializeLabels retorna null para vazio")
    void serializeLabelsNullOnEmpty() {
        assertThat(ReportV2Mapper.serializeLabels(List.of())).isNull();
        assertThat(ReportV2Mapper.serializeLabels(null)).isNull();
    }

    @Test
    @DisplayName("serializeLabels deduplica e ordena em CSV")
    void serializeLabelsDedupSort() {
        String csv = ReportV2Mapper.serializeLabels(List.of("rascunho", "oficial_mensal", "rascunho"));
        assertThat(csv).isEqualTo("oficial_mensal,rascunho");
    }
}
