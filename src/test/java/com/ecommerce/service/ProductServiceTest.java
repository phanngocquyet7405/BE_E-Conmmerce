package com.ecommerce.service;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.Product;
import com.ecommerce.dto.request.AdminProductCriteria;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.BrandRepository;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product activeProduct;
    private Product inactiveProduct;
    private ProductResponse activeResponse;
    private ProductResponse inactiveResponse;

    @BeforeEach
    void setUp() {
        activeProduct = Product.builder()
                .id("prod-active")
                .name("Sản phẩm hiển thị")
                .sku("SKU-ACTIVE")
                .price(BigDecimal.valueOf(100000))
                .stockQuantity(15)
                .active(true)
                .build();

        inactiveProduct = Product.builder()
                .id("prod-inactive")
                .name("Sản phẩm ẩn")
                .sku("SKU-INACTIVE")
                .price(BigDecimal.valueOf(200000))
                .stockQuantity(5)
                .active(false)
                .build();

        activeResponse = ProductResponse.builder()
                .id("prod-active")
                .name("Sản phẩm hiển thị")
                .active(true)
                .build();

        inactiveResponse = ProductResponse.builder()
                .id("prod-inactive")
                .name("Sản phẩm ẩn")
                .active(false)
                .build();
    }

    @Test
    @DisplayName("Public getProductById: Sản phẩm active -> Trả về thông tin sản phẩm 200")
    void getProductById_WhenActive_ReturnsProduct() {
        when(productRepository.findById("prod-active")).thenReturn(Optional.of(activeProduct));
        when(productMapper.toResponse(activeProduct)).thenReturn(activeResponse);

        ProductResponse result = productService.getProductById("prod-active");

        assertNotNull(result);
        assertEquals("prod-active", result.getId());
        assertTrue(result.getActive());
    }

    @Test
    @DisplayName("Public getProductById: Sản phẩm inactive (active = false) -> Ném 404 PRODUCT_NOT_FOUND")
    void getProductById_WhenInactive_ThrowsNotFound() {
        when(productRepository.findById("prod-inactive")).thenReturn(Optional.of(inactiveProduct));

        AppException ex = assertThrows(AppException.class, () ->
                productService.getProductById("prod-inactive"));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
        verify(productMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("Admin getProductByIdForAdmin: Sản phẩm inactive -> Vẫn trả về thông tin cho Admin")
    void getProductByIdForAdmin_WhenInactive_ReturnsProduct() {
        when(productRepository.findById("prod-inactive")).thenReturn(Optional.of(inactiveProduct));
        when(productMapper.toResponse(inactiveProduct)).thenReturn(inactiveResponse);

        ProductResponse result = productService.getProductByIdForAdmin("prod-inactive");

        assertNotNull(result);
        assertEquals("prod-inactive", result.getId());
        assertFalse(result.getActive());
    }

    @Test
    @DisplayName("Admin updateProductStatus: Bật lại sản phẩm active=true thành công")
    void updateProductStatus_TogglesActive() {
        when(productRepository.findById("prod-inactive")).thenReturn(Optional.of(inactiveProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(productMapper.toResponse(any(Product.class))).thenReturn(activeResponse);

        ProductResponse result = productService.updateProductStatus("prod-inactive", true);

        assertNotNull(result);
        assertTrue(inactiveProduct.getActive());
        verify(productRepository, times(1)).save(inactiveProduct);
    }

    @Test
    @DisplayName("Admin getProductsForAdmin: Lọc lowStock và phân trang thành công")
    void getProductsForAdmin_WithLowStock_Success() {
        AdminProductCriteria criteria = AdminProductCriteria.builder()
                .lowStock(true)
                .lowStockThreshold(10)
                .page(0)
                .size(15)
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Product.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(inactiveProduct));
        when(productMapper.toResponse(inactiveProduct)).thenReturn(inactiveResponse);

        PageResponse<ProductResponse> response = productService.getProductsForAdmin(criteria);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("prod-inactive", response.getItems().get(0).getId());
    }

    @Test
    @DisplayName("Whitelist sortBy: Giá trị không hợp lệ tự động fallback về 'createdAt'")
    void validateSortBy_InvalidField_DefaultsToCreatedAt() {
        assertEquals("createdAt", ProductServiceImpl.validateSortBy("malicious_field"));
        assertEquals("createdAt", ProductServiceImpl.validateSortBy("password"));
        assertEquals("price", ProductServiceImpl.validateSortBy("price"));
        assertEquals("stockQuantity", ProductServiceImpl.validateSortBy("stockQuantity"));
    }
}
