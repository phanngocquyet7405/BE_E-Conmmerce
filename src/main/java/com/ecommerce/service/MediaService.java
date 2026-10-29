package com.ecommerce.service;

import com.ecommerce.dto.response.MediaUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MediaService {
    MediaUploadResponse uploadFile(MultipartFile file);
    List<MediaUploadResponse> uploadMultipleFiles(List<MultipartFile> files);
}
