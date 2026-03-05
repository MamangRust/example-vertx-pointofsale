package com.sanedge.example_crud.domain.requests.order;


import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    private Integer merchantId;

    private Integer cashierId;

    private List<CreateOrderItemRequest> items;

}