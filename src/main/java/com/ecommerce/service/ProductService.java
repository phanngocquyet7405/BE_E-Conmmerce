package com.ecommerce.service;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.request.AdminProductCriteria;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    PageResponse<ProductResponse> getActiveProducts(Pageable pageable);
    ProductResponse getProductById(String id);
    ProductResponse getProductByIdForAdmin(String id);
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProduct(String id, ProductRequest request);
    ProductResponse updateProductStatus(String id, boolean active);
    void deleteProduct(String id);
    PageResponse<ProductResponse> searchProducts(ProductSearchCriteria criteria);
    PageResponse<ProductResponse> getProductsForAdmin(AdminProductCriteria criteria);
}
