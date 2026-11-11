package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.dto.response.MediaUploadResponse;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private MediaController mediaController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mediaController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Upload ảnh đơn thành công trả về HTTP 201 và URL ảnh")
    void uploadSingleFile_Returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        MediaUploadResponse mockResponse = MediaUploadResponse.builder()
                .fileName("sample.jpg")
                .originalName("test.jpg")
                .fileUrl("https://res.cloudinary.com/demo/image/upload/sample.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .build();

        when(mediaService.uploadFile(any())).thenReturn(mockResponse);

        mockMvc.perform(multipart(ApiRoutes.Media.BASE + ApiRoutes.Media.UPLOAD).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.fileUrl").value("https://res.cloudinary.com/demo/image/upload/sample.jpg"))
                .andExpect(jsonPath("$.data.url").value("https://res.cloudinary.com/demo/image/upload/sample.jpg"))
                .andExpect(jsonPath("$.data.fileName").value("sample.jpg"))
                .andExpect(jsonPath("$.data.publicId").value("sample.jpg"));
    }

    @Test
    @DisplayName("Upload nhiều ảnh thành công trả về HTTP 201 và danh sách URL ảnh")
    void uploadMultipleFiles_Returns201() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "test1.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "test2.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        MediaUploadResponse r1 = MediaUploadResponse.builder().fileUrl("https://res.cloudinary.com/demo/1.jpg").build();
        MediaUploadResponse r2 = MediaUploadResponse.builder().fileUrl("https://res.cloudinary.com/demo/2.png").build();

        when(mediaService.uploadMultipleFiles(any())).thenReturn(List.of(r1, r2));

        mockMvc.perform(multipart(ApiRoutes.Media.BASE + ApiRoutes.Media.UPLOAD_MULTIPLE).file(file1).file(file2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
