package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.request.AdminProductCriteria;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.request.UpdateProductStatusRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.impl.ProductServiceImpl;
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

    // 1. PUBLIC ROUTER: Khách hàng xem danh sách sản phẩm active
    @GetMapping(ApiRoutes.Products.PUBLIC_BASE)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getActiveProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        int validatedPage = Math.max(page, 0);
        int validatedSize = Math.min(Math.max(size, 1), 50);

        String safeSortBy = ProductServiceImpl.validateSortBy(sortBy);
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(validatedPage, validatedSize, sort);
        return ResponseEntity.ok(ApiResponse.ok(productService.getActiveProducts(pageable)));
    }

    // 2. PUBLIC ROUTER: Tìm kiếm nâng cao & bộ lọc
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

        int validatedPage = Math.max(page, 0);
        int validatedSize = Math.min(Math.max(size, 1), 50);

        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setKeyword(keyword);
        criteria.setCategoryId(categoryId);
        criteria.setBrandId(brandId);
        criteria.setMinPrice(minPrice);
        criteria.setMaxPrice(maxPrice);
        criteria.setPage(validatedPage);
        criteria.setSize(validatedSize);
        criteria.setSortBy(ProductServiceImpl.validateSortBy(sortBy));
        criteria.setSortDir(sortDir);

        return ResponseEntity.ok(ApiResponse.ok(productService.searchProducts(criteria)));
    }

    // 3. PUBLIC ROUTER: Xem chi tiết sản phẩm (Trả 404 nếu sản phẩm bị ẩn)
    @GetMapping(ApiRoutes.Products.PUBLIC_BASE + ApiRoutes.Products.BY_ID)
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
    }

    // 4. ADMIN ROUTER: Danh sách sản phẩm dành cho quản trị viên (gồm cả sản phẩm ẩn, lọc lowStock)
    @GetMapping(ApiRoutes.Products.ADMIN_BASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProductsForAdmin(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(defaultValue = "10") Integer lowStockThreshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        AdminProductCriteria criteria = AdminProductCriteria.builder()
                .keyword(keyword)
                .active(active)
                .categoryId(categoryId)
                .brandId(brandId)
                .lowStock(lowStock)
                .lowStockThreshold(lowStockThreshold)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(productService.getProductsForAdmin(criteria)));
    }

    // 5. ADMIN ROUTER: Xem chi tiết sản phẩm kể cả sản phẩm ẩn
    @GetMapping(ApiRoutes.Products.ADMIN_BASE + ApiRoutes.Products.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductByIdForAdmin(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductByIdForAdmin(id)));
    }

    // 6. ADMIN ROUTER: Bật/tắt trạng thái hiển thị của sản phẩm (active)
    @PatchMapping(ApiRoutes.Products.ADMIN_BASE + "/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProductStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái sản phẩm thành công",
                productService.updateProductStatus(id, request.getActive())));
    }

    // 7. ADMIN ROUTER: Tạo sản phẩm mới
    @PostMapping(ApiRoutes.Products.ADMIN_BASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo sản phẩm thành công", response));
    }

    // 8. ADMIN ROUTER: Cập nhật sản phẩm
    @PutMapping(ApiRoutes.Products.ADMIN_BASE + ApiRoutes.Products.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật sản phẩm thành công", response));
    }

    // 9. ADMIN ROUTER: Vô hiệu hóa sản phẩm (soft delete)
    @DeleteMapping(ApiRoutes.Products.ADMIN_BASE + ApiRoutes.Products.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã vô hiệu hóa sản phẩm", null));
    }
}
