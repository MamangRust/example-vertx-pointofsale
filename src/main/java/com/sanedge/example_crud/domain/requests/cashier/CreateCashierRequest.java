package com.sanedge.example_crud.domain.requests.cashier;

import lombok.Data;

@Data
public class CreateCashierRequest {
    private Integer merchantId;

    private Integer userId;

    private String name;
}
