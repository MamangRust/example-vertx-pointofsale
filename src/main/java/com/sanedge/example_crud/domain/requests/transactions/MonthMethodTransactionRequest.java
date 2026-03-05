package com.sanedge.example_crud.domain.requests.transactions;

import lombok.Data;

@Data
public class MonthMethodTransactionRequest {
    private Integer year;
    
    private Integer month;
}
