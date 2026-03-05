package com.sanedge.example_crud.domain.requests.product;

import lombok.Data;

@Data
public class FindAllProductByMerchantRequest {
    private Integer merchantId;

    private String search;

    private Integer categoryId;

    private Integer minPrice;

    private Integer maxPrice;

    private Integer page;
    
    private Integer pageSize;
}
