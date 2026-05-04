package com.bioqc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.bioqc.entity.QcRecord;
import com.bioqc.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class GeminiAiService {

    private static final String FRIENDLY_ERROR = "Não foi possível analisar no momento. Tente novamente.";
    private static final Map<String, String> VOICE_FORM_PROMPTS = Map.of(
        "registro",
        """
            Voce e um assistente de laboratorio de analises clinicas. O usuario esta ditando dados para registrar um controle de qualidade.

            Extraia os campos:
            - exam_name
            - value
            - target_value
            - equipment
            - analyst

            Regras:
            - exam_name em MAIUSCULAS
            - numeros devem ser float com ponto decimal
            - campos ausentes devem ser "" para texto e null para numeros
            - retorne APENAS um JSON valido

            Resposta:
            {"exam_name":"","value":null,"target_value":null,"equipment":"","analyst":""}
            """,
        "referencia",
        """
            Voce e um assistente de laboratorio de analises clinicas. O usuario esta ditando dados para cadastrar uma referencia de CQ.

            Extraia os campos:
            - name
            - exam_name
            - level
            - valid_from
            - valid_until
            - target_value
            - cv_max
            - lot_number
            - manufacturer
            - notes

            Regras:
            - exam_name em MAIUSCULAS
            - datas no formato YYYY-MM-DD
            - level deve ser "Normal", "N1", "N2" ou "N3"
            - retorne APENAS um JSON valido

            Resposta:
            {"name":"","exam_name":"","level":"Normal","valid_from":"","valid_until":"","target_value":null,"cv_max":null,"lot_number":"","manufacturer":"","notes":""}
            """,
        "reagente",
        """
            Voce e um assistente de laboratorio de analises clinicas. O usuario esta ditando dados para cadastrar um lote de reagente.

            Extraia os campos:
            - name
            - lot_number
            - expiry_date
            - initial_stock
            - daily_consumption
            - manufacturer

            Regras:
            - datas no formato YYYY-MM-DD
            - numeros devem ser float
            - retorne APENAS um JSON valido

            Resposta:
            {"name":"","lot_number":"","expiry_date":"","initial_stock":null,"daily_consumption":null,"manufacturer":""}
            """,
        "manutencao",
        """
            Voce e um assistente de laboratorio de analises clinicas. O usuario esta ditando dados para registrar uma manutencao de equipamento.

            Extraia os campos:
            - equipment
            - type
            - date
            - next_date
            - notes

            Regras:
            - type deve ser "Preventiva", "Corretiva" ou "Calibração"
            - datas no formato YYYY-MM-DD
            - retorne APENAS um JSON valido

            Resposta:
            {"equipment":"","type":"","date":"","next_date":"","notes":""}
            """
    );
    private static final String SYSTEM_PROMPT = """
        Você é um especialista em controle de qualidade laboratorial, com profundo conhecimento em:
        - Regras de Westgard (1-2s, 1-3s, 2-2s, R-4s, 4-1s, 10x)
        - Gráficos de Levey-Jennings
        - Coeficiente de Variação (CV%)
        - Calibração de equipamentos
        - Gestão de reagentes e lotes

        Responda sempre em português do Brasil, de forma clara e prática.
        Quando analisar dados, aponte:
        1. Tendências observadas
        2. Problemas identificados
        3. Recomendações de ação
        """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final Map<String, Deque<Instant>> rateLimitByUser = new ConcurrentHashMap<>();
    private final int maxAudioBytes;
    private final MeterRegistry meterRegistry;

    public GeminiAiService(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        @Value("${gemini.api-key}") String apiKey,
        @Value("${gemini.model}") String model,
        @Value("${voice.max-audio-bytes:2097152}") int maxAudioBytes,
        MeterRegistry meterRegistry
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxAudioBytes = maxAudioBytes;
        this.meterRegistry = meterRegistry;
    }

    public String analyze(String userPrompt, String context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ensureRateLimit();
            String prompt = SYSTEM_PROMPT + "\n\n" + (context == null ? "" : context) + "\n\nPergunta: " + userPrompt;
            String result = extractTextResponse(callGemini(Map.of(
                "contents",
                List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            )));
            recordAiRequest("analyze", "success");
            sample.stop(aiLatencyTimer("analyze", "success"));
            return result;
        } catch (BusinessException exception) {
            recordAiRequest("analyze", "business_error");
            sample.stop(aiLatencyTimer("analyze", "business_error"));
            throw exception;
        } catch (java.io.IOException | RuntimeException exception) {
            recordAiRequest("analyze", "error");
            sample.stop(aiLatencyTimer("analyze", "error"));
            log.error("Erro na chamada Gemini para analyze", exception);
            return FRIENDLY_ERROR;
        }
    }

    public Map<String, Object> processVoiceForm(String audioBase64, String formType, String mimeType) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ensureRateLimit();
            String prompt = VOICE_FORM_PROMPTS.get(formType);
            if (prompt == null) {
                throw new BusinessException("Tipo de formulário de voz inválido");
            }

            byte[] audioBytes;
            try {
                audioBytes = Base64.getDecoder().decode(audioBase64);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("Áudio inválido ou corrompido.");
            }

            if (audioBytes.length < 100) {
                throw new BusinessException("Áudio muito curto. Grave novamente.");
            }
            if (audioBytes.length > maxAudioBytes) {
                double limitMb = maxAudioBytes / (1024D * 1024D);
                throw new BusinessException("Áudio excede o limite de %.1f MB.".formatted(limitMb));
            }

            JsonNode root = callGemini(Map.of(
                "contents",
                List.of(
                    Map.of(
                        "parts",
                        List.of(
                            Map.of(
                                "inline_data",
                                Map.of(
                                    "mime_type",
                                    mimeType == null || mimeType.isBlank() ? "audio/webm" : mimeType,
                                    "data",
                                    audioBase64
                                )
                            ),
                            Map.of("text", prompt)
                        )
                    )
                )
            ));
            String rawText = cleanJsonResponse(extractTextResponse(root));
            Map<String, Object> result = objectMapper.readValue(rawText, new TypeReference<Map<String, Object>>() {
            });
            recordAiRequest("voice-to-form", "success");
            sample.stop(aiLatencyTimer("voice-to-form", "success"));
            return result;
        } catch (BusinessException exception) {
            recordAiRequest("voice-to-form", "business_error");
            sample.stop(aiLatencyTimer("voice-to-form", "business_error"));
            throw exception;
        } catch (java.io.IOException exception) {
            recordAiRequest("voice-to-form", "error");
            sample.stop(aiLatencyTimer("voice-to-form", "error"));
            log.error("Erro na chamada Gemini para voice-to-form (IOException)", exception);
            throw new BusinessException("Não foi possível interpretar a resposta da IA.");
        } catch (RuntimeException exception) {
            recordAiRequest("voice-to-form", "error");
            sample.stop(aiLatencyTimer("voice-to-form", "error"));
            log.error("Erro na chamada Gemini para voice-to-form", exception);
            throw new BusinessException(FRIENDLY_ERROR);
        }
    }

    public String buildQcContext(List<QcRecord> records) {
        StringBuilder context = new StringBuilder("Dados de Controle de Qualidade do Laboratório Biodiagnóstico:\n\n");
        for (QcRecord record : records) {
            context.append(String.format(
                "Data: %s | Exame: %s | Nível: %s | Valor: %.2f | Alvo: %.2f | SD: %.2f | CV: %.2f%% | Status: %s%n",
                record.getDate(),
                record.getExamName(),
                record.getLevel(),
                record.getValue(),
                record.getTargetValue(),
                record.getTargetSd(),
                record.getCv(),
                record.getStatus()
            ));
            if (record.getViolations() != null) {
                record.getViolations().forEach(violation -> context
                    .append("  -> Violação: ")
                    .append(violation.getRule())
                    .append(" - ")
                    .append(violation.getDescription())
                    .append('\n'));
            }
        }
        return context.toString();
    }

    private void ensureRateLimit() {
        String userKey = resolveCurrentUserKey();
        Deque<Instant> calls = rateLimitByUser.computeIfAbsent(userKey, ignored -> new ArrayDeque<>());
        Instant now = Instant.now();
        while (!calls.isEmpty() && Duration.between(calls.peekFirst(), now).toSeconds() >= 60) {
            calls.pollFirst();
        }
        if (calls.size() >= 10) {
            throw new BusinessException("Limite de 10 análises por minuto excedido");
        }
        calls.addLast(now);
    }

    private String resolveCurrentUserKey() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private JsonNode callGemini(Map<String, Object> body) throws java.io.IOException {
        ensureApiKeyConfigured();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
            .formatted(model);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        return objectMapper.readTree(response.getBody());
    }

    private String extractTextResponse(JsonNode root) {
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new BusinessException(FRIENDLY_ERROR);
        }
        return textNode.asText();
    }

    private void ensureApiKeyConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("GEMINI_API_KEY não configurada");
        }
    }

    private void recordAiRequest(String endpoint, String status) {
        Counter.builder("bioqc.ai.requests")
            .description("Number of AI API requests")
            .tag("endpoint", endpoint)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    private Timer aiLatencyTimer(String endpoint, String status) {
        return Timer.builder("bioqc.ai.latency")
            .description("Latency of AI API calls")
            .tag("endpoint", endpoint)
            .tag("status", status)
            .register(meterRegistry);
    }

    private String cleanJsonResponse(String rawText) {
        String cleaned = rawText == null ? "" : rawText.trim();
        if (cleaned.startsWith("```")) {
            int firstNewLine = cleaned.indexOf('\n');
            cleaned = firstNewLine >= 0 ? cleaned.substring(firstNewLine + 1).trim() : cleaned;
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }
        if (!cleaned.startsWith("{")) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
        }
        return cleaned;
    }
}
