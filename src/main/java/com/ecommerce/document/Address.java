package com.ecommerce.document;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {
    private String id;
    private String recipientName;
    private String phone;
    private String province;
    private String district;
    private String ward;
    private String streetAddress;

    private AddressLabel label;
    private String note;
    private Double latitude;
    private Double longitude;
    private String provinceCode;
    private String districtCode;
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
}
