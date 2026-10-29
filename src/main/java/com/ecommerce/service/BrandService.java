package com.ecommerce.service;

import com.ecommerce.dto.request.BrandRequest;
import com.ecommerce.dto.response.BrandResponse;

import java.util.List;

public interface BrandService {
    List<BrandResponse> getAllBrands();
    BrandResponse getBrandById(String id);
    BrandResponse createBrand(BrandRequest request);
    BrandResponse updateBrand(String id, BrandRequest request);
    void deleteBrand(String id);
}
