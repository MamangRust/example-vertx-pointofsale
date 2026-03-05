package com.sanedge.example_crud.model.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderYearTotalRevenue {
    private String year;
    private Integer totalRevenue;
}
