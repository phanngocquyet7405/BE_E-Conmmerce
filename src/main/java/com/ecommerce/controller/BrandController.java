package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.dto.request.BrandRequest;
import com.ecommerce.dto.response.BrandResponse;
import com.ecommerce.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    // 1. PUBLIC: Khách hàng xem danh sách thương hiệu
    @GetMapping(ApiRoutes.Brands.PUBLIC_BASE)
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrands() {
        return ResponseEntity.ok(ApiResponse.ok(brandService.getAllBrands()));
    }

    // 2. PUBLIC: Xem chi tiết thương hiệu
    @GetMapping(ApiRoutes.Brands.PUBLIC_BASE + ApiRoutes.Brands.BY_ID)
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(brandService.getBrandById(id)));
    }

    // 3. ADMIN: Tạo mới thương hiệu
    @PostMapping(ApiRoutes.Brands.ADMIN_BASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo thương hiệu thành công", response));
    }

    // 4. ADMIN: Cập nhật thương hiệu
    @PutMapping(ApiRoutes.Brands.ADMIN_BASE + ApiRoutes.Brands.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable String id,
            @Valid @RequestBody BrandRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thương hiệu thành công", brandService.updateBrand(id, request)));
    }

    // 5. ADMIN: Xóa thương hiệu
    @DeleteMapping(ApiRoutes.Brands.ADMIN_BASE + ApiRoutes.Brands.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable String id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa thương hiệu", null));
    }
}
