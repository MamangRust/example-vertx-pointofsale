package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.role.CreateRoleRequest;
import com.sanedge.example_crud.domain.requests.role.FindAllRoles;
import com.sanedge.example_crud.domain.requests.role.UpdateRoleRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
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
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void findActive(RoutingContext ctx) {
    FindAllRoles req = mapFindAllRoles(ctx);

    service.getActiveRoles(req)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void findTrashed(RoutingContext ctx) {
    FindAllRoles req = mapFindAllRoles(ctx);

    service.getTrashedRoles(req)
        .onSuccess(res -> sendResponse(ctx, res))
        .onFailure(err -> sendErrorResponse(ctx, err));
  }

  public void findById(RoutingContext ctx) {
    try {
      Integer roleId = Integer.parseInt(ctx.pathParam("id"));
      service.getRoleById(roleId)
          .onSuccess(role -> sendResponse(ctx, role))
          .onFailure(err -> sendErrorResponse(ctx, err));
    } catch (NumberFormatException e) {
      sendErrorResponse(ctx, new IllegalArgumentException("Invalid role ID format"));
    }
  }

  public void create(RoutingContext ctx) {
    try {
      JsonObject body = ctx.body().asJsonObject();
      CreateRoleRequest req = CreateRoleRequest.builder()
          .name(body.getString("roleName"))
          .build();

      service.createRole(req)
          .onSuccess(created -> sendResponse(ctx, created))
          .onFailure(err -> sendErrorResponse(ctx, err));
    } catch (Exception e) {
      sendErrorResponse(ctx, e);
    }
  }

  public void update(RoutingContext ctx) {
    try {
      Integer roleId = Integer.parseInt(ctx.pathParam("id"));
      JsonObject body = ctx.body().asJsonObject();

      UpdateRoleRequest updateRoleRequest = UpdateRoleRequest.builder()
          .roleId(roleId)
          .name(body.getString("roleName"))
          .build();

      service.updateRole(updateRoleRequest)
          .onSuccess(v -> sendResponse(ctx, v))
          .onFailure(err -> sendErrorResponse(ctx, err));
    } catch (NumberFormatException e) {
      sendErrorResponse(ctx, new IllegalArgumentException("Invalid role ID format"));
    }
  }

  public void trashed(RoutingContext ctx) {
    try {
      Integer roleId = Integer.parseInt(ctx.pathParam("id"));
      service.trashed(roleId)
          .onSuccess(v -> sendResponse(ctx, v))
          .onFailure(err -> sendErrorResponse(ctx, err));
    } catch (NumberFormatException e) {
      sendErrorResponse(ctx, new IllegalArgumentException("Invalid role ID format"));
    }
  }

  public void restore(RoutingContext ctx) {
    try {
      Integer roleId = Integer.parseInt(ctx.pathParam("id"));
      service.restore(roleId)
          .onSuccess(v -> sendResponse(ctx, v))
          .onFailure(err -> sendErrorResponse(ctx, err));
    } catch (NumberFormatException e) {
      sendErrorResponse(ctx, new IllegalArgumentException("Invalid role ID format"));
    }
  }

  public void deletePermanent(RoutingContext ctx) {
    try {
      Integer roleId = Integer.parseInt(ctx.pathParam("id"));
      service.deletePermanent(roleId)
          .onSuccess(v -> sendResponse(ctx, v))
          .onFailure(err -> sendErrorResponse(ctx, err));
    } catch (NumberFormatException e) {
      sendErrorResponse(ctx, new IllegalArgumentException("Invalid role ID format"));
    }
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
}