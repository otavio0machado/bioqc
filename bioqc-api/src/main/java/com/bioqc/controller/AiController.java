package com.bioqc.controller;

import com.bioqc.dto.request.AiAnalysisRequest;
import com.bioqc.dto.request.VoiceFormRequest;
import com.bioqc.dto.response.AiAnalysisResponse;
import com.bioqc.entity.QcRecord;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.service.GeminiAiService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiAiService geminiAiService;
    private final QcRecordRepository qcRecordRepository;

    public AiController(GeminiAiService geminiAiService, QcRecordRepository qcRecordRepository) {
        this.geminiAiService = geminiAiService;
        this.qcRecordRepository = qcRecordRepository;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AiAnalysisResponse> analyze(@Valid @RequestBody AiAnalysisRequest request) {
        String response;
        if (request.area() != null && !request.area().isBlank()) {
            int days = request.days() == null ? 30 : request.days();
            LocalDate startDate = LocalDate.now().minusDays(days);
            List<QcRecord> records = qcRecordRepository.findAllByOrderByDateDesc().stream()
                .filter(record -> request.area().equalsIgnoreCase(record.getArea()))
                .filter(record -> request.examName() == null || request.examName().isBlank()
                    || request.examName().equalsIgnoreCase(record.getExamName()))
                .filter(record -> !record.getDate().isBefore(startDate))
                .toList();
            response = geminiAiService.analyze(request.prompt(), geminiAiService.buildQcContext(records));
        } else {
            response = geminiAiService.analyze(request.prompt(), request.context());
        }
        return ResponseEntity.ok(new AiAnalysisResponse(response));
    }

    @PostMapping("/voice-to-form")
    public ResponseEntity<Map<String, Object>> voiceToForm(@Valid @RequestBody VoiceFormRequest request) {
        return ResponseEntity.ok(
            geminiAiService.processVoiceForm(request.audioBase64(), request.formType(), request.mimeType())
        );
    }
}
