package br.com.dms.service;

import br.com.dms.controller.request.DocumentChatRequest;
import br.com.dms.controller.response.DocumentChatResponse;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.exception.DmsDocumentNotFoundException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DocumentChatService {

    private static final int MAX_CONTEXT_CHUNKS = 4;
    private static final int MAX_CHUNK_SIZE = 450;

    private final TenantContextService tenantContextService;
    private final DmsDocumentRepository dmsDocumentRepository;
    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final boolean ragEnabled;
    private final boolean chatEnabled;
    private final boolean localProviderEnabled;
    private final String localProviderBaseUrl;
    private final String localProviderModel;

    public DocumentChatService(TenantContextService tenantContextService,
                               DmsDocumentRepository dmsDocumentRepository,
                               DmsDocumentVersionRepository dmsDocumentVersionRepository,
                               @Value("${dms.ai.rag.document.enabled:false}") boolean ragEnabled,
                               @Value("${dms.ai.chat.document.enabled:false}") boolean chatEnabled,
                               @Value("${dms.ai.provider.local.enabled:true}") boolean localProviderEnabled,
                               @Value("${dms.ai.provider.local.base-url:http://localhost:11434}") String localProviderBaseUrl,
                               @Value("${dms.ai.provider.local.model:llama3.1:8b}") String localProviderModel) {
        this.tenantContextService = tenantContextService;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.ragEnabled = ragEnabled;
        this.chatEnabled = chatEnabled;
        this.localProviderEnabled = localProviderEnabled;
        this.localProviderBaseUrl = localProviderBaseUrl;
        this.localProviderModel = localProviderModel;
    }

    public DocumentChatResponse chat(String documentId, DocumentChatRequest request) {
        if (!ragEnabled || !chatEnabled) {
            return DocumentChatResponse.builder()
                    .documentId(documentId)
                    .version(request.getVersion())
                    .enabled(false)
                    .status("DISABLED")
                    .message("Chat/RAG desabilitado por feature flags (dms.ai.rag.document.enabled e/ou dms.ai.chat.document.enabled).")
                    .contextChunks(List.of())
                    .build();
        }

        String tenantId = tenantContextService.requireTenantId();
        DmsDocument document = dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DmsDocumentNotFoundException("Document not found", TypeException.VALID));

        Optional<DmsDocumentVersion> targetVersion = StringUtils.isNotBlank(request.getVersion())
                ? dmsDocumentVersionRepository.findByTenantIdAndDmsDocumentIdAndVersionNumber(tenantId, documentId, request.getVersion())
                : dmsDocumentVersionRepository.findLastVersionByTenantIdAndDmsDocumentId(tenantId, documentId);

        String resolvedVersion = targetVersion
                .map(v -> v.getVersionNumber().stripTrailingZeros().toPlainString())
                .orElse(request.getVersion());

        List<String> contextChunks = buildContextChunks(document);
        String prompt = buildPrompt(document, contextChunks, request.getMessage());

        if (!localProviderEnabled) {
            return DocumentChatResponse.builder()
                    .documentId(documentId)
                    .version(resolvedVersion)
                    .enabled(true)
                    .status("PROVIDER_DISABLED")
                    .message("Provedor local de IA desabilitado (dms.ai.provider.local.enabled=false).")
                    .contextChunks(contextChunks)
                    .build();
        }

        try {
            String answer = queryLocalProvider(prompt);
            return DocumentChatResponse.builder()
                    .documentId(documentId)
                    .version(resolvedVersion)
                    .enabled(true)
                    .status("OK")
                    .message("Resposta gerada com contexto do documento.")
                    .answer(answer)
                    .model(localProviderModel)
                    .contextChunks(contextChunks)
                    .build();
        } catch (Exception ex) {
            return DocumentChatResponse.builder()
                    .documentId(documentId)
                    .version(resolvedVersion)
                    .enabled(true)
                    .status("PROVIDER_UNAVAILABLE")
                    .message("Serviço local de IA indisponível. Verifique Ollama e tente novamente.")
                    .contextChunks(contextChunks)
                    .build();
        }
    }

    private String queryLocalProvider(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", localProviderModel);
        payload.put("prompt", prompt);
        payload.put("stream", false);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                localProviderBaseUrl + "/api/generate",
                new HttpEntity<>(payload, headers),
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Erro ao consultar provedor local");
        }

        Object answer = response.getBody().get("response");
        if (answer == null) {
            throw new RuntimeException("Resposta vazia do provedor local");
        }
        return answer.toString();
    }

    private List<String> buildContextChunks(DmsDocument document) {
        List<String> chunks = new ArrayList<>();

        if (document.getMetadata() != null && !document.getMetadata().isEmpty()) {
            String metadataChunk = document.getMetadata().entrySet().stream()
                    .limit(12)
                    .map(entry -> entry.getKey() + ": " + String.valueOf(entry.getValue()))
                    .collect(Collectors.joining("\n"));
            if (StringUtils.isNotBlank(metadataChunk)) {
                chunks.add("Metadados relevantes:\n" + metadataChunk);
            }
        }

        String ocrText = StringUtils.trimToEmpty(document.getOcrText());
        if (StringUtils.isBlank(ocrText)) {
            return chunks.stream().limit(MAX_CONTEXT_CHUNKS).toList();
        }

        String normalized = ocrText.replace("\r", "\n");
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String clean = StringUtils.normalizeSpace(paragraph);
            if (StringUtils.isBlank(clean)) {
                continue;
            }
            int end = Math.min(clean.length(), MAX_CHUNK_SIZE);
            chunks.add(clean.substring(0, end));
            if (chunks.size() >= MAX_CONTEXT_CHUNKS) {
                break;
            }
        }

        return chunks;
    }

    private String buildPrompt(DmsDocument document, List<String> contextChunks, String question) {
        StringBuilder builder = new StringBuilder();
        builder.append("Você é um assistente do DMS. Responda em português do Brasil, objetivo e fiel ao contexto.\n");
        builder.append("Documento: ").append(StringUtils.defaultString(document.getFilename(), document.getId())).append("\n");
        builder.append("Categoria: ").append(StringUtils.defaultString(document.getCategory(), "n/a")).append("\n\n");
        builder.append("Contexto recuperado:\n");

        for (int i = 0; i < contextChunks.size(); i++) {
            builder.append("[").append(i + 1).append("] ").append(contextChunks.get(i)).append("\n");
        }

        if (contextChunks.isEmpty()) {
            builder.append("(Sem contexto OCR disponível; use apenas metadados básicos)\n");
        }

        builder.append("\nPergunta do usuário: ").append(question).append("\n");
        builder.append("Se não houver informação suficiente, diga explicitamente que não encontrou no documento.");
        return builder.toString();
    }
}
