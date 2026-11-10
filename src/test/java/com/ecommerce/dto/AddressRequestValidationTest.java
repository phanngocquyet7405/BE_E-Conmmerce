package com.ecommerce.dto;

import com.ecommerce.dto.request.AddressRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AddressRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private AddressRequest.AddressRequestBuilder validBuilder() {
        return AddressRequest.builder()
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .province("Hà Nội")
                .district("Cầu Giấy")
                .ward("Dịch Vọng")
                .streetAddress("Số 1 Duy Tân")
                .isDefault(true);
    }

    @Test
    @DisplayName("Dữ liệu chuẩn hợp lệ không có lỗi validation")
    void validAddressRequest_NoViolations() {
        AddressRequest req = validBuilder().build();
        Set<ConstraintViolation<AddressRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Dữ liệu hợp lệ không được có lỗi");
    }

    @Test
    @DisplayName("district để trống hoặc null vẫn hợp lệ (S6)")
    void optionalDistrict_IsValidWhenNullOrBlank() {
        AddressRequest reqNullDistrict = validBuilder().district(null).build();
        assertTrue(validator.validate(reqNullDistrict).isEmpty());

        AddressRequest reqEmptyDistrict = validBuilder().district("").build();
        assertTrue(validator.validate(reqEmptyDistrict).isEmpty());
    }

    @Test
    @DisplayName("Tên người nhận ngắn hơn 2 ký tự hoặc dài hơn 100 ký tự: Bị lỗi")
    void recipientName_LengthViolation() {
        AddressRequest reqShort = validBuilder().recipientName("A").build();
        assertFalse(validator.validate(reqShort).isEmpty());

        AddressRequest reqLong = validBuilder().recipientName("A".repeat(101)).build();
        assertFalse(validator.validate(reqLong).isEmpty());
    }

    @Test
    @DisplayName("Số điện thoại không đúng định dạng di động Việt Nam: Bị lỗi")
    void phone_InvalidFormatViolation() {
        String[] invalidPhones = {
                "12345678",
                "0123456789", // Đầu 01 cũ
                "02438888888", // Điện thoại bàn
                "+12025550199", // Số Mỹ
                "098765432",   // Thiếu số (9 số)
                "098765432101",// Thừa số (12 số)
                "phone_number"
        };
        for (String phone : invalidPhones) {
            AddressRequest req = validBuilder().phone(phone).build();
            Set<ConstraintViolation<AddressRequest>> violations = validator.validate(req);
            assertFalse(violations.isEmpty(), "Số điện thoại '" + phone + "' phải bị báo lỗi");
        }
    }

    @Test
    @DisplayName("Số điện thoại đúng chuẩn di động Việt Nam (0x hoặc +84x): Hợp lệ")
    void phone_ValidFormatAccepted() {
        String[] validPhones = {
                "0987654321",
                "+84987654321",
                "0321234567",
                "0561234567",
                "0771234567",
                "0881234567",
                "0901234567",
                "+84321234567"
        };
        for (String phone : validPhones) {
            AddressRequest req = validBuilder().phone(phone).build();
            Set<ConstraintViolation<AddressRequest>> violations = validator.validate(req);
            assertTrue(violations.isEmpty(), "Số điện thoại '" + phone + "' phải hợp lệ");
        }
    }

    @Test
    @DisplayName("Tọa độ GPS chỉ truyền 1 trong 2 trường: Bị lỗi validation")
    void gps_OnlyOneCoordinateProvided_Violation() {
        AddressRequest reqOnlyLat = validBuilder().latitude(10.7769).longitude(null).build();
        Set<ConstraintViolation<AddressRequest>> v1 = validator.validate(reqOnlyLat);
        assertFalse(v1.isEmpty());

        AddressRequest reqOnlyLng = validBuilder().latitude(null).longitude(106.7009).build();
        Set<ConstraintViolation<AddressRequest>> v2 = validator.validate(reqOnlyLng);
        assertFalse(v2.isEmpty());
    }

    @Test
    @DisplayName("Tọa độ GPS ngoài phạm vi [-90, 90] hoặc [-180, 180]: Bị lỗi validation")
    void gps_OutOfRange_Violation() {
        AddressRequest reqLatOut = validBuilder().latitude(91.0).longitude(100.0).build();
        assertFalse(validator.validate(reqLatOut).isEmpty());

        AddressRequest reqLngOut = validBuilder().latitude(10.0).longitude(181.0).build();
        assertFalse(validator.validate(reqLngOut).isEmpty());
    }

    @Test
    @DisplayName("Ghi chú dài hơn 200 ký tự: Bị lỗi validation")
    void note_Exceeds200Chars_Violation() {
        AddressRequest reqLongNote = validBuilder().note("N".repeat(201)).build();
        assertFalse(validator.validate(reqLongNote).isEmpty());

        AddressRequest reqValidNote = validBuilder().note("N".repeat(200)).build();
        assertTrue(validator.validate(reqValidNote).isEmpty());
    }
}
