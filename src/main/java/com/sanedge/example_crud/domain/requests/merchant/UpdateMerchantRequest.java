package com.sanedge.example_crud.domain.requests.merchant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UpdateMerchantRequest {
    @JsonProperty("merchant_id")
    private Integer merchantId;

    @JsonProperty("user_id")
    private Integer userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("address")
    private String address;

    @JsonProperty("contact_email")
    private String contactEmail;

    @JsonProperty("contact_phone")
    private String contactPhone;

    @JsonProperty("status")
    private String status;
}