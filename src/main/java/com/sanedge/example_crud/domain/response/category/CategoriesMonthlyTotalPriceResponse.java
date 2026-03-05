package com.sanedge.example_crud.domain.response.category;

import java.util.List;

import com.sanedge.example_crud.model.category.CategoryMonthTotalPrice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriesMonthlyTotalPriceResponse {
    private String year;
    private String month;
    private Long totalRevenue;

    public static CategoriesMonthlyTotalPriceResponse from(CategoryMonthTotalPrice response) {
        return CategoriesMonthlyTotalPriceResponse.builder()
                .year(response.getYear())
                .month(response.getMonth())
                .totalRevenue((long) response.getTotalRevenue())
                .build();
    }

    public static List<CategoriesMonthlyTotalPriceResponse> fromList(
            List<CategoryMonthTotalPrice> response) {
        if (response == null)
            return List.of();
        return response.stream().map(CategoriesMonthlyTotalPriceResponse::from).toList();
    }
}
