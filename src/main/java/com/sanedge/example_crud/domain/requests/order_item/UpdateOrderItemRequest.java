package com.sanedge.example_crud.domain.requests.order_item;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateOrderItemRequest {
    @JsonProperty("order_item_id")
    private Long orderItemId;
    
    @JsonProperty("product_id")
    private Long productId;
    
    @JsonProperty("quantity")
    private Integer quantity;
    
    @JsonProperty("price")
    private Integer price;
}
