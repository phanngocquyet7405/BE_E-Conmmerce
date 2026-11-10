package com.ecommerce.dto;

import com.ecommerce.document.Address;
import com.ecommerce.document.AddressLabel;
import com.ecommerce.dto.request.AddressRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressJsonMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("1. Đọc JSON {isDefault: true} vào AddressRequest thành công")
    void readingIsDefault_Succeeds() throws Exception {
        AddressRequest req = objectMapper.readValue("{\"isDefault\": true}", AddressRequest.class);
        assertTrue(req.isDefault());
    }

    @Test
    @DisplayName("2. Đọc JSON {default: true} vào AddressRequest thành công qua alias")
    void readingDefault_Succeeds() throws Exception {
        AddressRequest req = objectMapper.readValue("{\"default\": true}", AddressRequest.class);
        assertTrue(req.isDefault());
    }

    @Test
    @DisplayName("3. Đọc JSON {is_default: true} vào AddressRequest thành công qua alias snake_case")
    void readingIsDefaultSnakeCase_Succeeds() throws Exception {
        AddressRequest req = objectMapper.readValue("{\"is_default\": true}", AddressRequest.class);
        assertTrue(req.isDefault());
    }

    @Test
    @DisplayName("4. Serialize Address ra JSON sinh khóa 'isDefault' đồng nhất")
    void serializingAddress_OutputsKeyIsDefault() throws Exception {
        Address address = Address.builder()
                .id("addr-1")
                .recipientName("Nguyen Van A")
                .label(AddressLabel.HOME)
                .note("Giao gio hanh chinh")
                .latitude(10.7769)
                .longitude(106.7009)
                .isDefault(true)
                .build();
        String json = objectMapper.writeValueAsString(address);
        assertTrue(json.contains("\"isDefault\":true"));
        assertTrue(json.contains("\"label\":\"HOME\""));
        assertTrue(json.contains("\"note\":\"Giao gio hanh chinh\""));
        assertTrue(json.contains("\"latitude\":10.7769"));
        assertTrue(json.contains("\"longitude\":106.7009"));
    }

    @Test
    @DisplayName("5. Đọc JSON đầy đủ các trường mới vào AddressRequest thành công")
    void readingAllNewFields_Succeeds() throws Exception {
        String json = """
                {
                    "recipientName": "Trần Thị B",
                    "phone": "0987654321",
                    "province": "TP Hồ Chí Minh",
                    "district": "Quận 1",
                    "ward": "Phường Bến Nghé",
                    "streetAddress": "123 Lê Duẩn",
                    "label": "OFFICE",
                    "note": "Tòa nhà Mplaza lầu 5",
                    "latitude": 10.7828,
                    "longitude": 106.7001,
                    "provinceCode": "79",
                    "districtCode": "760",
                    "wardCode": "26734",
                    "isDefault": true
                }
                """;
        AddressRequest req = objectMapper.readValue(json, AddressRequest.class);
        assertEquals("Trần Thị B", req.getRecipientName());
        assertEquals("0987654321", req.getPhone());
        assertEquals(AddressLabel.OFFICE, req.getLabel());
        assertEquals("Tòa nhà Mplaza lầu 5", req.getNote());
        assertEquals(10.7828, req.getLatitude());
        assertEquals(106.7001, req.getLongitude());
        assertEquals("79", req.getProvinceCode());
        assertEquals("760", req.getDistrictCode());
        assertEquals("26734", req.getWardCode());
        assertTrue(req.isDefault());
        assertTrue(req.isValidGps());
    }
}
