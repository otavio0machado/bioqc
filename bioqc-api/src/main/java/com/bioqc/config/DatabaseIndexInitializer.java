package com.bioqc.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseIndexInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void createPartialIndexes() {
        try {
            jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_reagent_lot_manufacturer "
                    + "ON reagent_lots (lot_number, COALESCE(manufacturer, ''))"
            );
            log.info("Partial index idx_reagent_lot_manufacturer verificado/criado com sucesso.");
        } catch (Exception e) {
            log.warn("Falha ao criar partial index idx_reagent_lot_manufacturer: {}", e.getMessage());
        }
    }
}
