package com.sanedge.example_crud.domain.response.cashier;

import com.sanedge.example_crud.model.cashier.CashierMonthSales;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashierResponseMonthSales {
    private String month;
    private Integer cashierId;
    private String cashierName;
    private Integer orderCount;
    private Long totalSales;

    public static CashierResponseMonthSales from(CashierMonthSales entity) {
        return CashierResponseMonthSales.builder()
                .month(entity.getMonth())
                .cashierId(entity.getCashierId())
                .cashierName(entity.getCashierName())
                .orderCount(entity.getOrderCount())
                .totalSales(entity.getTotalSales())
                .build();
    }
}
