package com.bioqc.service.reports.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bioqc.service.reports.v2.storage.LocalFilesystemReportStorage;
import com.bioqc.service.reports.v2.storage.ReportStorage;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemReportStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("save escreve bytes e retorna key estruturada")
    void saveWritesAndReturnsKey() throws IOException {
        LocalFilesystemReportStorage storage = new LocalFilesystemReportStorage(tempDir);
        byte[] content = "%PDF-fake".getBytes();
        String key = storage.save(content, new ReportStorage.StorageKeyHint(
            "CQ_OPERATIONAL_V2", "202604", "BIO-202604-000001", "pdf"
        ));

        assertThat(key).isEqualTo("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.pdf");
        assertThat(storage.load(key)).isEqualTo(content);
    }

    @Test
    @DisplayName("replace substitui conteudo atomicamente")
    void replaceOverwrites() throws IOException {
        LocalFilesystemReportStorage storage = new LocalFilesystemReportStorage(tempDir);
        String key = storage.save("v1".getBytes(), new ReportStorage.StorageKeyHint(
            "CQ_OPERATIONAL_V2", "202604", "BIO-202604-000002", "pdf"
        ));
        storage.replace(key, "v2-signed".getBytes());
        assertThat(storage.load(key)).isEqualTo("v2-signed".getBytes());
    }

    @Test
    @DisplayName("delete remove arquivo sem erro quando ausente")
    void deleteIsIdempotent() throws IOException {
        LocalFilesystemReportStorage storage = new LocalFilesystemReportStorage(tempDir);
        String key = storage.save("x".getBytes(), new ReportStorage.StorageKeyHint(
            "CQ_OPERATIONAL_V2", "202604", "BIO-202604-000003", "pdf"
        ));
        storage.delete(key);
        storage.delete(key); // segunda vez deve ser no-op
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    @DisplayName("rejeita storageKey com path traversal (..)")
    void rejectsPathTraversal() {
        LocalFilesystemReportStorage storage = new LocalFilesystemReportStorage(tempDir);
        assertThatThrownBy(() -> storage.load("../etc/passwd"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("signedUrl retorna Optional.empty para local filesystem")
    void signedUrlEmpty() {
        LocalFilesystemReportStorage storage = new LocalFilesystemReportStorage(tempDir);
        assertThat(storage.signedUrl("reports/v2/x/y.pdf")).isEmpty();
    }

    @Test
    @DisplayName("save com bytes nulos falha")
    void saveNullFails() {
        LocalFilesystemReportStorage storage = new LocalFilesystemReportStorage(tempDir);
        assertThatThrownBy(() -> storage.save(null, new ReportStorage.StorageKeyHint(
            "CQ_OPERATIONAL_V2", "202604", "BIO-202604-000099", "pdf"
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
