package com.sanedge.example_crud.domain.requests.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CreateTransactionRequest {
    @JsonProperty("order_id") 
    private Integer orderID;

    @JsonProperty("merchant_id")
    private Integer merchantID;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("amount")
    private Integer amount;

    @JsonProperty("payment_status")
    private String paymentStatus;
}
