package com.ecommerce.service;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    PageResponse<ProductResponse> getActiveProducts(Pageable pageable);
    ProductResponse getProductById(String id);
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProduct(String id, ProductRequest request);
    void deleteProduct(String id);
    PageResponse<ProductResponse> searchProducts(ProductSearchCriteria criteria);
}
