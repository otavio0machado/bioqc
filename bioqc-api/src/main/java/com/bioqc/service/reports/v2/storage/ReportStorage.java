package com.bioqc.service.reports.v2.storage;

import java.io.IOException;
import java.util.Optional;

/**
 * Abstracao de armazenamento para PDFs/artefatos V2. A implementacao padrao e
 * local filesystem; no futuro podemos trocar por S3 sem alterar
 * {@code ReportServiceV2}.
 */
public interface ReportStorage {

    /**
     * Salva {@code bytes} em um caminho derivado de {@code hint} e retorna a
     * {@code storageKey} (sempre com forward slashes).
     */
    String save(byte[] bytes, StorageKeyHint hint) throws IOException;

    /**
     * Carrega os bytes referentes a uma {@code storageKey}. Lanca
     * {@link java.nio.file.NoSuchFileException} se nao encontrar.
     */
    byte[] load(String storageKey) throws IOException;

    /**
     * Substitui atomicamente o conteudo de uma {@code storageKey}. Usado quando
     * o artefato e reassinado.
     */
    void replace(String storageKey, byte[] bytes) throws IOException;

    /**
     * Remove o artefato (se existir).
     */
    void delete(String storageKey) throws IOException;

    /**
     * URL assinada temporaria para acesso direto (S3-like). No backend local
     * retorna {@code Optional.empty()}.
     */
    Optional<String> signedUrl(String storageKey);

    /**
     * Sugestao estruturada para compor uma storageKey legivel e unica.
     */
    record StorageKeyHint(String reportCode, String yearMonth, String reportNumber, String extension) {
        public StorageKeyHint {
            if (reportCode == null || reportCode.isBlank()) {
                throw new IllegalArgumentException("StorageKeyHint.reportCode obrigatorio");
            }
            if (yearMonth == null || yearMonth.isBlank()) {
                throw new IllegalArgumentException("StorageKeyHint.yearMonth obrigatorio");
            }
            if (reportNumber == null || reportNumber.isBlank()) {
                throw new IllegalArgumentException("StorageKeyHint.reportNumber obrigatorio");
            }
            if (extension == null || extension.isBlank()) {
                extension = "pdf";
            }
        }
    }
}
