package com.ecommerce.dto.request;

import com.ecommerce.document.AddressLabel;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    @NotBlank(message = "Tên người nhận không được để trống")
    @Size(min = 2, max = 100, message = "Tên người nhận phải từ 2 đến 100 ký tự")
    private String recipientName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)(3|5|7|8|9)\\d{8}$", message = "Số điện thoại không đúng định dạng di động Việt Nam")
    private String phone;

    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    @Size(max = 100, message = "Tỉnh/Thành phố tối đa 100 ký tự")
    private String province;

    @Size(max = 100, message = "Quận/Huyện tối đa 100 ký tự")
    private String district;

    @NotBlank(message = "Phường/Xã không được để trống")
    @Size(max = 100, message = "Phường/Xã tối đa 100 ký tự")
    private String ward;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
    private String streetAddress;

    private AddressLabel label;

    @Size(max = 200, message = "Ghi chú tối đa 200 ký tự")
    private String note;

    private Double latitude;

    private Double longitude;

    @Size(max = 20, message = "Mã tỉnh/thành phố tối đa 20 ký tự")
    private String provinceCode;

    @Size(max = 20, message = "Mã quận/huyện tối đa 20 ký tự")
    private String districtCode;

    @Size(max = 20, message = "Mã phường/xã tối đa 20 ký tự")
    private String wardCode;

    @JsonProperty("isDefault")
    @JsonAlias({"default", "is_default"})
    private boolean isDefault;

    @JsonProperty("isDefault")
    public boolean isDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault")
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    @AssertTrue(message = "Tọa độ GPS không hợp lệ: Vĩ độ [-90, 90] và kinh độ [-180, 180] phải cùng được cung cấp hoặc cùng để trống")
    public boolean isValidGps() {
        if (latitude == null && longitude == null) {
            return true;
        }
        if (latitude == null || longitude == null) {
            return false;
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return false;
        }
        return latitude >= -90.0 && latitude <= 90.0 && longitude >= -180.0 && longitude <= 180.0;
    }
}
