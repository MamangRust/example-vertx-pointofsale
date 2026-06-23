package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.role.CreateRoleRequest;
import com.sanedge.example_crud.domain.requests.role.FindAllRoles;
import com.sanedge.example_crud.domain.requests.role.UpdateRoleRequest;
import com.sanedge.example_crud.exception.BadRequestException;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.service.RoleService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RoleHandler {
  private final RoleService service;

  public void findAll(RoutingContext ctx) {
    FindAllRoles req = mapFindAllRoles(ctx);

    service.getAllRoles(req)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void findActive(RoutingContext ctx) {
    FindAllRoles req = mapFindAllRoles(ctx);

    service.getActiveRoles(req)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void findTrashed(RoutingContext ctx) {
    FindAllRoles req = mapFindAllRoles(ctx);

    service.getTrashedRoles(req)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void findById(RoutingContext ctx) {
    Integer roleId = Integer.parseInt(ctx.pathParam("id"));
    service.getRoleById(roleId)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void create(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      handleFailure(ctx, new BadRequestException("Request body cannot be empty"));
      return;
    }

    CreateRoleRequest req = CreateRoleRequest.builder().name(body.getString("roleName")).build();

    service.createRole(req)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void update(RoutingContext ctx) {
    Integer roleId = Integer.parseInt(ctx.pathParam("id"));
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      handleFailure(ctx, new BadRequestException("Request body cannot be empty"));
      return;
    }

    UpdateRoleRequest updateRoleRequest = UpdateRoleRequest.builder().roleId(roleId).name(body.getString("roleName"))
        .build();

    service.updateRole(updateRoleRequest)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void trashed(RoutingContext ctx) {
    Integer roleId = Integer.parseInt(ctx.pathParam("id"));
    service.trashed(roleId)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void restore(RoutingContext ctx) {
    Integer roleId = Integer.parseInt(ctx.pathParam("id"));
    service.restore(roleId)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void deletePermanent(RoutingContext ctx) {
    Integer roleId = Integer.parseInt(ctx.pathParam("id"));
    service.deletePermanent(roleId)
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void restoreAllRoles(RoutingContext ctx) {
    service.restoreAll()
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  public void deleteAllPermanentRoles(RoutingContext ctx) {
    service.deleteAllPermanent()
        .onSuccess(res -> sendJsonResponse(ctx, res))
        .onFailure(err -> handleFailure(ctx, err));
  }

  private FindAllRoles mapFindAllRoles(RoutingContext ctx) {
    FindAllRoles req = new FindAllRoles();

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
