package com.sanedge.example_crud.domain.response.api;

public record ApiResponsePagination<T>(
    String status,
    String message,
    T data,
    PaginationMeta pagination) {

  public static <T> ApiResponsePagination<T> success(
      String message,
      T data,
      PaginationMeta pagination) {
    return new ApiResponsePagination<>("success", message, data, pagination);
  }

  public static <T> ApiResponsePagination<T> success(String message) {
    return new ApiResponsePagination<>("success", message, null, null);
  }

  public static <T> ApiResponsePagination<T> error(String message) {
    return new ApiResponsePagination<>("error", message, null, null);
  }
}
