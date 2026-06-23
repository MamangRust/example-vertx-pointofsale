package com.sanedge.example_crud.domain.requests.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderRecordRequest {
    private Long merchantId;

    private Long cashierId;

    private Integer totalPrice;
}