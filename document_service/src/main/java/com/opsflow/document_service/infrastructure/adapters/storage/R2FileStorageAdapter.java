package com.opsflow.document_service.infrastructure.adapters.storage;

import com.opsflow.document_service.application.dtos.StoredFileInfo;
import com.opsflow.document_service.domain.enums.ErrorCode;
import com.opsflow.document_service.domain.exceptions.OpsFlowStorageException;
import com.opsflow.document_service.domain.port.out.FileStoragePort;
import com.opsflow.document_service.infrastructure.config.R2StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "true")
public class R2FileStorageAdapter implements FileStoragePort {

    private final S3Client s3;
    private final R2StorageProperties props;

    public R2FileStorageAdapter(S3Client r2S3Client, R2StorageProperties props) {
        this.s3 = r2S3Client;
        this.props = props;
        if (props.getBucket() == null || props.getBucket().isBlank()) {
            throw new OpsFlowStorageException(
                    "storage.r2.enabled=true pero storage.r2.bucket esta vacio",
                    ErrorCode.STORAGE_INITIALIZATION_FAILED);
        }
        log.info("R2 storage enabled. bucket={} endpoint={}", props.getBucket(), props.getEndpoint());
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (file.isEmpty()) {
            throw new OpsFlowStorageException("Failed to store empty file.", ErrorCode.FILE_EMPTY);
        }

        String safeFolder = stripLeadingSlashes(folder);
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String key = safeFolder == null || safeFolder.isBlank()
                ? filename
                : safeFolder + "/" + filename;

        try (InputStream in = file.getInputStream()) {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3.putObject(req, RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException | SdkException e) {
            throw new OpsFlowStorageException(
                    describeNetworkOrSdkError("Failed to upload to R2", e),
                    ErrorCode.FILE_READ_ERROR, e);
        }
        return key;
    }

    @Override
    public byte[] downloadFile(String fileUrl) {
        String key = stripLeadingSlashes(fileUrl);
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .build();
        try (ResponseInputStream<GetObjectResponse> in = s3.getObject(req)) {
            return in.readAllBytes();
        } catch (NoSuchKeyException e) {
            throw new OpsFlowStorageException("File not found in R2: " + key, ErrorCode.FILE_NOT_FOUND, e);
        } catch (IOException | SdkException e) {
            throw new OpsFlowStorageException(
                    describeNetworkOrSdkError("Could not read file from R2", e),
                    ErrorCode.FILE_READ_ERROR, e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        String key = stripLeadingSlashes(fileUrl);
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(key)
                    .build());
        } catch (SdkException e) {
            throw new OpsFlowStorageException(
                    describeNetworkOrSdkError("Could not delete file in R2", e),
                    ErrorCode.FILE_READ_ERROR, e);
        }
    }

    @Override
    public List<StoredFileInfo> listFiles(String prefix) {
        List<StoredFileInfo> out = new ArrayList<>();
        String safePrefix = (prefix == null) ? "" : stripLeadingSlashes(prefix);

        ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                .bucket(props.getBucket())
                .prefix(safePrefix);

        String continuationToken = null;
        try {
            do {
                if (continuationToken != null) {
                    reqBuilder.continuationToken(continuationToken);
                }
                ListObjectsV2Response resp = s3.listObjectsV2(reqBuilder.build());
                for (S3Object o : resp.contents()) {
                    out.add(new StoredFileInfo(
                            o.key(),
                            o.size(),
                            o.lastModified(),
                            buildPublicUrl(o.key())
                    ));
                }
                continuationToken = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
            } while (continuationToken != null);
        } catch (SdkException e) {
            throw new OpsFlowStorageException(
                    describeNetworkOrSdkError("Could not list R2 objects", e),
                    ErrorCode.FILE_READ_ERROR, e);
        }
        return out;
    }

    private String describeNetworkOrSdkError(String prefix, Exception e) {
        if (e instanceof SdkClientException) {
            return prefix + ": no se pudo contactar Cloudflare R2 (" + e.getMessage() + "). " +
                    "Comprueba conexion a internet, proxy corporativo o antivirus.";
        }
        if (e instanceof S3Exception s3e) {
            return prefix + ": " + s3e.awsErrorDetails().errorCode() + " - " + s3e.getMessage();
        }
        return prefix + ": " + e.getMessage();
    }

    private String buildPublicUrl(String key) {
        String base = props.getPublicBaseUrl();
        if (base == null || base.isBlank()) return null;
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/" + key;
    }

    private static String stripLeadingSlashes(String s) {
        if (s == null) return null;
        int i = 0;
        while (i < s.length() && s.charAt(i) == '/') i++;
        return s.substring(i);
    }
}
