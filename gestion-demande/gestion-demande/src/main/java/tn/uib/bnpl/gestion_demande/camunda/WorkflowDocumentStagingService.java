package tn.uib.bnpl.gestion_demande.camunda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
public class WorkflowDocumentStagingService {

    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket:bnpl-documents}")
    private String bucket;

    public WorkflowDocumentStagingService(MinioClient minioClient, ObjectMapper objectMapper) {
        this.minioClient = minioClient;
        this.objectMapper = objectMapper;
    }

    public String stageDocuments(String businessKey, Map<String, MultipartFile> files) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (Map.Entry<String, MultipartFile> e : files.entrySet()) {
            MultipartFile file = e.getValue();
            if (file == null || file.isEmpty()) {
                continue;
            }
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : e.getKey();
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            String objectKey = "workflow-staging/" + businessKey + "/" + e.getKey() + "/" + System.currentTimeMillis()
                    + "-" + filename;
            try (InputStream is = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(is, file.getSize(), -1)
                        .contentType(contentType)
                        .build());
            } catch (Exception ex) {
                throw new RuntimeException("Staging MinIO échoué pour " + filename, ex);
            }
            keys.put(e.getKey(), objectKey + "|" + contentType + "|" + filename);
        }
        try {
            return objectMapper.writeValueAsString(keys);
        } catch (Exception ex) {
            throw new RuntimeException("Sérialisation documentKeys", ex);
        }
    }

    public Map<String, MultipartFile> loadStagedFiles(String documentKeysJson) {
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        if (documentKeysJson == null || documentKeysJson.isBlank()) {
            return files;
        }
        try {
            Map<String, String> meta = objectMapper.readValue(
                    documentKeysJson, new TypeReference<Map<String, String>>() {});
            for (Map.Entry<String, String> e : meta.entrySet()) {
                String[] parts = e.getValue().split("\\|", 3);
                String objectKey = parts[0];
                String contentType = parts.length > 1 ? parts[1] : "application/octet-stream";
                String filename = parts.length > 2 ? parts[2] : e.getKey();
                byte[] bytes = readObject(objectKey);
                files.put(e.getKey(), new BytesMultipartFile(e.getKey(), filename, contentType, bytes));
            }
        } catch (Exception ex) {
            throw new RuntimeException("Chargement documents workflow", ex);
        }
        return files;
    }

    private byte[] readObject(String objectKey) throws Exception {
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return is.readAllBytes();
        }
    }
}
