package com.ecommerce.common.constant;

public final class ApiRoutes {
    private ApiRoutes() {}

    public static final String API_V1 = "/api/v1";

    public static final class Auth {
        private Auth() {}
        public static final String BASE = API_V1 + "/auth";
        public static final String LOGIN = "/login";
        public static final String REGISTER = "/register";
        public static final String REFRESH = "/refresh-token";
        public static final String LOGOUT = "/logout";
    }

    public static final class Profile {
        private Profile() {}
        public static final String BASE = API_V1 + "/profile";
        public static final String ME = "/me";
        public static final String CHANGE_PASSWORD = "/change-password";
        public static final String ADDRESSES = "/addresses";
        public static final String ADDRESS_BY_ID = "/addresses/{addressId}";
        public static final String SET_DEFAULT_ADDRESS = "/addresses/{addressId}/default";
    }

    public static final class Products {
        private Products() {}
        public static final String PUBLIC_BASE = API_V1 + "/public/products";
        public static final String ADMIN_BASE = API_V1 + "/admin/products";
        public static final String BY_ID = "/{id}";
        public static final String SEARCH = "/search";
    }

    public static final class Categories {
        private Categories() {}
        public static final String PUBLIC_BASE = API_V1 + "/public/categories";
        public static final String ADMIN_BASE = API_V1 + "/admin/categories";
        public static final String BY_ID = "/{id}";
    }

    public static final class Brands {
        private Brands() {}
        public static final String PUBLIC_BASE = API_V1 + "/public/brands";
        public static final String ADMIN_BASE = API_V1 + "/admin/brands";
        public static final String BY_ID = "/{id}";
    }

    public static final class Cart {
        private Cart() {}
        public static final String BASE = API_V1 + "/cart";
        public static final String ITEM = "/items";
        public static final String ITEM_BY_PRODUCT_ID = "/items/{productId}";
        public static final String CLEAR = "/clear";
    }

    public static final class Orders {
        private Orders() {}
        public static final String CUSTOMER_BASE = API_V1 + "/orders";
        public static final String BY_ID = "/{id}";
        public static final String CANCEL = "/{id}/cancel";
        public static final String ADMIN_BASE = API_V1 + "/admin/orders";
        public static final String UPDATE_STATUS = "/{id}/status";
    }

    public static final class Media {
        private Media() {}
        public static final String BASE = API_V1 + "/media";
        public static final String UPLOAD = "/upload";
        public static final String UPLOAD_MULTIPLE = "/upload-multiple";
    }
}
