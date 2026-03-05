package com.sanedge.example_crud.domain.response.order;

import java.util.List;

import com.sanedge.example_crud.model.order.OrderMonthTotalRevenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMonthlyTotalRevenueResponse {
    private String year;
    private String month;
    private Long totalRevenue;

    public static OrderMonthlyTotalRevenueResponse from(OrderMonthTotalRevenue response) {
        return OrderMonthlyTotalRevenueResponse.builder()
                .year(response.getYear())
                .month(response.getMonth())
                .totalRevenue((long) response.getTotalRevenue())
                .build();
    }

    public static List<OrderMonthlyTotalRevenueResponse> fromList(List<OrderMonthTotalRevenue> response) {
        if (response == null)
            return List.of();
        return response.stream().map(OrderMonthlyTotalRevenueResponse::from).toList();
    }
}