package com.sanedge.example_crud.domain.requests.transactions;

import lombok.Data;

@Data
public class YearMethodTransactionMerchantRequest {
    private Integer merchantId;

    private Integer year;
}
