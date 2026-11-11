package com.ecommerce.service.impl;

import com.ecommerce.document.Brand;
import com.ecommerce.dto.request.BrandRequest;
import com.ecommerce.dto.response.BrandResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.mapper.BrandMapper;
import com.ecommerce.repository.BrandRepository;
import com.ecommerce.service.BrandService;
import com.ecommerce.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;

    @Override
    @Cacheable(value = "brands", key = "'all'")
    public List<BrandResponse> getAllBrands() {
        return brandRepository.findAll().stream()
                .map(brandMapper::toResponse)
                .toList();
    }

    @Override
    @Cacheable(value = "brands", key = "#id")
    public BrandResponse getBrandById(String id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
        return brandMapper.toResponse(brand);
    }

    @Override
    @CacheEvict(value = "brands", allEntries = true)
    public BrandResponse createBrand(BrandRequest request) {
        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (brandRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên thương hiệu đã tồn tại");
        }

        Brand brand = brandMapper.toDocument(request);
        brand.setName(trimmedName);

        String slug = StringUtils.hasText(request.getSlug())
                ? SlugUtil.toSlug(request.getSlug())
                : SlugUtil.toSlug(trimmedName);

        if (!StringUtils.hasText(slug)) {
            slug = "brand-" + System.currentTimeMillis();
        }

        // Tự động giải quyết trùng lặp slug (thêm hậu tố -1, -2, ...)
        String baseSlug = slug;
        int counter = 1;
        while (brandRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        brand.setSlug(slug);

        Brand saved = brandRepository.save(brand);
        log.info("Đã tạo mới thương hiệu: id={}, name='{}', slug='{}'", saved.getId(), saved.getName(), saved.getSlug());
        return brandMapper.toResponse(saved);
    }

    @Override
    @CacheEvict(value = "brands", allEntries = true)
    public BrandResponse updateBrand(String id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (brandRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên thương hiệu đã tồn tại");
        }

        brandMapper.updateDocumentFromRequest(request, brand);
        brand.setName(trimmedName);

        // Xử lý slug khi cập nhật
        if (StringUtils.hasText(request.getSlug())) {
            String newSlug = SlugUtil.toSlug(request.getSlug());
            String baseSlug = newSlug;
            int counter = 1;
            while (brandRepository.existsBySlugAndIdNot(newSlug, id)) {
                newSlug = baseSlug + "-" + counter++;
            }
            brand.setSlug(newSlug);
        } else if (!StringUtils.hasText(brand.getSlug())) {
            String newSlug = SlugUtil.toSlug(trimmedName);
            String baseSlug = newSlug;
            int counter = 1;
            while (brandRepository.existsBySlugAndIdNot(newSlug, id)) {
                newSlug = baseSlug + "-" + counter++;
            }
            brand.setSlug(newSlug);
        }

        Brand saved = brandRepository.save(brand);
        log.info("Đã cập nhật thương hiệu: id={}, name='{}', slug='{}'", saved.getId(), saved.getName(), saved.getSlug());
        return brandMapper.toResponse(saved);
    }

    @Override
    @CacheEvict(value = "brands", allEntries = true)
    public void deleteBrand(String id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
        brandRepository.delete(brand);
        log.info("Đã xóa thương hiệu: id={}, name='{}'", brand.getId(), brand.getName());
    }
}
