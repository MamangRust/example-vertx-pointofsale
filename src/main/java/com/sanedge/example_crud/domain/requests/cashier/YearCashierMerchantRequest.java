package com.sanedge.example_crud.domain.requests.cashier;

import lombok.Data;

@Data
public class YearCashierMerchantRequest {
    private Integer merchantId;

    private Integer year;
}