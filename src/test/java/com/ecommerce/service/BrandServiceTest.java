package com.ecommerce.service;

import com.ecommerce.document.Brand;
import com.ecommerce.dto.request.BrandRequest;
import com.ecommerce.dto.response.BrandResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.mapper.BrandMapper;
import com.ecommerce.repository.BrandRepository;
import com.ecommerce.service.impl.BrandServiceImpl;
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
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private BrandMapper brandMapper;

    @InjectMocks
    private BrandServiceImpl brandService;

    private BrandRequest brandRequest;
    private Brand brand;
    private BrandResponse brandResponse;

    @BeforeEach
    void setUp() {
        brandRequest = BrandRequest.builder()
                .name("Sony")
                .logoUrl("https://res.cloudinary.com/test/image/upload/sony.png")
                .description("Hãng điện tử Sony")
                .build();

        brand = Brand.builder()
                .id("brand-123")
                .name("Sony")
                .slug("sony")
                .logoUrl(brandRequest.getLogoUrl())
                .description(brandRequest.getDescription())
                .build();

        brandResponse = BrandResponse.builder()
                .id("brand-123")
                .name("Sony")
                .slug("sony")
                .logoUrl(brandRequest.getLogoUrl())
                .description(brandRequest.getDescription())
                .build();
    }

    @Test
    @DisplayName("Tạo thương hiệu không truyền slug: Tự động sinh slug từ tên và lưu thành công")
    void createBrand_withoutSlug_shouldAutoGenerateSlugFromBrandName() {
        when(brandRepository.existsByNameIgnoreCase("Sony")).thenReturn(false);
        when(brandMapper.toDocument(brandRequest)).thenReturn(brand);
        when(brandRepository.existsBySlug("sony")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(brandMapper.toResponse(any(Brand.class))).thenReturn(brandResponse);

        BrandResponse result = brandService.createBrand(brandRequest);

        assertNotNull(result);
        assertEquals("Sony", result.getName());
        assertEquals("sony", result.getSlug());
        verify(brandRepository).save(any(Brand.class));
    }

    @Test
    @DisplayName("Tạo thương hiệu khi slug đã tồn tại: Tự động thêm hậu tố -1 để đảm bảo duy nhất")
    void createBrand_slugConflict_shouldAppendSuffix() {
        when(brandRepository.existsByNameIgnoreCase("Sony")).thenReturn(false);
        when(brandMapper.toDocument(brandRequest)).thenReturn(brand);
        when(brandRepository.existsBySlug("sony")).thenReturn(true);
        when(brandRepository.existsBySlug("sony-1")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(brandMapper.toResponse(any(Brand.class))).thenReturn(brandResponse);

        BrandResponse result = brandService.createBrand(brandRequest);

        assertNotNull(result);
        verify(brandRepository).save(argThat(b -> "sony-1".equals(b.getSlug())));
    }

    @Test
    @DisplayName("Tạo thương hiệu trùng tên: Ném AppException VALIDATION_ERROR")
    void createBrand_duplicateName_shouldThrowException() {
        when(brandRepository.existsByNameIgnoreCase("Sony")).thenReturn(true);

        assertThrows(AppException.class, () -> brandService.createBrand(brandRequest));
        verify(brandRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật thương hiệu không truyền slug: Giữ nguyên slug cũ nếu hợp lệ")
    void updateBrand_withoutSlug_shouldPreserveExistingSlug() {
        when(brandRepository.findById("brand-123")).thenReturn(Optional.of(brand));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("Sony", "brand-123")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(brandMapper.toResponse(any(Brand.class))).thenReturn(brandResponse);

        BrandResponse result = brandService.updateBrand("brand-123", brandRequest);

        assertNotNull(result);
        verify(brandRepository).save(argThat(b -> "sony".equals(b.getSlug())));
    }
}
