package com.sanedge.example_crud.domain.requests.category;

import lombok.Data;

@Data
public class FindAllCategory {
    private String search;

    private Integer page;

    private Integer pageSize;
}
