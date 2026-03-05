package com.sanedge.example_crud.domain.requests.transactions;

import lombok.Data;

@Data
public class FindAllTransactionRequest {
    private String search;

    private Integer page = 1;
    
    private Integer pageSize = 10;
}