package com.sanedge.example_crud.domain.response.cashier;

import com.sanedge.example_crud.model.cashier.CashierYearTotalSales;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashierResponseYearTotalSales {
    private String year;
    private Long totalSales;

    public static CashierResponseYearTotalSales from(CashierYearTotalSales entity) {
        return CashierResponseYearTotalSales.builder()
                .year(entity.getYear())
                .totalSales(entity.getTotalSales())
                .build();
    }
}
