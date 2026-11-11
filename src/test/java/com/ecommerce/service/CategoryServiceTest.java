package com.ecommerce.service;

import com.ecommerce.document.Category;
import com.ecommerce.dto.request.CategoryRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private CategoryRequest categoryRequest;
    private Category category;
    private CategoryResponse categoryResponse;

    @BeforeEach
    void setUp() {
        categoryRequest = CategoryRequest.builder()
                .name("Điện Thoại & Tablet")
                .description("Ngành hàng điện thoại di động")
                .build();

        category = Category.builder()
                .id("cat-123")
                .name("Điện Thoại & Tablet")
                .slug("dien-thoai-tablet")
                .description(categoryRequest.getDescription())
                .build();

        categoryResponse = CategoryResponse.builder()
                .id("cat-123")
                .name("Điện Thoại & Tablet")
                .slug("dien-thoai-tablet")
                .description(categoryRequest.getDescription())
                .build();
    }

    @Test
    @DisplayName("Tạo danh mục không truyền slug: Tự động sinh slug tiếng Việt chuẩn")
    void createCategory_withoutSlug_shouldAutoGenerateSlug() {
        when(categoryRepository.existsByNameIgnoreCase("Điện Thoại & Tablet")).thenReturn(false);
        when(categoryMapper.toDocument(categoryRequest)).thenReturn(category);
        when(categoryRepository.existsBySlug("dien-thoai-tablet")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryMapper.toResponse(any(Category.class))).thenReturn(categoryResponse);

        CategoryResponse result = categoryService.createCategory(categoryRequest);

        assertNotNull(result);
        assertEquals("Điện Thoại & Tablet", result.getName());
        assertEquals("dien-thoai-tablet", result.getSlug());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("Tạo danh mục trùng tên: Ném AppException VALIDATION_ERROR")
    void createCategory_duplicateName_shouldThrowException() {
        when(categoryRepository.existsByNameIgnoreCase("Điện Thoại & Tablet")).thenReturn(true);

        assertThrows(AppException.class, () -> categoryService.createCategory(categoryRequest));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật danh mục: Thành công và kiểm tra tính duy nhất")
    void updateCategory_success() {
        when(categoryRepository.findById("cat-123")).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Điện Thoại & Tablet", "cat-123")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryMapper.toResponse(any(Category.class))).thenReturn(categoryResponse);

        CategoryResponse result = categoryService.updateCategory("cat-123", categoryRequest);

        assertNotNull(result);
        verify(categoryRepository).save(any(Category.class));
    }
}
