package com.sanedge.example_crud.domain.requests.transactions;


import lombok.Data;

@Data
public class UpdateTransactionRequest {
    private Integer transactionID;

    private Integer orderID;

    private Integer merchantID;

    private String paymentMethod;

    private Integer amount;

    private String paymentStatus;
}
