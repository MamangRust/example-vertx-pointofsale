package com.sanedge.example_crud.domain.response.transaction;

import com.sanedge.example_crud.model.transaction.TransactionYearlyAmountSuccess;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyAmountSuccessResponse {
    private String year;
    private Integer totalSuccess;
    private Long totalAmount;

    public static TransactionYearlyAmountSuccessResponse from(TransactionYearlyAmountSuccess entity) {
        return TransactionYearlyAmountSuccessResponse.builder()
                .year(entity.getYear())
                .totalSuccess(entity.getTotalSuccess())
                .totalAmount(entity.getTotalAmount())
                .build();
    }
}