package com.opsflow.document_service.domain.port.out;

import com.opsflow.document_service.application.dtos.StoredFileInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileStoragePort {

    String uploadFile(MultipartFile file, String folder);
    byte[] downloadFile(String fileUrl);
    void deleteFile(String fileUrl);
    List<StoredFileInfo> listFiles(String prefix);
}
