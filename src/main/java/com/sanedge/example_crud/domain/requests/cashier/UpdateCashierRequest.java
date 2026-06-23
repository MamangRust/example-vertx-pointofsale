package com.sanedge.example_crud.domain.requests.cashier;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UpdateCashierRequest {
    @JsonProperty("cashier_id")
    private Integer cashierId;

    @JsonProperty("name")
    private String name;
}
