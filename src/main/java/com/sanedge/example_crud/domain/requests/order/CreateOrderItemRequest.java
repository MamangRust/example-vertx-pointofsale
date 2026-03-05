package com.sanedge.example_crud.domain.requests.order;

import lombok.Data;

@Data
public class CreateOrderItemRequest {
    private Integer productId;

    private Integer quantity;

    private Integer price;
}