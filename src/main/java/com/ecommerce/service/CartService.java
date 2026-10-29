package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartResponse;

public interface CartService {
    CartResponse getCart(String userId);
    CartResponse addItemToCart(String userId, CartItemRequest request);
    CartResponse updateItemQuantity(String userId, CartItemRequest request);
    CartResponse removeItemFromCart(String userId, String productId);
    void clearCart(String userId);
}
