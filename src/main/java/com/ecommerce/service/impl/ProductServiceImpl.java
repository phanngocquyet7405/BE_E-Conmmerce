package com.ecommerce.service.impl;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.Brand;
import com.ecommerce.document.Category;
import com.ecommerce.document.Product;
import com.ecommerce.dto.request.AdminProductCriteria;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.BrandRepository;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductMapper productMapper;
    private final MongoTemplate mongoTemplate;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "price", "stockQuantity", "stock_quantity", "createdAt", "created_at", "updatedAt"
    );

    public static String validateSortBy(String sortBy) {
        if (StringUtils.hasText(sortBy) && ALLOWED_SORT_FIELDS.contains(sortBy)) {
            return sortBy;
        }
        return "createdAt";
    }

    @Override
    public PageResponse<ProductResponse> getActiveProducts(Pageable pageable) {
        Page<ProductResponse> page = productRepository
                .findAllByActiveTrue(pageable)
                .map(productMapper::toResponse);
        return PageResponse.fromPage(page);
    }

    /**
     * Public API: Xem chi tiết sản phẩm. Nếu sản phẩm bị ẩn (active = false) thì trả 404 PRODUCT_NOT_FOUND.
     */
    @Override
    public ProductResponse getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!Boolean.TRUE.equals(product.getActive())) {
            log.info("Khách hàng truy cập sản phẩm đang bị ẩn: id={}", id);
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return productMapper.toResponse(product);
    }

    /**
     * Admin API: Xem chi tiết sản phẩm dành cho quản trị viên (kể cả sản phẩm đang bị ẩn).
     */
    @Override
    public ProductResponse getProductByIdForAdmin(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toResponse(product);
    }

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.getSku())) {
            throw new AppException(ErrorCode.PRODUCT_SKU_EXISTS);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        Product product = productMapper.toDocument(request);
        product.setCategoryName(category.getName());

        // Liên kết Thương hiệu (nếu có truyền brandId)
        if (StringUtils.hasText(request.getBrandId())) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
            product.setBrandId(brand.getId());
            product.setBrandName(brand.getName());
        }

        Product saved = productRepository.save(product);
        log.info("Đã tạo sản phẩm mới: id={}, sku={}, name={}", saved.getId(), saved.getSku(), saved.getName());
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSku().equalsIgnoreCase(request.getSku())
                && productRepository.existsBySkuIgnoreCase(request.getSku())) {
            throw new AppException(ErrorCode.PRODUCT_SKU_EXISTS);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        productMapper.updateDocumentFromRequest(request, product);
        product.setCategoryName(category.getName());

        // Cập nhật Thương hiệu
        if (StringUtils.hasText(request.getBrandId())) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
            product.setBrandId(brand.getId());
            product.setBrandName(brand.getName());
        } else {
            product.setBrandId(null);
            product.setBrandName(null);
        }

        Product saved = productRepository.save(product);
        log.info("Đã cập nhật sản phẩm: id={}, sku={}", saved.getId(), saved.getSku());
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse updateProductStatus(String id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setActive(active);
        Product saved = productRepository.save(product);
        log.info("Admin cập nhật trạng thái sản phẩm: id={}, active={}", id, active);
        return productMapper.toResponse(saved);
    }

    @Override
    public void deleteProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setActive(false);
        productRepository.save(product);
        log.info("Đã vô hiệu hóa sản phẩm: id={}", id);
    }

    @Override
    public PageResponse<ProductResponse> searchProducts(ProductSearchCriteria criteria) {
        Query query = new Query();
        query.addCriteria(Criteria.where("active").is(true));

        // 1. Tìm kiếm từ khóa theo tên, mô tả, SKU hoặc tên thương hiệu (Có escape Pattern.quote chống ReDoS)
        if (StringUtils.hasText(criteria.getKeyword())) {
            String safeRegex = ".*" + Pattern.quote(criteria.getKeyword().trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(safeRegex, "i"),
                    Criteria.where("description").regex(safeRegex, "i"),
                    Criteria.where("sku").regex(safeRegex, "i"),
                    Criteria.where("brand_name").regex(safeRegex, "i")
            ));
        }

        // 2. Lọc theo danh mục
        if (StringUtils.hasText(criteria.getCategoryId())) {
            query.addCriteria(Criteria.where("category_id").is(criteria.getCategoryId()));
        }

        // 3. Lọc theo thương hiệu
        if (StringUtils.hasText(criteria.getBrandId())) {
            query.addCriteria(Criteria.where("brand_id").is(criteria.getBrandId()));
        }

        // 4. Lọc theo khoảng giá
        if (criteria.getMinPrice() != null && criteria.getMaxPrice() != null) {
            query.addCriteria(Criteria.where("price").gte(criteria.getMinPrice()).lte(criteria.getMaxPrice()));
        } else if (criteria.getMinPrice() != null) {
            query.addCriteria(Criteria.where("price").gte(criteria.getMinPrice()));
        } else if (criteria.getMaxPrice() != null) {
            query.addCriteria(Criteria.where("price").lte(criteria.getMaxPrice()));
        }

        long totalElements = mongoTemplate.count(query, Product.class);

        // Giới hạn an toàn phân trang chống DoS RAM
        int validatedSize = Math.min(Math.max(criteria.getSize(), 1), 50);
        int validatedPage = Math.max(criteria.getPage(), 0);

        String safeSortBy = validateSortBy(criteria.getSortBy());
        Sort sort = "asc".equalsIgnoreCase(criteria.getSortDir())
                ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(validatedPage, validatedSize, sort);
        query.with(pageable);

        List<Product> products = mongoTemplate.find(query, Product.class);
        List<ProductResponse> responses = products.stream().map(productMapper::toResponse).toList();
        Page<ProductResponse> page = new PageImpl<>(responses, pageable, totalElements);

        return PageResponse.fromPage(page);
    }

    @Override
    public PageResponse<ProductResponse> getProductsForAdmin(AdminProductCriteria criteria) {
        Query query = new Query();

        // 1. Lọc theo active (nếu truyền; nếu không truyền thì lấy tất cả gồm cả sản phẩm ẩn)
        if (criteria.getActive() != null) {
            query.addCriteria(Criteria.where("active").is(criteria.getActive()));
        }

        // 2. Tìm kiếm từ khóa (name, sku, description, brand_name) với Pattern.quote
        if (StringUtils.hasText(criteria.getKeyword())) {
            String safeRegex = ".*" + Pattern.quote(criteria.getKeyword().trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(safeRegex, "i"),
                    Criteria.where("sku").regex(safeRegex, "i"),
                    Criteria.where("description").regex(safeRegex, "i"),
                    Criteria.where("brand_name").regex(safeRegex, "i")
            ));
        }

        // 3. Lọc theo danh mục
        if (StringUtils.hasText(criteria.getCategoryId())) {
            query.addCriteria(Criteria.where("category_id").is(criteria.getCategoryId()));
        }

        // 4. Lọc theo thương hiệu
        if (StringUtils.hasText(criteria.getBrandId())) {
            query.addCriteria(Criteria.where("brand_id").is(criteria.getBrandId()));
        }

        // 5. Lọc sản phẩm sắp hết hàng (lowStock)
        if (Boolean.TRUE.equals(criteria.getLowStock())) {
            int threshold = criteria.getLowStockThreshold() != null ? criteria.getLowStockThreshold() : 10;
            query.addCriteria(Criteria.where("stock_quantity").lte(threshold));
        }

        long totalElements = mongoTemplate.count(query, Product.class);

        int validatedSize = Math.min(Math.max(criteria.getSize(), 1), 50);
        int validatedPage = Math.max(criteria.getPage(), 0);

        String safeSortBy = validateSortBy(criteria.getSortBy());
        Sort sort = "asc".equalsIgnoreCase(criteria.getSortDir())
                ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(validatedPage, validatedSize, sort);
        query.with(pageable);

        List<Product> products = mongoTemplate.find(query, Product.class);
        List<ProductResponse> responses = products.stream().map(productMapper::toResponse).toList();
        Page<ProductResponse> page = new PageImpl<>(responses, pageable, totalElements);

        return PageResponse.fromPage(page);
    }
}
