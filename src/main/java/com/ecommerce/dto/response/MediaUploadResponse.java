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

    /**
     * Alias để tương thích với các frontend client (Next.js/React/Mobile) sử dụng field `url`
     */
    public String getUrl() {
        return fileUrl;
    }

    /**
     * Alias để tương thích với các frontend client sử dụng field `publicId`
     */
    public String getPublicId() {
        return fileName;
    }
}

