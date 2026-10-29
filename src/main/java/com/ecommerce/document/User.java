package com.ecommerce.document;

import com.ecommerce.common.constant.RoleEnum;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Document(collection = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;

    @Field("full_name")
    private String fullName;

    private String phone;

    @Field("avatar_url")
    private String avatarUrl;

    @Field("roles")
    private Set<RoleEnum> roles;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    @Field("addresses")
    private List<Address> addresses = new ArrayList<>();

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
