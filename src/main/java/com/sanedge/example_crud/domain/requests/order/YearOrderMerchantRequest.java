package com.sanedge.example_crud.domain.requests.order;

import lombok.Data;

@Data
public class YearOrderMerchantRequest {
    private Integer merchantId;

    private Integer year;
}