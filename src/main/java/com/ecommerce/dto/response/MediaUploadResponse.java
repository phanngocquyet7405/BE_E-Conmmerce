package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MediaUploadResponse {
    private final String fileName;
    private final String originalName;
    private final String fileUrl;
    private final String contentType;
    private final long size;
}
