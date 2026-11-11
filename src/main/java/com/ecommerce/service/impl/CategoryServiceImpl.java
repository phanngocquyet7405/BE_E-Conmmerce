package com.ecommerce.service.impl;

import com.ecommerce.document.Category;
import com.ecommerce.dto.request.CategoryRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.service.CategoryService;
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
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Cacheable(value = "categories", key = "'all'")
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Cacheable(value = "categories", key = "#id")
    public CategoryResponse getCategoryById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        return categoryMapper.toResponse(category);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (categoryRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên danh mục đã tồn tại");
        }

        Category category = categoryMapper.toDocument(request);
        category.setName(trimmedName);

        String slug = StringUtils.hasText(request.getSlug())
                ? SlugUtil.toSlug(request.getSlug())
                : SlugUtil.toSlug(trimmedName);

        if (!StringUtils.hasText(slug)) {
            slug = "category-" + System.currentTimeMillis();
        }

        // Tự động giải quyết trùng lặp slug (thêm hậu tố -1, -2, ...)
        String baseSlug = slug;
        int counter = 1;
        while (categoryRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        category.setSlug(slug);

        Category saved = categoryRepository.save(category);
        log.info("Đã tạo mới danh mục: id={}, name='{}', slug='{}'", saved.getId(), saved.getName(), saved.getSlug());
        return categoryMapper.toResponse(saved);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên danh mục đã tồn tại");
        }

        categoryMapper.updateDocumentFromRequest(request, category);
        category.setName(trimmedName);

        // Xử lý slug khi cập nhật
        if (StringUtils.hasText(request.getSlug())) {
            String newSlug = SlugUtil.toSlug(request.getSlug());
            String baseSlug = newSlug;
            int counter = 1;
            while (categoryRepository.existsBySlugAndIdNot(newSlug, id)) {
                newSlug = baseSlug + "-" + counter++;
            }
            category.setSlug(newSlug);
        } else if (!StringUtils.hasText(category.getSlug())) {
            String newSlug = SlugUtil.toSlug(trimmedName);
            String baseSlug = newSlug;
            int counter = 1;
            while (categoryRepository.existsBySlugAndIdNot(newSlug, id)) {
                newSlug = baseSlug + "-" + counter++;
            }
            category.setSlug(newSlug);
        }

        Category saved = categoryRepository.save(category);
        log.info("Đã cập nhật danh mục: id={}, name='{}', slug='{}'", saved.getId(), saved.getName(), saved.getSlug());
        return categoryMapper.toResponse(saved);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        categoryRepository.delete(category);
        log.info("Đã xóa danh mục: id={}, name='{}'", category.getId(), category.getName());
    }
}
