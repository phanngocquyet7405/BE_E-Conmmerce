package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.dto.request.CategoryRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // 1. PUBLIC: Khách hàng xem danh sách danh mục
    @GetMapping(ApiRoutes.Categories.PUBLIC_BASE)
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAllCategories()));
    }

    // 2. PUBLIC: Xem chi tiết danh mục
    @GetMapping(ApiRoutes.Categories.PUBLIC_BASE + ApiRoutes.Categories.BY_ID)
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getCategoryById(id)));
    }

    // 3. ADMIN: Tạo mới danh mục
    @PostMapping(ApiRoutes.Categories.ADMIN_BASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo danh mục thành công", response));
    }

    // 4. ADMIN: Cập nhật danh mục
    @PutMapping(ApiRoutes.Categories.ADMIN_BASE + ApiRoutes.Categories.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật danh mục thành công", categoryService.updateCategory(id, request)));
    }

    // 5. ADMIN: Xóa danh mục
    @DeleteMapping(ApiRoutes.Categories.ADMIN_BASE + ApiRoutes.Categories.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa danh mục", null));
    }
}
