package com.sanedge.example_crud.domain.requests.cashier;

import lombok.Data;

@Data
public class YearCashierIdRequest {
    private Integer cashierId;

    private Integer year;
}