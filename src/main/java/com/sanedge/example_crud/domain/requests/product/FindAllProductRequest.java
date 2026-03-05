package com.sanedge.example_crud.domain.requests.product;


import lombok.Data;

@Data
public class FindAllProductRequest {
    private String search;

    private Integer page;

    private Integer pageSize;
}