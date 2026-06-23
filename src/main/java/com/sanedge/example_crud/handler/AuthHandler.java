package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.user.CreateUserRequest;
import com.sanedge.example_crud.exception.BadRequestException;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.service.AuthService;
import com.sanedge.example_crud.service.UserService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthHandler {
  private final AuthService service;
  private final UserService userService;

  public void login(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      handleFailure(ctx, new BadRequestException("Request body cannot be empty"));
      return;
    }
    service.login(body.getString("email"), body.getString("password"))
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void register(RoutingContext ctx) {
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

    service
        .register(register)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void refreshToken(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      handleFailure(ctx, new BadRequestException("Request body cannot be empty"));
      return;
    }

    service.refreshToken(body.getString("refresh_token"))
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void getMe(RoutingContext ctx) {
    Integer userid = ctx.user().principal().getInteger("userId");

    userService.getUserById(userid)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void logout(RoutingContext ctx) {
    Integer userid = ctx.user().principal().getInteger("userId");

    service.logout(userid)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  private void sendJsonResponse(RoutingContext ctx, Object result) {
    ctx.response()
        .putHeader("Content-Type", "application/json")
        .end(Json.encode(result));
  }

  private void handleFailure(RoutingContext ctx, Throwable err) {
    int statusCode = 500;

    if (err instanceof BadRequestException) {
      statusCode = 400;
    } else if (err instanceof NotFoundException) {
      statusCode = 404;
    }

    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("Content-Type", "application/json")
        .end(Json.encode(new JsonObject().put("error", err.getMessage())));
  }
}
