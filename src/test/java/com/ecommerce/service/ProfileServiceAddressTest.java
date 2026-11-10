package com.ecommerce.service;

import com.ecommerce.document.Address;
import com.ecommerce.document.AddressLabel;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.AddressRequest;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceAddressTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    private ProfileServiceImpl profileService;

    private User testUser;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(
                userRepository,
                passwordEncoder,
                redisTokenService,
                emailService,
                passwordPolicyService
        );
        profileService.setMaxAddressesPerUser(10);

        testUser = User.builder()
                .id("user-addr-1")
                .email("user1@example.com")
                .fullName("Nguyễn Văn A")
                .addresses(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("1. Thêm địa chỉ đầu tiên: Tự động đặt làm mặc định dù request gửi isDefault = false")
    void addAddress_FirstAddress_AutoSetToDefault() {
        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressRequest request = AddressRequest.builder()
                .recipientName("  Nguyễn Văn A  ")
                .phone("0987654321")
                .province("  Hà Nội  ")
                .district("  Cầu Giấy  ")
                .ward("  Dịch Vọng  ")
                .streetAddress("  Số 1 Duy Tân  ")
                .isDefault(false)
                .build();

        Address created = profileService.addAddress("user-addr-1", request);

        assertNotNull(created);
        assertTrue(created.isDefault(), "Địa chỉ đầu tiên bắt buộc phải là mặc định");
        assertEquals("Nguyễn Văn A", created.getRecipientName(), "Cần trim khoảng trắng");
        assertEquals("Hà Nội", created.getProvince());
        assertEquals("Cầu Giấy", created.getDistrict());
        assertEquals("Số 1 Duy Tân", created.getStreetAddress());
        assertEquals(1, testUser.getAddresses().size());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("2. Thêm địa chỉ thứ 2 làm mặc định: Địa chỉ cũ bị hủy trạng thái mặc định")
    void addAddress_SecondAddressAsDefault_UnsetsPreviousDefault() {
        Address first = Address.builder()
                .id("addr-1")
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .province("Hà Nội")
                .district("Cầu Giấy")
                .ward("Dịch Vọng")
                .streetAddress("Số 1 Duy Tân")
                .isDefault(true)
                .build();
        testUser.getAddresses().add(first);

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressRequest secondRequest = AddressRequest.builder()
                .recipientName("Trần Thị B")
                .phone("0912345678")
                .province("TP Hồ Chí Minh")
                .district("Quận 1")
                .ward("Bến Nghé")
                .streetAddress("72 Lê Thánh Tôn")
                .isDefault(true)
                .build();

        Address second = profileService.addAddress("user-addr-1", secondRequest);

        assertTrue(second.isDefault());
        assertFalse(first.isDefault(), "Địa chỉ thứ nhất phải bị chuyển thành false");
        assertEquals(2, testUser.getAddresses().size());
    }

    @Test
    @DisplayName("3. Thêm địa chỉ thứ 2 không làm mặc định: Địa chỉ đầu vẫn giữ mặc định")
    void addAddress_SecondAddressNotDefault_KeepsFirstAsDefault() {
        Address first = Address.builder()
                .id("addr-1")
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .province("Hà Nội")
                .district("Cầu Giấy")
                .ward("Dịch Vọng")
                .streetAddress("Số 1 Duy Tân")
                .isDefault(true)
                .build();
        testUser.getAddresses().add(first);

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressRequest secondRequest = AddressRequest.builder()
                .recipientName("Trần Thị B")
                .phone("0912345678")
                .province("TP Hồ Chí Minh")
                .district("Quận 1")
                .ward("Bến Nghé")
                .streetAddress("72 Lê Thánh Tôn")
                .isDefault(false)
                .build();

        Address second = profileService.addAddress("user-addr-1", secondRequest);

        assertFalse(second.isDefault());
        assertTrue(first.isDefault(), "Địa chỉ thứ nhất vẫn phải giữ mặc định");
    }

    @Test
    @DisplayName("4. Thêm địa chỉ vượt quá giới hạn tối đa: Ném ADDRESS_LIMIT_EXCEEDED")
    void addAddress_ExceedsMaxLimit_ThrowsAddressLimitExceeded() {
        profileService.setMaxAddressesPerUser(2);
        testUser.getAddresses().add(Address.builder().id("a1").isDefault(true).build());
        testUser.getAddresses().add(Address.builder().id("a2").isDefault(false).build());

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));

        AddressRequest request = AddressRequest.builder()
                .recipientName("Lê Văn C")
                .phone("0933333333")
                .province("Đà Nẵng")
                .ward("Hải Châu 1")
                .streetAddress("123 Bạch Đằng")
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                profileService.addAddress("user-addr-1", request));

        assertEquals(ErrorCode.ADDRESS_LIMIT_EXCEEDED, ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("5. Thêm địa chỉ với tọa độ GPS không hợp lệ: Ném VALIDATION_ERROR")
    void addAddress_InvalidGps_ThrowsValidationException() {
        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));

        // Trường hợp chỉ truyền latitude mà không truyền longitude
        AddressRequest requestOnlyLat = AddressRequest.builder()
                .recipientName("Lê Văn C")
                .phone("0933333333")
                .province("Đà Nẵng")
                .ward("Hải Châu 1")
                .streetAddress("123 Bạch Đằng")
                .latitude(16.0544)
                .longitude(null)
                .build();

        AppException ex1 = assertThrows(AppException.class, () ->
                profileService.addAddress("user-addr-1", requestOnlyLat));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex1.getErrorCode());

        // Trường hợp latitude vượt quá 90 độ
        AddressRequest requestOutRange = AddressRequest.builder()
                .recipientName("Lê Văn C")
                .phone("0933333333")
                .province("Đà Nẵng")
                .ward("Hải Châu 1")
                .streetAddress("123 Bạch Đằng")
                .latitude(95.0)
                .longitude(108.0)
                .build();

        AppException ex2 = assertThrows(AppException.class, () ->
                profileService.addAddress("user-addr-1", requestOutRange));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex2.getErrorCode());
    }

    @Test
    @DisplayName("6. Cập nhật địa chỉ thành công với đầy đủ các trường mới")
    void updateAddress_Success_UpdatesAllFields() {
        Address old = Address.builder()
                .id("addr-update-1")
                .recipientName("Cũ")
                .phone("0900000000")
                .province("Cũ")
                .ward("Cũ")
                .streetAddress("Cũ")
                .isDefault(true)
                .build();
        testUser.getAddresses().add(old);

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressRequest request = AddressRequest.builder()
                .recipientName("  Nguyễn Văn Mới  ")
                .phone("0988888888")
                .province("Hà Nội")
                .district("Ba Đình")
                .ward("Kim Mã")
                .streetAddress("Kim Mã Thượng")
                .label(AddressLabel.OFFICE)
                .note("  Gọi trước khi đến  ")
                .latitude(21.0333)
                .longitude(105.8167)
                .provinceCode("01")
                .districtCode("001")
                .wardCode("00010")
                .isDefault(true)
                .build();

        Address updated = profileService.updateAddress("user-addr-1", "addr-update-1", request);

        assertEquals("Nguyễn Văn Mới", updated.getRecipientName());
        assertEquals("0988888888", updated.getPhone());
        assertEquals(AddressLabel.OFFICE, updated.getLabel());
        assertEquals("Gọi trước khi đến", updated.getNote());
        assertEquals(21.0333, updated.getLatitude());
        assertEquals(105.8167, updated.getLongitude());
        assertEquals("01", updated.getProvinceCode());
        assertEquals("001", updated.getDistrictCode());
        assertEquals("00010", updated.getWardCode());
        assertTrue(updated.isDefault());
    }

    @Test
    @DisplayName("7. Cập nhật địa chỉ không tồn tại: Ném ADDRESS_NOT_FOUND")
    void updateAddress_NonExistentAddress_ThrowsAddressNotFound() {
        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));

        AddressRequest request = AddressRequest.builder()
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .province("Hà Nội")
                .ward("Dịch Vọng")
                .streetAddress("Số 1 Duy Tân")
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                profileService.updateAddress("user-addr-1", "non-existent-id", request));

        assertEquals(ErrorCode.ADDRESS_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("8. Cập nhật địa chỉ duy nhất với isDefault = false: Vẫn giữ mặc định do bất biến")
    void updateAddress_OnlyDefaultAddress_CannotUnsetDefault() {
        Address single = Address.builder()
                .id("addr-single")
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .province("Hà Nội")
                .ward("Dịch Vọng")
                .streetAddress("Số 1 Duy Tân")
                .isDefault(true)
                .build();
        testUser.getAddresses().add(single);

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressRequest request = AddressRequest.builder()
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .province("Hà Nội")
                .ward("Dịch Vọng")
                .streetAddress("Số 1 Duy Tân")
                .isDefault(false)
                .build();

        Address updated = profileService.updateAddress("user-addr-1", "addr-single", request);

        assertTrue(updated.isDefault(), "Địa chỉ duy nhất bắt buộc phải giữ là mặc định");
    }

    @Test
    @DisplayName("9. Xóa địa chỉ thành công: Loại bỏ khỏi danh sách")
    void deleteAddress_Success_RemovesAddress() {
        Address a1 = Address.builder().id("addr-1").isDefault(true).build();
        Address a2 = Address.builder().id("addr-2").isDefault(false).build();
        testUser.getAddresses().add(a1);
        testUser.getAddresses().add(a2);

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        profileService.deleteAddress("user-addr-1", "addr-2");

        assertEquals(1, testUser.getAddresses().size());
        assertEquals("addr-1", testUser.getAddresses().get(0).getId());
        assertTrue(testUser.getAddresses().get(0).isDefault());
    }

    @Test
    @DisplayName("10. Xóa địa chỉ không tồn tại: Ném ADDRESS_NOT_FOUND (không nuốt lỗi)")
    void deleteAddress_NonExistentAddress_ThrowsAddressNotFound() {
        Address a1 = Address.builder().id("addr-1").isDefault(true).build();
        testUser.getAddresses().add(a1);

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));

        AppException ex = assertThrows(AppException.class, () ->
                profileService.deleteAddress("user-addr-1", "non-existent-id"));

        assertEquals(ErrorCode.ADDRESS_NOT_FOUND, ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("11. Xóa địa chỉ mặc định khi còn địa chỉ khác: Tự động thăng hạng địa chỉ còn lại")
    void deleteAddress_WhenDefaultDeleted_PromotesRemainingAddressToDefault() {
        Address defaultAddr = Address.builder().id("addr-default").isDefault(true).build();
        Address otherAddr = Address.builder().id("addr-other").isDefault(false).build();
        testUser.getAddresses().add(defaultAddr);
        testUser.getAddresses().add(otherAddr);

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        profileService.deleteAddress("user-addr-1", "addr-default");

        assertEquals(1, testUser.getAddresses().size());
        assertEquals("addr-other", testUser.getAddresses().get(0).getId());
        assertTrue(testUser.getAddresses().get(0).isDefault(), "Địa chỉ còn lại phải được thăng hạng làm mặc định");
    }

    @Test
    @DisplayName("12. Xóa địa chỉ khi danh sách null: Ném ADDRESS_NOT_FOUND an toàn không NPE")
    void deleteAddress_NullAddresses_ThrowsAddressNotFound() {
        testUser.setAddresses(null);
        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));

        AppException ex = assertThrows(AppException.class, () ->
                profileService.deleteAddress("user-addr-1", "addr-1"));

        assertEquals(ErrorCode.ADDRESS_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("13. Đặt địa chỉ mặc định thành công: Target thành true, các địa chỉ khác thành false")
    void setDefaultAddress_Success_SetsTargetAndUnsetsOthers() {
        Address a1 = Address.builder().id("addr-1").isDefault(true).build();
        Address a2 = Address.builder().id("addr-2").isDefault(false).build();
        testUser.getAddresses().add(a1);
        testUser.getAddresses().add(a2);

        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        profileService.setDefaultAddress("user-addr-1", "addr-2");

        assertFalse(a1.isDefault());
        assertTrue(a2.isDefault());
    }

    @Test
    @DisplayName("14. Đặt địa chỉ mặc định cho ID không tồn tại: Ném ADDRESS_NOT_FOUND")
    void setDefaultAddress_NonExistent_ThrowsAddressNotFound() {
        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));

        AppException ex = assertThrows(AppException.class, () ->
                profileService.setDefaultAddress("user-addr-1", "non-existent-id"));

        assertEquals(ErrorCode.ADDRESS_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("15. Lấy danh sách địa chỉ khi addresses = null: Trả về danh sách rỗng an toàn")
    void getAddresses_NullList_ReturnsEmptyList() {
        testUser.setAddresses(null);
        when(userRepository.findById("user-addr-1")).thenReturn(Optional.of(testUser));

        List<Address> result = profileService.getAddresses("user-addr-1");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
