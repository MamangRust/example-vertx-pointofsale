package com.sanedge.example_crud.domain.requests.merchant;

import lombok.Data;

@Data
public class FindAllMerchants {
    private String search;

    private Integer page;

    private Integer pageSize;
}
