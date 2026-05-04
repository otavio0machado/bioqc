package com.bioqc.dto.reports.v2;

import java.util.List;

/**
 * Payload para aplicar/remover rotulos a uma execucao de relatorio V2.
 *
 * <p>Ambas as listas sao opcionais. A mesma execucao pode receber adicoes
 * e remocoes no mesmo request; as remocoes sao aplicadas apos as adicoes.
 *
 * @param add    rotulos a adicionar (valores em lowercase snake_case)
 * @param remove rotulos a remover
 */
public record SetReportLabelsRequest(List<String> add, List<String> remove) {
    public SetReportLabelsRequest {
        if (add == null) add = List.of();
        if (remove == null) remove = List.of();
    }
}
