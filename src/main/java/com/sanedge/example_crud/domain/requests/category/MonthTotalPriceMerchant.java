package com.sanedge.example_crud.domain.requests.category;

import lombok.Data;

@Data
public class MonthTotalPriceMerchant {
    private Integer merchantId;

    private Integer year;

    private Integer month;
}
