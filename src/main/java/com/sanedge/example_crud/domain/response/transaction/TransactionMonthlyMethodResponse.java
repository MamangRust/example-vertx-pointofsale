package com.sanedge.example_crud.domain.response.transaction;

import com.sanedge.example_crud.model.transaction.TransactionMonthlyMethod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionMonthlyMethodResponse {
    private String month;
    private String paymentMethod;
    private Integer totalTransactions;
    private Long totalAmount;

    public static TransactionMonthlyMethodResponse from(TransactionMonthlyMethod entity) {
        return TransactionMonthlyMethodResponse.builder()
                .month(entity.getMonth())
                .paymentMethod(entity.getPaymentMethod())
                .totalTransactions(entity.getTotalTransactions())
                .totalAmount(entity.getTotalAmount())
                .build();
    }
}
