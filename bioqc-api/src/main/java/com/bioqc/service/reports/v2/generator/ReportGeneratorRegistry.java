package com.bioqc.service.reports.v2.generator;

import com.bioqc.service.reports.v2.ReportCodeNotFoundException;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Registro runtime dos {@link ReportGenerator}s disponiveis. Colhe todos os
 * beans {@code ReportGenerator} do contexto e indexa por {@link ReportCode}.
 * Duas implementacoes para o mesmo codigo falham o startup.
 *
 * <p>Quando o feature flag {@code reports.v2.enabled=false}, o catalogo V2 e
 * irrelevante: o controller V2 nao e registrado. Mesmo assim, o registry
 * pode conviver sem problemas — ate util para testes unitarios.
 */
@Component
public class ReportGeneratorRegistry {

    private final Map<ReportCode, ReportGenerator> generators;

    public ReportGeneratorRegistry(ObjectProvider<List<ReportGenerator>> allProvider) {
        List<ReportGenerator> all = allProvider.getIfAvailable(List::of);
        Map<ReportCode, ReportGenerator> map = new EnumMap<>(ReportCode.class);
        for (ReportGenerator g : all) {
            ReportCode code = g.definition().code();
            ReportGenerator existing = map.get(code);
            if (existing != null && existing != g) {
                throw new IllegalStateException(
                    "Duplicate ReportGenerator for code=" + code
                    + " (" + existing.getClass().getName() + " e " + g.getClass().getName() + ")"
                );
            }
            map.put(code, g);
        }
        this.generators = Map.copyOf(map);
    }

    public ReportGenerator resolve(ReportCode code) {
        ReportGenerator generator = generators.get(code);
        if (generator == null) {
            throw new ReportCodeNotFoundException("Nenhum generator registrado para " + code);
        }
        return generator;
    }

    public boolean hasGenerator(ReportCode code) {
        return generators.containsKey(code);
    }
}
