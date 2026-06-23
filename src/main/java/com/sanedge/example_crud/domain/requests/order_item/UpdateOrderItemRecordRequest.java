package com.sanedge.example_crud.domain.requests.order_item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateOrderItemRecordRequest {
    private Long orderItemId;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private Integer price;
}