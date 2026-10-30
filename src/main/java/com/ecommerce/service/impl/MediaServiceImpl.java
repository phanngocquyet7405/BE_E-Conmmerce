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
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
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

    @Autowired
    private Environment environment;

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

        // Kiểm tra Magic Bytes nhị phân (bao gồm kiểm tra WebP: bytes 0-3 "RIFF" và bytes 8-11 "WEBP")
        if (!hasValidImageMagicBytes(file)) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE, "Nội dung file không phải là ảnh hợp lệ");
        }

        String safeExtension = getSafeExtension(contentType);
        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : ("image" + safeExtension));

        // 1. Ưu tiên stream trực tiếp lên Cloudinary (tránh load nguyên byte[] vào JVM heap 512MB)
        if (cloudinary != null) {
            try (InputStream is = file.getInputStream()) {
                Map<?, ?> uploadResult = cloudinary.uploader().upload(is, ObjectUtils.asMap(
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
                log.error("Lỗi khi stream ảnh lên Cloudinary: ", e);
                if (isProdProfile()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Dịch vụ lưu trữ media đám mây (Cloudinary) gặp sự cố trên môi trường Production.");
                }
            }
        } else if (isProdProfile()) {
            // Trên Production: bắt buộc phải có Cloudinary, không được lưu cục bộ
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ lưu trữ media đám mây (Cloudinary) chưa được cấu hình trên môi trường Production.");
        }

        // 2. Fallback lưu cục bộ cho môi trường dev/test:
        try {
            Path targetFolder = Paths.get(uploadDir, "images").toAbsolutePath().normalize();
            if (!Files.exists(targetFolder)) {
                Files.createDirectories(targetFolder);
            }

            String uniqueFileName = UUID.randomUUID().toString() + safeExtension;
            Path targetLocation = targetFolder.resolve(uniqueFileName);
            
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            String fileUrl = uploadBaseUrl + "/images/" + uniqueFileName;
            log.info("Đã lưu ảnh cục bộ: {}", fileUrl);

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

    private boolean isProdProfile() {
        if (environment == null) return false;
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private String getSafeExtension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        };
    }

    private boolean hasValidImageMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 12) return false;

            // JPEG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) return true;
            // PNG: 89 50 4E 47
            if ((header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47) return true;
            // GIF: 47 49 46 38 ('GIF8')
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') return true;
            // WEBP: Bytes 0-3 = "RIFF" và Bytes 8-11 = "WEBP"
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
