package com.sanedge.example_crud.domain.requests.category;

import lombok.Data;

@Data
public class CreateCategoryRequest {
    private String name;

    private String description;

    private String slugCategory;
}
