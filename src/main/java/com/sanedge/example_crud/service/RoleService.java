package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.role.CreateRoleRequest;
import com.sanedge.example_crud.domain.requests.role.FindAllRoles;
import com.sanedge.example_crud.domain.requests.role.UpdateRoleRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.domain.response.role.RoleResponse;
import com.sanedge.example_crud.domain.response.role.RoleResponseDeleteAt;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.model.Role;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.RoleRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RoleService {

  private static final Logger logger = LoggerFactory.getLogger(RoleService.class);

  private final RoleRepository repo;
  private final RedisService redisService;
  private final TracingMetrics tracingMetrics;

  public Future<ApiResponsePagination<List<RoleResponse>>> getAllRoles(FindAllRoles req) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.getAllRoles");
    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
    req.setSearch(keyword);
    req.setPage(page);
    req.setPageSize(pageSize);

    return fetchPaginatedRoles(req, "roles:all", page, pageSize, keyword,
        repo::getRoles, RoleResponse::from, ctx, "get_all",
        "Roles fetched successfully");
  }

  public Future<ApiResponsePagination<List<RoleResponseDeleteAt>>> getActiveRoles(FindAllRoles req) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.getActiveRoles");
    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
    req.setSearch(keyword);
    req.setPage(page);
    req.setPageSize(pageSize);

    return fetchPaginatedRoles(req, "roles:active", page, pageSize, keyword,
        repo::getActiveRoles, RoleResponseDeleteAt::from, ctx, "get_active",
        "Active roles fetched successfully");
  }

  public Future<ApiResponsePagination<List<RoleResponseDeleteAt>>> getTrashedRoles(FindAllRoles req) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.getTrashedRoles");
    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
    req.setSearch(keyword);
    req.setPage(page);
    req.setPageSize(pageSize);

    return fetchPaginatedRoles(req, "roles:trashed", page, pageSize, keyword,
        repo::getTrashedRoles, RoleResponseDeleteAt::from, ctx, "get_trashed",
        "Trashed roles fetched successfully");
  }

  public Future<ApiResponse<RoleResponse>> getRoleById(Integer roleId) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.getRoleById",
        Attributes.builder().put("role.id", roleId).build());
    Span span = Span.fromContext(ctx.getContext());

    String cacheKey = "role:" + roleId;

    return redisService.get(cacheKey)
        .compose(cached -> {
          if (cached != null && !cached.isEmpty()) {
            span.setAttribute("role.cache_hit", true);
            try {
              Role role = Role.fromJson(new JsonObject(cached));
              tracingMetrics.completeSpanSuccess(ctx, "get_by_id", "Role fetched from cache");
              return Future.succeededFuture(
                  ApiResponse.success("Role fetched successfully (from cache)", RoleResponse.from(role)));
            } catch (Exception e) {
              logger.warn("Failed to parse cached role data for role {}: {}", roleId, e.getMessage());
              return fetchRoleFromDatabase(roleId, ctx);
            }
          }
          span.setAttribute("role.cache_hit", false);
          return fetchRoleFromDatabase(roleId, ctx);
        })
        .recover(err -> {
          logger.error("Failed to fetch role by id: {}", roleId, err);
          tracingMetrics.completeSpanError(ctx, "get_by_id", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<RoleResponse>error("Failed to fetch role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponse>> createRole(CreateRoleRequest req) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.createRole",
        Attributes.builder().put("role.name", req.getName()).build());
    Span span = Span.fromContext(ctx.getContext());

    return repo.createRole(req)
        .map(created -> {
          span.setAttribute("role.id", created.getRoleId());
          tracingMetrics.completeSpanSuccess(ctx, "create", "Role created successfully");
          return ApiResponse.success("Role created successfully", RoleResponse.from(created));
        })
        .recover(err -> {
          logger.error("Failed to create role: {}", req.getName(), err);
          tracingMetrics.completeSpanError(ctx, "create", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<RoleResponse>error("Failed to create role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponse>> updateRole(UpdateRoleRequest req) {
    Integer roleId = req.getRoleId();
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.updateRole",
        Attributes.builder().put("role.id", roleId).put("role.name", req.getName()).build());

    return repo.updateRole(req)
        .compose(role -> {
          if (role == null) {
            return Future.failedFuture(new NotFoundException("Role not found"));
          }
          invalidateCache(roleId);
          tracingMetrics.completeSpanSuccess(ctx, "update", "Role updated successfully");
          return Future.succeededFuture(ApiResponse.success("Role updated successfully", RoleResponse.from(role)));
        })
        .recover(err -> {
          logger.error("Failed to update role: {}", roleId, err);
          tracingMetrics.completeSpanError(ctx, "update", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<RoleResponse>error("Failed to update role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponseDeleteAt>> trashed(Integer roleId) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.trashed",
        Attributes.builder().put("role.id", roleId).build());

    return repo.trashed(roleId)
        .compose(role -> {
          if (role == null) {
            return Future.failedFuture(new NotFoundException("Role not found with id: " + roleId));
          }
          invalidateCache(roleId);
          tracingMetrics.completeSpanSuccess(ctx, "trashed", "Role trashed successfully");
          return Future
              .succeededFuture(ApiResponse.success("Role trashed successfully", RoleResponseDeleteAt.from(role)));
        })
        .recover(err -> {
          logger.error("Failed to trash role: {}", roleId, err);
          tracingMetrics.completeSpanError(ctx, "trashed", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<RoleResponseDeleteAt>error("Failed to trash role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponseDeleteAt>> restore(Integer roleId) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.restore",
        Attributes.builder().put("role.id", roleId).build());

    return repo.findByTrashed(roleId)
        .compose(role -> {
          if (role == null) {
            return Future.failedFuture(new NotFoundException("Role not found with id: " + roleId));
          }
          return repo.restore(roleId);
        })
        .compose(role -> {
          invalidateCache(roleId);
          tracingMetrics.completeSpanSuccess(ctx, "restore", "Role restored successfully");
          return Future
              .succeededFuture(ApiResponse.success("Role restored successfully", RoleResponseDeleteAt.from(role)));
        })
        .recover(err -> {
          logger.error("Failed to restore role: {}", roleId, err);
          tracingMetrics.completeSpanError(ctx, "restore", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<RoleResponseDeleteAt>error("Failed to restore role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Void>> deletePermanent(Integer roleId) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.deletePermanent",
        Attributes.builder().put("role.id", roleId).build());

    return repo.findByTrashed(roleId)
        .compose(role -> {
          if (role == null) {
            return Future.failedFuture(new NotFoundException("Role not found with id: " + roleId));
          }
          return repo.deletePermanent(roleId);
        })
        .map(v -> {
          invalidateCache(roleId);
          tracingMetrics.completeSpanSuccess(ctx, "deletePermanent", "Role deleted permanently");
          return ApiResponse.<Void>success("Role deleted permanently", null);
        })
        .recover(err -> {
          logger.error("Failed to deletePermanent role: {}", roleId, err);
          tracingMetrics.completeSpanError(ctx, "deletePermanent", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<Void>error("Failed to delete role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Integer>> restoreAll() {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.restoreAll");
    return repo.restoreAllRoles()
        .compose(count -> {
          if (count == 0) {
            return Future.failedFuture(new NotFoundException("No trashed roles found"));
          }
          tracingMetrics.completeSpanSuccess(ctx, "restore_all", "Success");
          return Future.succeededFuture(ApiResponse.success("All roles restored", count));
        })
        .recover(err -> {
          logger.error("Failed to restore all roles", err);
          tracingMetrics.completeSpanError(ctx, "restore_all", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(ApiResponse.<Integer>error("Failed to restore all: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Integer>> deleteAllPermanent() {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("RoleService.deleteAllPermanent");
    return repo.deleteAllPermanentRoles()
        .compose(count -> {
          if (count == 0) {
            return Future.failedFuture(new NotFoundException("No trashed roles found"));
          }
          tracingMetrics.completeSpanSuccess(ctx, "delete_all", "Success");
          return Future.succeededFuture(ApiResponse.success("All roles deleted permanently", count));
        })
        .recover(err -> {
          logger.error("Failed to delete all roles permanently", err);
          tracingMetrics.completeSpanError(ctx, "delete_all", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(ApiResponse.<Integer>error("Failed to delete all: " + err.getMessage()));
        });
  }

  private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedRoles(T req, String cachePrefix,
      int page, int pageSize, String keyword,
      Function<T, Future<PagedResult<Role>>> dbFetcher,
      Function<Role, R> responseMapper, TracingMetrics.TracingContext tracingContext,
      String spanName, String successMessage) {

    Span span = Span.fromContext(tracingContext.getContext());
    String cacheKey = String.format("%s:page:%d:size:%d:search:%s", cachePrefix, page, pageSize, keyword);

    return redisService.get(cacheKey)
        .compose(cached -> {
          if (cached != null && !cached.isEmpty()) {
            span.setAttribute("cache.hit", true);
            try {
              JsonObject json = new JsonObject(cached);
              int totalRecords = json.getInteger("totalRecords");
              int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

              List<R> data = json.getJsonArray("data").stream()
                  .map(obj -> responseMapper.apply(Role.fromJson((JsonObject) obj)))
                  .toList();

              tracingMetrics.completeSpanSuccess(tracingContext, spanName, "Roles fetched from cache");
              return Future.succeededFuture(new ApiResponsePagination<>("success", successMessage, data,
                  new PaginationMeta(page + 1, pageSize, totalPages, totalRecords)));
            } catch (Exception e) {
              logger.warn("Failed to parse cached paginated roles: {}", e.getMessage());
            }
          }

          span.setAttribute("cache.hit", false);
          return dbFetcher.apply(req)
              .map(result -> {
                JsonObject jsonToCache = new JsonObject()
                    .put("totalRecords", result.getTotalRecords())
                    .put("data", new JsonArray(
                        result.getData().stream().map(Role::toJson).toList()));

                redisService.set(cacheKey, jsonToCache.encode(), Duration.ofMinutes(5))
                    .onFailure(err -> logger.warn("Failed to cache {}: {}", cachePrefix, err.getMessage()));

                span.setAttribute("roles.count", result.getData().size());
                span.setAttribute("roles.total_records", result.getTotalRecords());
                tracingMetrics.completeSpanSuccess(tracingContext, spanName, successMessage);

                return mapPagination(result, page, pageSize, responseMapper, successMessage);
              });
        })
        .recover(throwable -> {
          logger.error("Failed to fetch paginated roles for {}", cachePrefix, throwable);
          tracingMetrics.completeSpanError(tracingContext, spanName, throwable.getMessage());
          return Future.succeededFuture(
              ApiResponsePagination.<List<R>>error("Failed to fetch roles: " + throwable.getMessage()));
        });
  }

  private Future<ApiResponse<RoleResponse>> fetchRoleFromDatabase(Integer roleId,
      TracingMetrics.TracingContext tracingContext) {
    Span span = Span.fromContext(tracingContext.getContext());

    return repo.getRoleById(roleId)
        .compose(role -> {
          if (role == null) {
            return Future.failedFuture(new NotFoundException("Role not found"));
          }

          span.setAttribute("role.name", role.getRoleName());

          redisService.setJson("role:" + roleId, role.toJson(), Duration.ofMinutes(60))
              .onFailure(err -> logger.warn("Failed to cache role {}: {}", roleId, err.getMessage()));

          return Future.succeededFuture(ApiResponse.success("Role fetched successfully", RoleResponse.from(role)));
        });
  }

  private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<Role> result, int page, int pageSize,
      Function<Role, R> mapper, String message) {
    int totalRecords = result.getTotalRecords();
    int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    List<R> data = result.getData().stream().map(mapper).toList();

    return new ApiResponsePagination<>("success", message, data,
        new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
  }

  private void invalidateCache(Integer roleId) {
    if (roleId != null) {
      redisService.delete("role:" + roleId)
          .onSuccess(deleted -> {
            if (deleted > 0) {
              logger.debug("Role {} cache invalidated", roleId);
            }
          })
          .onFailure(err -> logger.warn("Failed to invalidate cache for role {}: {}", roleId, err.getMessage()));
    }
  }
}