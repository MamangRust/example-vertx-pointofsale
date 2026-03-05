package com.sanedge.example_crud.domain.requests.transactions;

import lombok.Data;

@Data
public class MonthMethodTransactionMerchantRequest {
    private Integer merchantId;

    private Integer year;

    private Integer month;
}
