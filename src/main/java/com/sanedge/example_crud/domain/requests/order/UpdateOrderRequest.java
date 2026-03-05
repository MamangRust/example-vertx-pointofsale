package com.sanedge.example_crud.domain.requests.order;

import java.util.List;

import lombok.Data;

@Data
public class UpdateOrderRequest {
    private Integer orderId;

    private Integer cashierId;

    private List<UpdateOrderItemRequest> items;
}