package com.sanedge.example_crud.domain.requests.order;


import lombok.Data;

@Data
public class MonthTotalRevenueMerchantRequest {
    private Integer merchantId;

    private Integer year;
    
    private Integer month;
}
