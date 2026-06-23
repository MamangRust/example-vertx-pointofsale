package com.sanedge.example_crud.domain.requests.order;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sanedge.example_crud.domain.requests.order_item.CreateOrderItemRequest;

import lombok.Data;


@Data
public class CreateOrderRequest {
    @JsonProperty("merchant_id")
    private Integer merchantId;

    @JsonProperty("cashier_id")
    private Integer cashierId;

    @JsonProperty("items")
    private List<CreateOrderItemRequest> items;
}