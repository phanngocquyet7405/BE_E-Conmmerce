package com.ecommerce.service.impl;

import com.ecommerce.document.Cart;
import com.ecommerce.document.CartItem;
import com.ecommerce.document.Product;
import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartItemResponse;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Override
    public CartResponse getCart(String userId) {
        Cart cart = getOrCreateCart(userId);
        return mapToCartResponse(cart);
    }

    @Override
    public CartResponse addItemToCart(String userId, CartItemRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getActive()) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm hiện đang ngừng kinh doanh");
        }

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }

        Cart cart = getOrCreateCart(userId);
        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            if (product.getStockQuantity() < newQuantity) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }
            existingItem.setQuantity(newQuantity);
            existingItem.setUnitPrice(product.getPrice());
            existingItem.setThumbnailUrl(product.getThumbnailUrl());
            existingItem.setBrandName(product.getBrandName());
        } else {
            CartItem newItem = CartItem.builder()
                    .productId(product.getId())
                    .sku(product.getSku())
                    .productName(product.getName())
                    .thumbnailUrl(product.getThumbnailUrl())
                    .unitPrice(product.getPrice())
                    .quantity(request.getQuantity())
                    .categoryName(product.getCategoryName())
                    .brandName(product.getBrandName())
                    .build();
            cart.getItems().add(newItem);
        }

        cartRepository.save(cart);
        return mapToCartResponse(cart);
    }

    @Override
    public CartResponse updateItemQuantity(String userId, CartItemRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }

        Cart cart = getOrCreateCart(userId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        item.setQuantity(request.getQuantity());
        item.setUnitPrice(product.getPrice());
        item.setThumbnailUrl(product.getThumbnailUrl());
        item.setBrandName(product.getBrandName());

        cartRepository.save(cart);
        return mapToCartResponse(cart);
    }

    @Override
    public CartResponse removeItemFromCart(String userId, String productId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        cartRepository.save(cart);
        return mapToCartResponse(cart);
    }

    @Override
    public void clearCart(String userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Cart getOrCreateCart(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .userId(userId)
                        .items(new ArrayList<>())
                        .build()));
    }

    private CartResponse mapToCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(i -> CartItemResponse.builder()
                        .productId(i.getProductId())
                        .sku(i.getSku())
                        .productName(i.getProductName())
                        .thumbnailUrl(i.getThumbnailUrl())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .subTotal(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .categoryName(i.getCategoryName())
                        .brandName(i.getBrandName())
                        .build())
                .toList();

        int totalCount = cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .items(itemResponses)
                .totalItems(totalCount)
                .totalAmount(cart.getTotalAmount())
                .build();
    }
}
