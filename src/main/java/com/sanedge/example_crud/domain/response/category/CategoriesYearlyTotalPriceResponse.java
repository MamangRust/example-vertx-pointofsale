package com.sanedge.example_crud.domain.response.category;

import java.util.List;

import com.sanedge.example_crud.model.category.CategoryYearTotalPrice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriesYearlyTotalPriceResponse {
    private String year;
    private Long totalRevenue;

    public static CategoriesYearlyTotalPriceResponse from(CategoryYearTotalPrice response) {
        return CategoriesYearlyTotalPriceResponse.builder()
                .year(response.getYear())
                .totalRevenue((long) response.getTotalRevenue())
                .build();
    }

    public static List<CategoriesYearlyTotalPriceResponse> fromList(List<CategoryYearTotalPrice> response) {
        if (response == null)
            return List.of();
        return response.stream().map(CategoriesYearlyTotalPriceResponse::from).toList();
    }
}