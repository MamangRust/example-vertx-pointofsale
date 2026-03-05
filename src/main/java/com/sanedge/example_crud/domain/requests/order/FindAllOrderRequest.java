package com.sanedge.example_crud.domain.requests.order;

import lombok.Data;

@Data
public class FindAllOrderRequest {
    private String search;

    private Integer page = 1;

    private Integer pageSize = 10;
}