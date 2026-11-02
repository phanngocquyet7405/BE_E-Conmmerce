package com.ecommerce.service.impl;

import com.ecommerce.document.Brand;
import com.ecommerce.dto.request.BrandRequest;
import com.ecommerce.dto.response.BrandResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.mapper.BrandMapper;
import com.ecommerce.repository.BrandRepository;
import com.ecommerce.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;

    @Override
    public List<BrandResponse> getAllBrands() {
        return brandRepository.findAll().stream()
                .map(brandMapper::toResponse)
                .toList();
    }

    @Override
    public BrandResponse getBrandById(String id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
        return brandMapper.toResponse(brand);
    }

    @Override
    public BrandResponse createBrand(BrandRequest request) {
        if (brandRepository.existsByName(request.getName())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên thương hiệu đã tồn tại");
        }

        Brand brand = brandMapper.toDocument(request);
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Override
    public BrandResponse updateBrand(String id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));

        brandMapper.updateDocumentFromRequest(request, brand);
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Override
    public void deleteBrand(String id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
        brandRepository.delete(brand);
    }
}
