package com.sanedge.example_crud.domain.requests.product;

import lombok.Data;

@Data
public class CreateProductRequest {
    private Integer merchantId;

    private Integer categoryId;

    private String name;

    private String description;

    private Integer price;

    private Integer countInStock;

    private String brand;

    private Integer weight;

    private Integer rating;

    private String slugProduct;

    private String imageProduct;
}