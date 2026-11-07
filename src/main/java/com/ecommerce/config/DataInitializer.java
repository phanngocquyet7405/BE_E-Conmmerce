package com.ecommerce.config;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.document.Brand;
import com.ecommerce.document.Category;
import com.ecommerce.document.Product;
import com.ecommerce.document.User;
import com.ecommerce.repository.BrandRepository;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String adminInitialPassword;

    @Value("${CUSTOMER_INITIAL_PASSWORD:}")
    private String customerInitialPassword;

    @Override
    public void run(String... args) {
        try {
            initUsers();
            initCategoriesAndBrands();
        } catch (Exception e) {
            log.warn("Lưu ý: Chưa thể kết nối tới CSDL MongoDB để khởi tạo dữ liệu mẫu (dev). Dữ liệu sẽ tự động tạo khi kết nối CSDL thành công. Chi tiết: {}", e.getMessage());
        }
    }

    private void initUsers() {
        if (!userRepository.existsByEmail("admin@ecommerce.com")) {
            String passwordToUse = StringUtils.hasText(adminInitialPassword)
                    ? adminInitialPassword.trim()
                    : generateSecureRandomPassword();

            User admin = User.builder()
                    .email("admin@ecommerce.com")
                    .password(passwordEncoder.encode(passwordToUse))
                    .fullName("Quản Trị Viên")
                    .phone("0988888888")
                    .roles(Set.of(RoleEnum.ROLE_ADMIN))
                    .active(true)
                    .emailVerified(true)
                    .build();
            userRepository.save(admin);

            log.warn("=================================================================");
            log.warn("=> [SECURITY ALERT] TÀI KHOẢN ADMIN ĐÃ ĐƯỢC KHỞI TẠO:");
            log.warn("   Email: admin@ecommerce.com");
            log.warn("   Mật khẩu khởi tạo: [{}]", passwordToUse);
            log.warn("   Vui lòng lưu lại và đổi mật khẩu ngay sau lần đăng nhập đầu tiên!");
            log.warn("=================================================================");
        }

        if (!userRepository.existsByEmail("customer@ecommerce.com")) {
            String customerPasswordToUse = StringUtils.hasText(customerInitialPassword)
                    ? customerInitialPassword.trim()
                    : generateSecureRandomPassword();

            User customer = User.builder()
                    .email("customer@ecommerce.com")
                    .password(passwordEncoder.encode(customerPasswordToUse))
                    .fullName("Khách Hàng Mẫu")
                    .phone("0977777777")
                    .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                    .active(true)
                    .emailVerified(true)
                    .build();
            userRepository.save(customer);

            log.info("=> [DEV] Khởi tạo tài khoản Khách hàng mẫu: customer@ecommerce.com với mật khẩu ngẫu nhiên: [{}]",
                    customerPasswordToUse);
        }
    }

    private String generateSecureRandomPassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void initCategoriesAndBrands() {
        if (categoryRepository.count() == 0) {
            Category electronics = categoryRepository.save(Category.builder()
                    .name("Điện Tử & Công Nghệ")
                    .slug("dien-tu-cong-nghe")
                    .description("Điện thoại, Laptop, Máy tính bảng, Phụ kiện")
                    .build());

            Category fashion = categoryRepository.save(Category.builder()
                    .name("Thời Trang & Phụ Kiện")
                    .slug("thoi-trang-phu-kien")
                    .description("Quần áo, Giày dép, Túi xách thời trang")
                    .build());

            Brand apple = brandRepository.save(Brand.builder()
                    .name("Apple")
                    .slug("apple")
                    .description("Thương hiệu công nghệ hàng đầu thế giới")
                    .logoUrl("http://localhost:8080/uploads/images/apple-logo.png")
                    .build());

            Brand samsung = brandRepository.save(Brand.builder()
                    .name("Samsung")
                    .slug("samsung")
                    .description("Tập đoàn điện tử công nghệ Samsung")
                    .logoUrl("http://localhost:8080/uploads/images/samsung-logo.png")
                    .build());

            Brand nike = brandRepository.save(Brand.builder()
                    .name("Nike")
                    .slug("nike")
                    .description("Thương hiệu thời trang thể thao Nike")
                    .logoUrl("http://localhost:8080/uploads/images/nike-logo.png")
                    .build());

            if (productRepository.count() == 0) {
                productRepository.save(Product.builder()
                        .sku("IP15PM-256")
                        .name("iPhone 15 Pro Max 256GB")
                        .slug("iphone-15-pro-max-256gb")
                        .description("Điện thoại thông minh cao cấp từ Apple với khung Titanium và chip A17 Pro mạnh mẽ.")
                        .price(BigDecimal.valueOf(29990000))
                        .stockQuantity(50)
                        .thumbnailUrl("http://localhost:8080/uploads/images/iphone-15-thumb.jpg")
                        .images(List.of(
                                "http://localhost:8080/uploads/images/iphone-15-front.jpg",
                                "http://localhost:8080/uploads/images/iphone-15-back.jpg"
                        ))
                        .categoryId(electronics.getId())
                        .categoryName(electronics.getName())
                        .brandId(apple.getId())
                        .brandName(apple.getName())
                        .active(true)
                        .build());

                productRepository.save(Product.builder()
                        .sku("SGS24U-512")
                        .name("Samsung Galaxy S24 Ultra 512GB")
                        .slug("samsung-galaxy-s24-ultra-512gb")
                        .description("Flagship đỉnh cao tích hợp Galaxy AI và camera 200MP đột phá.")
                        .price(BigDecimal.valueOf(31490000))
                        .stockQuantity(30)
                        .thumbnailUrl("http://localhost:8080/uploads/images/s24-ultra-thumb.jpg")
                        .images(List.of(
                                "http://localhost:8080/uploads/images/s24-ultra-1.jpg"
                        ))
                        .categoryId(electronics.getId())
                        .categoryName(electronics.getName())
                        .brandId(samsung.getId())
                        .brandName(samsung.getName())
                        .active(true)
                        .build());
            }

            log.info("=> [DEV] Khởi tạo thành công Danh mục, Thương hiệu và Sản phẩm mẫu ban đầu.");
        }
    }
}
