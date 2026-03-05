package com.sanedge.example_crud.domain.requests.cashier;

import lombok.Data;

@Data
public class MonthTotalSalesMerchant {
    private Integer merchantId;

    private Integer year;

    private Integer month;
}
