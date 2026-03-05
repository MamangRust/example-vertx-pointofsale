package com.sanedge.example_crud.domain.response.category;

import java.util.List;

import com.sanedge.example_crud.model.category.CategoryYearPrice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriesYearPriceResponse {
    private String year;
    private Integer categoryId;
    private String categoryName;
    private Integer orderCount;
    private Integer itemsSold;
    private Long totalRevenue;
    private Integer uniqueProductsSold;

    public static CategoriesYearPriceResponse from(CategoryYearPrice response) {
        return CategoriesYearPriceResponse.builder()
                .year(response.getYear())
                .categoryId(response.getCategoryId())
                .categoryName(response.getCategoryName())
                .orderCount(response.getOrderCount())
                .itemsSold(response.getItemsSold())
                .totalRevenue((long) response.getTotalRevenue())
                .uniqueProductsSold(response.getUniqueProductsSold())
                .build();
    }

    public static List<CategoriesYearPriceResponse> fromList(List<CategoryYearPrice> response) {
        if (response == null)
            return List.of();
        return response.stream().map(CategoriesYearPriceResponse::from).toList();
    }
}