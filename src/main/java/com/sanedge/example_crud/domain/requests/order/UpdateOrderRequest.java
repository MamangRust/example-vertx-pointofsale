package com.sanedge.example_crud.domain.requests.order;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sanedge.example_crud.domain.requests.order_item.UpdateOrderItemRequest;

import lombok.Data;


@Data
public class UpdateOrderRequest {
    @JsonProperty("order_id")
    private Integer orderId;

    @JsonProperty("cashier_id")
    private Integer cashierId;

    @JsonProperty("items")
    private List<UpdateOrderItemRequest> items;
}