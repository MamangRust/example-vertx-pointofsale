package com.sanedge.example_crud.domain.requests.transactions;

import lombok.Data;

@Data
public class MonthAmountTransactionMerchant {
    private Integer merchantId;

    private Integer year;

    private Integer month;
}
