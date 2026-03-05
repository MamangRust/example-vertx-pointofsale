package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.user.CreateUserRequest;
import com.sanedge.example_crud.domain.requests.user.FindAllUsers;
import com.sanedge.example_crud.domain.requests.user.UpdateUserRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.service.UserService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserHandler {
  private final UserService service;

  public void findAll(RoutingContext ctx) {
    FindAllUsers req = mapFindAllUsers(ctx);

    service.getAllUsers(req)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void findActive(RoutingContext ctx) {
    FindAllUsers req = mapFindAllUsers(ctx);

    service.getActiveUsers(req)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void findTrashed(RoutingContext ctx) {
    FindAllUsers req = mapFindAllUsers(ctx);

    service.getTrashedUsers(req)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void findById(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    service.getUserById(userId)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void create(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    CreateUserRequest register = CreateUserRequest.builder()
        .firstName(body.getString("firstname"))
        .lastName(body.getString("lastname"))
        .email(body.getString("email"))
        .password(body.getString("password"))
        .build();

    service.createUser(register)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void update(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    JsonObject body = ctx.body().asJsonObject();

    UpdateUserRequest updateUserRequest = UpdateUserRequest.builder()
        .userId(userId)
        .firstName(body.getString("firstname"))
        .lastName(body.getString("lastname"))
        .email(body.getString("email"))
        .password(body.getString("password"))
        .build();

    service.updateUser(updateUserRequest)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void trashed(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    service.trashed(userId)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void restore(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    service.restore(userId)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void deletePermanent(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    service.deletePermanent(userId)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void restoreAll(RoutingContext ctx) {
    service.restoreAll()
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void deleteAllPermanent(RoutingContext ctx) {
    service.deleteAllPermanent()
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  private void sendResponse(RoutingContext ctx, Object res) {
    ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(Json.encodePrettily(res));
  }

  private void sendErrorResponse(RoutingContext ctx, Throwable err) {
    ctx.response()
        .setStatusCode(500)
        .putHeader("Content-Type", "application/json")
        .end(Json.encodePrettily(ApiResponse.error(err.getMessage())));
  }

  private FindAllUsers mapFindAllUsers(RoutingContext ctx) {
    FindAllUsers req = new FindAllUsers();

    req.setSearch(ctx.queryParams().get("search"));
    req.setPage(
        ctx.queryParams().contains("page")
            ? Integer.parseInt(ctx.queryParams().get("page"))
            : 1);
    req.setPageSize(
        ctx.queryParams().contains("pageSize")
            ? Integer.parseInt(ctx.queryParams().get("pageSize"))
            : 10);

    return req;
  }
}
