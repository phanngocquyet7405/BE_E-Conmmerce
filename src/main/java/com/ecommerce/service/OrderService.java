package com.ecommerce.service;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.dto.request.AdminOrderCriteria;
import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.AdminOrderDetailResponse;
import com.ecommerce.dto.response.OrderResponse;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse checkout(String userId, CheckoutRequest request);
    OrderResponse getOrderById(String userId, String orderId);
    PageResponse<OrderResponse> getCustomerOrders(String userId, Pageable pageable);
    OrderResponse cancelOrder(String userId, String orderId);
    PageResponse<OrderResponse> getAllOrdersForAdmin(OrderStatus status, Pageable pageable);
    PageResponse<OrderResponse> getOrdersForAdmin(AdminOrderCriteria criteria);
    AdminOrderDetailResponse getOrderDetailForAdmin(String orderId);
    OrderResponse updateOrderStatus(String orderId, UpdateOrderStatusRequest request);
    OrderResponse confirmRefund(String orderId);
    int cancelExpiredOrders(int timeoutMinutes);
}
