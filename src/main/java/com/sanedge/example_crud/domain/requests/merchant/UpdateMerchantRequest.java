package com.sanedge.example_crud.domain.requests.merchant;

import lombok.Data;

@Data
public class UpdateMerchantRequest {
    private Integer merchantId;

    private Integer userId;

    private String name;

    private String description;

    private String address;

    private String contactEmail;

    private String contactPhone;

    private String status;
}
