package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.user.CreateUserRequest;
import com.sanedge.example_crud.domain.requests.user.FindAllUsers;
import com.sanedge.example_crud.domain.requests.user.UpdateUserRequest;
import com.sanedge.example_crud.exception.BadRequestException;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.exception.UnauthorizedException;
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
        .onSuccess(resp -> sendJsonResponse(ctx, 200, resp))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void findActive(RoutingContext ctx) {
    FindAllUsers req = mapFindAllUsers(ctx);
    service.getActiveUsers(req)
        .onSuccess(resp -> sendJsonResponse(ctx, 200, resp))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void findTrashed(RoutingContext ctx) {
    FindAllUsers req = mapFindAllUsers(ctx);
    service.getTrashedUsers(req)
        .onSuccess(resp -> sendJsonResponse(ctx, 200, resp))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void findById(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    service.getUserById(userId)
        .onSuccess(resp -> sendJsonResponse(ctx, 200, resp))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void create(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      handleFailure(ctx, new BadRequestException("Request body cannot be empty"));
      return;
    }
    CreateUserRequest register = CreateUserRequest.builder()
        .firstName(body.getString("firstname"))
        .lastName(body.getString("lastname"))
        .email(body.getString("email"))
        .password(body.getString("password"))
        .build();

    service.createUser(register)
        .onSuccess(created -> sendJsonResponse(ctx, 201, created))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void update(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      handleFailure(ctx, new BadRequestException("Request body cannot be empty"));
      return;
    }

    UpdateUserRequest updateUserRequest = UpdateUserRequest.builder()
        .userId(userId)
        .firstName(body.getString("firstname"))
        .lastName(body.getString("lastname"))
        .email(body.getString("email"))
        .password(body.getString("password"))
        .build();

    service.updateUser(updateUserRequest)
        .onSuccess(resp -> sendJsonResponse(ctx, 200, resp))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void trashed(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    service.trashed(userId)
        .onSuccess(resp -> sendJsonResponse(ctx, 200, resp))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void restore(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    service.restore(userId)
        .onSuccess(resp -> sendJsonResponse(ctx, 200, resp))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void deletePermanent(RoutingContext ctx) {
    Integer userId = Integer.parseInt(ctx.pathParam("id"));
    service.deletePermanent(userId)
        .onSuccess(v -> sendJsonResponse(ctx, 200, v))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void restoreAllUsers(RoutingContext ctx) {
    service.restoreAll()
        .onSuccess(res -> sendJsonResponse(ctx, 200, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void deleteAllPermanentUsers(RoutingContext ctx) {
    service.deleteAllPermanent()
        .onSuccess(res -> sendJsonResponse(ctx, 200, res))
        .onFailure(err -> handleFailure(ctx, err));
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

  private void sendJsonResponse(RoutingContext ctx, int statusCode, Object result) {
    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("Content-Type", "application/json")
        .end(Json.encode(result));
  }

  private void handleFailure(RoutingContext ctx, Throwable err) {
    int statusCode = 500;
    if (err instanceof BadRequestException) {
      statusCode = 400;
    } else if (err instanceof NotFoundException) {
      statusCode = 404;
    } else if (err instanceof UnauthorizedException) {
      statusCode = 401;
    }

    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("Content-Type", "application/json")
        .end(Json.encodePrettily(new JsonObject()
            .put("status", "error")
            .put("message", err.getMessage())));
  }
}