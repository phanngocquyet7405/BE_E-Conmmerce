package com.ecommerce.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ecommerce.dto.response.MediaUploadResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/jpg"
    );

    @Autowired(required = false)
    private Cloudinary cloudinary;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:http://localhost:8080/uploads}")
    private String uploadBaseUrl;

    @Override
    public MediaUploadResponse uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "File tải lên không được để trống");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg");

        // 1. Ưu tiên tải thẳng lên Cloudinary nếu đã cấu hình tài khoản
        if (cloudinary != null) {
            try {
                Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                        "folder", "ecommerce_products",
                        "resource_type", "image"
                ));

                String secureUrl = (String) uploadResult.get("secure_url");
                String publicId = (String) uploadResult.get("public_id");

                log.info("Đã tải ảnh thành công lên Cloudinary: {}", secureUrl);

                return MediaUploadResponse.builder()
                        .fileName(publicId)
                        .originalName(originalFilename)
                        .fileUrl(secureUrl)
                        .contentType(contentType)
                        .size(file.getSize())
                        .build();

            } catch (Exception e) {
                log.error("Lỗi khi tải ảnh lên Cloudinary, chuyển sang lưu cục bộ: ", e);
            }
        }

        // 2. Fallback lưu cục bộ nếu chưa cấu hình Cloudinary
        try {
            Path targetFolder = Paths.get(uploadDir, "images").toAbsolutePath().normalize();
            if (!Files.exists(targetFolder)) {
                Files.createDirectories(targetFolder);
            }

            String extension = "";
            int extIndex = originalFilename.lastIndexOf(".");
            if (extIndex > 0) {
                extension = originalFilename.substring(extIndex);
            }

            String uniqueFileName = UUID.randomUUID().toString() + extension;
            Path targetLocation = targetFolder.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = uploadBaseUrl + "/images/" + uniqueFileName;

            return MediaUploadResponse.builder()
                    .fileName(uniqueFileName)
                    .originalName(originalFilename)
                    .fileUrl(fileUrl)
                    .contentType(contentType)
                    .size(file.getSize())
                    .build();

        } catch (IOException e) {
            log.error("Lỗi khi lưu trữ file cục bộ: ", e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public List<MediaUploadResponse> uploadMultipleFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Danh sách file tải lên không được để trống");
        }
        List<MediaUploadResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                responses.add(uploadFile(file));
            }
        }
        return responses;
    }
}
