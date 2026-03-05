package com.sanedge.example_crud.domain.requests.cashier;


import lombok.Data;

@Data
public class UpdateCashierRequest {
    private Integer cashierId;

    private String name;
}
