package com.opsflow.document_service.infrastructure.adapters.storage;

import com.opsflow.document_service.application.dtos.StoredFileInfo;
import com.opsflow.document_service.domain.enums.ErrorCode;
import com.opsflow.document_service.domain.exceptions.OpsFlowStorageException;
import com.opsflow.document_service.domain.port.out.FileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path rootLocation;

    public LocalFileStorageAdapter(@Value("${app.storage.location:uploads}") String location) {
        this.rootLocation = Paths.get(location);
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new OpsFlowStorageException("Could not initialize storage location", ErrorCode.STORAGE_INITIALIZATION_FAILED, e);
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (file.isEmpty()) {
            throw new OpsFlowStorageException("Failed to store empty file.", ErrorCode.FILE_EMPTY);
        }

        try {
            Path folderPath = rootLocation.resolve(folder);
            Files.createDirectories(folderPath);

            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path destinationFile = folderPath.resolve(Paths.get(filename))
                    .normalize().toAbsolutePath();

            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return folder + "/" + filename;
        } catch (IOException e) {
            throw new OpsFlowStorageException("Failed to store file.", ErrorCode.FILE_READ_ERROR, e);
        }
    }

    @Override
    public byte[] downloadFile(String fileUrl) {
        try {
            Path file = rootLocation.resolve(fileUrl);
            if (!Files.exists(file)) {
                throw new OpsFlowStorageException("File not found: " + fileUrl, ErrorCode.FILE_NOT_FOUND);
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new OpsFlowStorageException("Could not read file", ErrorCode.FILE_READ_ERROR, e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            Path file = rootLocation.resolve(fileUrl);
            if (!Files.exists(file)) {
                throw new OpsFlowStorageException("Cannot delete. File not found: " + fileUrl, ErrorCode.FILE_NOT_FOUND);
            }
            Files.delete(file);
        } catch (IOException e) {
            throw new OpsFlowStorageException("Could not delete file", ErrorCode.FILE_READ_ERROR, e);
        }
    }

    @Override
    public List<StoredFileInfo> listFiles(String prefix) {
        Path base = (prefix == null || prefix.isBlank())
                ? rootLocation
                : rootLocation.resolve(prefix);
        if (!Files.exists(base)) {
            return List.of();
        }
        List<StoredFileInfo> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    String key = rootLocation.relativize(p).toString().replace('\\', '/');
                    out.add(new StoredFileInfo(
                            key,
                            attrs.size(),
                            attrs.lastModifiedTime().toInstant(),
                            null
                    ));
                } catch (IOException ignore) {
                }
            });
        } catch (IOException e) {
            throw new OpsFlowStorageException("Could not list local files", ErrorCode.FILE_READ_ERROR, e);
        }
        out.sort(Comparator.comparing(StoredFileInfo::key));
        return out;
    }
}
