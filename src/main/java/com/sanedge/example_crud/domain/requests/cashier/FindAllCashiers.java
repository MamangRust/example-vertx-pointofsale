package com.sanedge.example_crud.domain.requests.cashier;

import lombok.Data;

@Data
public class FindAllCashiers {
    private String search;

    private Integer page;

    private Integer pageSize;
}
