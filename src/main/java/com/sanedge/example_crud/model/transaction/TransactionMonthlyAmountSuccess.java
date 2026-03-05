package com.sanedge.example_crud.model.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonthlyAmountSuccess {
    private String year;
    private String month;
    private Integer totalSuccess;
    private Long totalAmount;
}