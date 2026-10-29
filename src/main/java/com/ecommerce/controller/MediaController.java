package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.dto.response.MediaUploadResponse;
import com.ecommerce.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping(ApiRoutes.Media.BASE)
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = ApiRoutes.Media.UPLOAD, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadSingleFile(
            @RequestParam("file") MultipartFile file) {
        MediaUploadResponse response = mediaService.uploadFile(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tải ảnh lên thành công", response));
    }

    @PostMapping(value = ApiRoutes.Media.UPLOAD_MULTIPLE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<MediaUploadResponse>>> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files) {
        List<MediaUploadResponse> responses = mediaService.uploadMultipleFiles(files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tải nhiều ảnh lên thành công", responses));
    }
}
