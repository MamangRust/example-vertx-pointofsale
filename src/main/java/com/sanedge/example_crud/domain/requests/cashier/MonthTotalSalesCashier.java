package com.sanedge.example_crud.domain.requests.cashier;

import lombok.Data;

@Data
public class MonthTotalSalesCashier {
    private Integer cashierId;

    private Integer year;

    private Integer month;
}
