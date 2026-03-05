package com.sanedge.example_crud.domain.requests.category;

import lombok.Data;

@Data
public class MonthTotalPriceCategory {
    private Integer categoryId;

    private Integer year;

    private Integer month;
}
