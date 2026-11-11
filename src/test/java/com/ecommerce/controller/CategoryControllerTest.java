package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.dto.request.CategoryRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.service.CategoryService;
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
class CategoryControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryController categoryController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(categoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Admin tạo danh mục không cần truyền slug (tương thích form Frontend): Trả về 201 Created")
    void createCategory_withoutSlug_Returns201() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("Điện Thoại")
                .description("Danh mục điện thoại các loại")
                .build();

        CategoryResponse response = CategoryResponse.builder()
                .id("cat-1")
                .name("Điện Thoại")
                .slug("dien-thoai")
                .description(request.getDescription())
                .build();

        when(categoryService.createCategory(any(CategoryRequest.class))).thenReturn(response);

        mockMvc.perform(post(ApiRoutes.Categories.ADMIN_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Điện Thoại"))
                .andExpect(jsonPath("$.data.slug").value("dien-thoai"));
    }

    @Test
    @DisplayName("Admin tạo danh mục thiếu tên: Trả về 400 Bad Request VALIDATION_ERROR")
    void createCategory_missingName_Returns400() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("")
                .build();

        mockMvc.perform(post(ApiRoutes.Categories.ADMIN_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @DisplayName("Public xem danh sách danh mục: Trả về 200 OK")
    void getAllCategories_Returns200() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of(
                CategoryResponse.builder().id("c1").name("Điện Thoại").slug("dien-thoai").build()
        ));

        mockMvc.perform(get(ApiRoutes.Categories.PUBLIC_BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Điện Thoại"));
    }
}
