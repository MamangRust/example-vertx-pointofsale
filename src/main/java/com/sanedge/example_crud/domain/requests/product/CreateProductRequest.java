package com.sanedge.example_crud.domain.requests.product;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CreateProductRequest {
    @JsonProperty("merchant_id")
    private Integer merchantId;

    @JsonProperty("category_id")
    private Integer categoryId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("price")
    private Integer price;

    @JsonProperty("count_in_stock")
    private Integer countInStock;

    @JsonProperty("brand")
    private String brand;

    @JsonProperty("weight")
    private Integer weight;

    @JsonProperty("rating")
    private Integer rating;

    @JsonProperty("slug_product")
    private String slugProduct;

    @JsonProperty("image_product")
    private String imageProduct;
}   