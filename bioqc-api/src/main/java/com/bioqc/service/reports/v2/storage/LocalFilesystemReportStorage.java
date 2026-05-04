package com.bioqc.service.reports.v2.storage;

import com.bioqc.config.ReportsV2Properties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementacao local do {@link ReportStorage}. Armazena arquivos em disco
 * dentro de {@code reports.v2.storage.dir}, protegendo contra path traversal
 * via verificacao do {@code toRealPath()}.
 *
 * <p>Grava atomicamente: escreve em arquivo temporario adjacente e executa
 * {@code Files.move(ATOMIC_MOVE)} — evita readers verem bytes parciais.
 *
 * <p>Registrado apenas quando {@code reports.v2.enabled=true} (evita exigir
 * {@code storage.dir} em startup sem feature flag).
 */
@Component
@ConditionalOnProperty(prefix = "reports.v2", name = "enabled", havingValue = "true")
public class LocalFilesystemReportStorage implements ReportStorage {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFilesystemReportStorage.class);
    private static final String FALLBACK_SUBDIR = "bioqc-reports-v2";

    private final Path baseDir;

    @Autowired
    public LocalFilesystemReportStorage(ReportsV2Properties properties) {
        String dir = properties.getStorage() != null ? properties.getStorage().getDir() : null;
        if (dir == null || dir.isBlank()) {
            // Nao lancamos — em prod o ambiente pode ter ligado a flag antes
            // de configurar REPORTS_V2_STORAGE_DIR e preferimos um fallback
            // ephemeral a derrubar o container em crashloop. Storage em tmp e
            // aceitavel para piloto; Supabase Storage entra numa proxima fase.
            Path fallback = Paths.get(System.getProperty("java.io.tmpdir"), FALLBACK_SUBDIR).toAbsolutePath();
            LOG.warn(
                "reports.v2.storage.dir nao configurado. Usando fallback ephemeral em {}. "
                + "Configure REPORTS_V2_STORAGE_DIR para persistencia confiavel.",
                fallback);
            this.baseDir = fallback;
        } else {
            this.baseDir = Paths.get(dir).toAbsolutePath();
        }
    }

    /**
     * Construtor de teste — recebe diretamente o {@code baseDir} em vez do
     * properties inteiro. Mantido publico para facilitar uso a partir de
     * outros pacotes de teste.
     */
    public LocalFilesystemReportStorage(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath();
    }

    @Override
    public String save(byte[] bytes, StorageKeyHint hint) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes nao pode ser vazio");
        }
        String key = buildKey(hint);
        Path target = resolveSafely(key);
        Files.createDirectories(target.getParent());
        writeAtomically(target, bytes);
        return key;
    }

    @Override
    public byte[] load(String storageKey) throws IOException {
        Path target = resolveSafely(storageKey);
        if (!Files.exists(target)) {
            throw new NoSuchFileException(storageKey);
        }
        return Files.readAllBytes(target);
    }

    @Override
    public void replace(String storageKey, byte[] bytes) throws IOException {
        Path target = resolveSafely(storageKey);
        Files.createDirectories(target.getParent());
        writeAtomically(target, bytes);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path target = resolveSafely(storageKey);
        Files.deleteIfExists(target);
    }

    @Override
    public Optional<String> signedUrl(String storageKey) {
        return Optional.empty();
    }

    private String buildKey(StorageKeyHint hint) {
        // reports/v2/<yearMonth>/<reportCode>/<reportNumber>.<extension>
        return String.join("/",
            "reports", "v2", hint.yearMonth(), hint.reportCode(), hint.reportNumber() + "." + hint.extension());
    }

    private Path resolveSafely(String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey obrigatorio");
        }
        if (storageKey.contains("..") || storageKey.startsWith("/") || storageKey.contains("\0")) {
            throw new SecurityException("storageKey invalida: " + storageKey);
        }
        Files.createDirectories(baseDir);
        // canonicaliza baseDir (lida com symlinks tipicos no macOS /var -> /private/var)
        Path baseReal = baseDir.toRealPath();
        Path candidate = baseReal.resolve(storageKey).normalize().toAbsolutePath();
        if (!candidate.startsWith(baseReal)) {
            throw new SecurityException("path traversal detectado: " + storageKey);
        }
        return candidate;
    }

    private void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp-" + System.nanoTime());
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailed) {
            // fallback para filesystems que nao suportam ATOMIC_MOVE
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
