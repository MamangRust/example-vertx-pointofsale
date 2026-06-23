package com.sanedge.example_crud.domain.requests.role;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateRoleRequest {
  @JsonProperty("name")
  private String name;
}