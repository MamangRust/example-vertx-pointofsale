package com.sanedge.example_crud.domain.requests.category;

import lombok.Data;

@Data
public class UpdateCategoryRequest {
    private Integer categoryId;

    private String name;

    private String description;

    private String slugCategory;
}
