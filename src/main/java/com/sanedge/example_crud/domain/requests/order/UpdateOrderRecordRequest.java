package com.sanedge.example_crud.domain.requests.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateOrderRecordRequest {
    private Long orderId;

    private Integer totalPrice;
}