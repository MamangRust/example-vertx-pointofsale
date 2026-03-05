package com.sanedge.example_crud.domain.requests.product;

import lombok.Data;

@Data
public class FindAllProductByCategoryRequest {
    private String categoryName;

    private String search;

    private Integer minPrice;

    private Integer maxPrice;

    private Integer page;

    private Integer pageSize;
}