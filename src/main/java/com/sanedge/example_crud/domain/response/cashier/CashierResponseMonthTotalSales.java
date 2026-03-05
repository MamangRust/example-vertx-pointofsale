package com.sanedge.example_crud.domain.response.cashier;

import com.sanedge.example_crud.model.cashier.CashierMonthTotalSales;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashierResponseMonthTotalSales {
    private String year;
    private String month;
    private Long totalSales;

    public static CashierResponseMonthTotalSales from(CashierMonthTotalSales entity) {
        return CashierResponseMonthTotalSales.builder()
                .year(entity.getYear())
                .month(entity.getMonth())
                .totalSales(entity.getTotalSales())
                .build();
    }
}
