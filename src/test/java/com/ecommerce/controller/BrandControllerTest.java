package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.dto.request.BrandRequest;
import com.ecommerce.dto.response.BrandResponse;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.service.BrandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BrandControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private BrandService brandService;

    @InjectMocks
    private BrandController brandController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(brandController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Admin tạo thương hiệu không cần truyền slug (tương thích form Frontend): Trả về 201 Created")
    void createBrand_withoutSlug_Returns201() throws Exception {
        BrandRequest request = BrandRequest.builder()
                .name("Sony")
                .logoUrl("https://res.cloudinary.com/dpebxdjot/image/upload/sony.png")
                .description("Tập đoàn công nghệ Sony")
                .build();

        BrandResponse response = BrandResponse.builder()
                .id("brand-sony-1")
                .name("Sony")
                .slug("sony")
                .logoUrl(request.getLogoUrl())
                .description(request.getDescription())
                .build();

        when(brandService.createBrand(any(BrandRequest.class))).thenReturn(response);

        mockMvc.perform(post(ApiRoutes.Brands.ADMIN_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Sony"))
                .andExpect(jsonPath("$.data.slug").value("sony"));
    }

    @Test
    @DisplayName("Admin tạo thương hiệu thiếu tên: Trả về 400 Bad Request VALIDATION_ERROR")
    void createBrand_missingName_Returns400() throws Exception {
        BrandRequest request = BrandRequest.builder()
                .name("")
                .build();

        mockMvc.perform(post(ApiRoutes.Brands.ADMIN_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @DisplayName("Public xem danh sách thương hiệu: Trả về 200 OK")
    void getAllBrands_Returns200() throws Exception {
        when(brandService.getAllBrands()).thenReturn(List.of(
                BrandResponse.builder().id("b1").name("Sony").slug("sony").build()
        ));

        mockMvc.perform(get(ApiRoutes.Brands.PUBLIC_BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Sony"));
    }
}
