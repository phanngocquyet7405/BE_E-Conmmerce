package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 1. PUBLIC ROUTER: Khách hàng xem danh sách sản phẩm
    @GetMapping(ApiRoutes.Products.PUBLIC_BASE)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getActiveProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(productService.getActiveProducts(pageable)));
    }

    // 2. PUBLIC ROUTER: Tìm kiếm nâng cao & bộ lọc (kèm lọc thương hiệu)
    @GetMapping(ApiRoutes.Products.PUBLIC_BASE + ApiRoutes.Products.SEARCH)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setKeyword(keyword);
        criteria.setCategoryId(categoryId);
        criteria.setBrandId(brandId);
        criteria.setMinPrice(minPrice);
        criteria.setMaxPrice(maxPrice);
        criteria.setPage(page);
        criteria.setSize(size);
        criteria.setSortBy(sortBy);
        criteria.setSortDir(sortDir);

        return ResponseEntity.ok(ApiResponse.ok(productService.searchProducts(criteria)));
    }

    // 3. PUBLIC ROUTER: Xem chi tiết sản phẩm
    @GetMapping(ApiRoutes.Products.PUBLIC_BASE + ApiRoutes.Products.BY_ID)
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
    }

    // 4. ADMIN ROUTER: Chủ cửa hàng tạo sản phẩm
    @PostMapping(ApiRoutes.Products.ADMIN_BASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo sản phẩm thành công", response));
    }

    // 5. ADMIN ROUTER: Chủ cửa hàng cập nhật sản phẩm
    @PutMapping(ApiRoutes.Products.ADMIN_BASE + ApiRoutes.Products.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật sản phẩm thành công", response));
    }

    // 6. ADMIN ROUTER: Chủ cửa hàng xóa sản phẩm (soft delete)
    @DeleteMapping(ApiRoutes.Products.ADMIN_BASE + ApiRoutes.Products.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã vô hiệu hóa sản phẩm", null));
    }
}
