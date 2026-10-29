package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.Cart.BASE)
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCart(userDetails.getId())));
    }

    @PostMapping(ApiRoutes.Cart.ITEM)
    public ResponseEntity<ApiResponse<CartResponse>> addItemToCart(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã thêm vào giỏ hàng", cartService.addItemToCart(userDetails.getId(), request)));
    }

    @PutMapping(ApiRoutes.Cart.ITEM)
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật số lượng", cartService.updateItemQuantity(userDetails.getId(), request)));
    }

    @DeleteMapping(ApiRoutes.Cart.ITEM_BY_PRODUCT_ID)
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa khỏi giỏ hàng", cartService.removeItemFromCart(userDetails.getId(), productId)));
    }

    @DeleteMapping(ApiRoutes.Cart.CLEAR)
    public ResponseEntity<ApiResponse<Void>> clearCart(@AuthenticationPrincipal CustomUserDetails userDetails) {
        cartService.clearCart(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.ok("Đã dọn sạch giỏ hàng", null));
    }
}
