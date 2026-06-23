package com.sanedge.example_crud.domain.requests.cashier;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CreateCashierRequest {
    @JsonProperty("merchant_id")
    private Integer merchantId;

    @JsonProperty("user_id")
    private Integer userId;

    @JsonProperty("name")
    private String name;
}