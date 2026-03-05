package com.sanedge.example_crud.domain.requests.order;

import lombok.Data;

@Data
public class MonthOrderMerchantRequest {
    private Integer merchantId;

    private Integer year;
}
