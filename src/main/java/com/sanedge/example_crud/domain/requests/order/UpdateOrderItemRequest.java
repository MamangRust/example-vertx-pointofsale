package com.sanedge.example_crud.domain.requests.order;


import lombok.Data;

@Data
public class UpdateOrderItemRequest {
    private Integer orderItemId;
    private Integer productId;

    private Integer quantity;

    private Integer price;
}