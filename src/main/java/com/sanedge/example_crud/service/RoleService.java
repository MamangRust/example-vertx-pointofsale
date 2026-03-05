package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.Json;

public class RoleService {
  private static final Logger logger = LoggerFactory.getLogger(RoleService.class);
  private final RoleRepository repo;
  private final RedisService redisService;
  private final TracingMetrics tracingMetrics;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public RoleService(
      RoleRepository repo,
      RedisService redisService,
      TracingMetrics tracingMetrics) {
    this.repo = repo;
    this.redisService = redisService;
    this.tracingMetrics = tracingMetrics;
  }

  public Future<ApiResponsePagination<List<RoleResponse>>> getAllRoles(FindAllRoles req) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("RoleService.getAllRoles");
    Span span = Span.fromContext(tracingContext.getContext());

    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

    req.setSearch(keyword);
    req.setPage(page);
    req.setPageSize(pageSize);

    logger.info("Fetching roles | search={}, page={}, pageSize={}", req.getSearch(), page, pageSize);

    String cacheKey = String.format("roles:page:%d:search:%s", page, keyword);

    return redisService.get(cacheKey)
        .compose(cached -> handleCacheOrRepo(
            cached,
            cacheKey,
            () -> repo.getRoles(req),
            new TypeReference<PagedResult<Role>>() {
            }, 
            result -> mapRolePagination(result, req, keyword),
            tracingContext,
            "get_all",
            Duration.ofMinutes(5) 
        ))
        .map(response -> {
          span.setAttribute("roles.count", response.data().size());
          span.setAttribute("roles.total_records", response.pagination().totalRecords());
          return response;
        })
        .recover(throwable -> {
          logger.error("Failed to fetch roles", throwable);
          tracingMetrics.completeSpanError(tracingContext, "get_all", throwable.getMessage());
          return Future.succeededFuture(
              ApiResponsePagination.<List<RoleResponse>>error("Failed to fetch roles: " + throwable.getMessage()));
        });
  }

  public Future<ApiResponsePagination<List<RoleResponseDeleteAt>>> getActiveRoles(FindAllRoles req) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("RoleService.getActiveRoles");
    Span span = Span.fromContext(tracingContext.getContext());

    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

    req.setSearch(keyword);

    logger.info("Fetching active roles | search={}, page={}, pageSize={}", req.getSearch(), page, pageSize);

    String cacheKey = String.format("roles:active:page:%d:search:%s", page, keyword);

    return redisService.get(cacheKey)
        .compose(cached -> handleCacheOrRepo(
            cached,
            cacheKey,
            () -> repo.getActiveRoles(req),
            new TypeReference<PagedResult<Role>>() {
            },
            result -> mapRolePaginationDeleteAt(result, req, keyword),
            tracingContext,
            "get_active",
            Duration.ofMinutes(5)))
        .map(response -> {
          span.setAttribute("role.count", response.data().size());
          span.setAttribute("role.total_records", response.pagination().totalRecords());
          return response;
        })
        .recover(throwable -> {
          logger.error("Failed to fetch active roles", throwable);
          tracingMetrics.completeSpanError(tracingContext, "get_active", throwable.getMessage());
          return Future.succeededFuture(
              ApiResponsePagination
                  .<List<RoleResponseDeleteAt>>error("Failed to fetch active roles: " + throwable.getMessage()));
        });
  }

  public Future<ApiResponsePagination<List<RoleResponseDeleteAt>>> getTrashedRoles(FindAllRoles req) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("RoleService.getTrashedRoles");
    Span span = Span.fromContext(tracingContext.getContext());

    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

    req.setSearch(keyword);

    logger.info("Fetching trashed roles | search={}, page={}, pageSize={}", req.getSearch(), page, pageSize);

    String cacheKey = String.format("roles:trashed:page:%d:search:%s", page, keyword);

    return redisService.get(cacheKey)
        .compose(cached -> handleCacheOrRepo(
            cached,
            cacheKey,
            () -> repo.getTrashedRoles(req),
            new TypeReference<PagedResult<Role>>() {
            },
            result -> mapRolePaginationDeleteAt(result, req, keyword),
            tracingContext,
            "get_trashed",
            Duration.ofMinutes(5)))
        .map(response -> {
          span.setAttribute("role.count", response.data().size());
          span.setAttribute("role.total_records", response.pagination().totalRecords());
          return response;
        })
        .recover(throwable -> {
          logger.error("Failed to fetch trashed roles", throwable);
          tracingMetrics.completeSpanError(tracingContext, "get_trashed", throwable.getMessage());
          return Future.succeededFuture(
              ApiResponsePagination
                  .<List<RoleResponseDeleteAt>>error("Failed to fetch trashed roles: " + throwable.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponse>> getRoleById(Integer roleId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "RoleService.getRoleById",
        io.opentelemetry.api.common.Attributes.builder()
            .put("role.id", roleId)
            .build());
    Span span = Span.fromContext(tracingContext.getContext());

    logger.info("Fetching role by id: {}", roleId);
    String cacheKey = "role:" + roleId;

    return redisService.get(cacheKey)
        .compose(cached -> {
          span.setAttribute("role.cache_hit", cached != null);

          return handleCacheOrRepo(
              cached,
              cacheKey,
              () -> repo.getRoleById(roleId).map(role -> {
                if (role == null) {
                  throw new NotFoundException("Role not found");
                }

                span.setAttribute("role.name", role.getRoleName());
                return role;
              }),
              new TypeReference<Role>() {
              },
              role -> ApiResponse.success("Role fetched successfully", RoleResponse.from(role)),
              tracingContext,
              "get_by_id",
              Duration.ofMinutes(60));
        })
        .recover(err -> {
          logger.error("Failed to fetch role by id: {}", roleId, err);
          tracingMetrics.completeSpanError(tracingContext, "get_by_id", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<RoleResponse>error("Failed to fetch role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponse>> createRole(CreateRoleRequest req) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "RoleService.createRole",
        io.opentelemetry.api.common.Attributes.builder()
            .put("role.name", req.getName())
            .build());
    Span span = Span.fromContext(tracingContext.getContext());

    logger.info("Creating role: {}", req.getName());

    return repo.createRole(req)
        .map(created -> {
          span.setAttribute("role.id", created.getRoleId());
          tracingMetrics.completeSpanSuccess(tracingContext, "create", "Role created successfully");
          return ApiResponse.success(
              "Role created successfully",
              RoleResponse.from(created));
        })
        .recover(err -> {
          logger.error("Failed to create role: {}", req.getName(), err);
          tracingMetrics.completeSpanError(tracingContext, "create", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<RoleResponse>error(
                  "Failed to create role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponse>> updateRole(UpdateRoleRequest req) {
    Integer roleId = req.getRoleId();
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "RoleService.updateRole",
        io.opentelemetry.api.common.Attributes.builder()
            .put("role.id", roleId)
            .put("role.name", req.getName())
            .build());

    logger.info("Updating role: {}, name: {}", roleId, req.getName());

    return repo.updateRole(req)
        .compose((Role dota) -> {
          String cacheKey = "role:" + roleId;
          return redisService.delete(cacheKey)
              .onSuccess(deleted -> {
                if (deleted > 0) {
                  logger.debug("Role {} cache invalidated", roleId);
                }
              })
              .onFailure(err -> logger.warn("Failed to invalidate cache for role {}: {}", roleId, err.getMessage()))
              .map(dota);
        })
        .map((Role dota) -> {
          tracingMetrics.completeSpanSuccess(tracingContext, "update", "Role updated successfully");
          return ApiResponse.success(
              "Role updated successfully",
              RoleResponse.from(dota));
        })
        .recover(err -> {
          logger.error("Failed to update role: {}", roleId, err);
          tracingMetrics.completeSpanError(tracingContext, "update", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<RoleResponse>error(
                  "Failed to update role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponseDeleteAt>> trashed(Integer roleId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "RoleService.trashed",
        io.opentelemetry.api.common.Attributes.builder()
            .put("role.id", roleId)
            .build());

    logger.info("Trashing role: {}", roleId);

    return repo.trashed(roleId)
        .compose(role -> {
          if (role == null) {
            return Future.failedFuture(new NotFoundException("Role not found with id: " + roleId));
          }
          String cacheKey = "role:" + roleId;
          return redisService.delete(cacheKey)
              .onSuccess(deleted -> {
                if (deleted > 0) {
                  logger.debug("Role {} cache invalidated on trash", roleId);
                }
              })
              .onFailure(
                  err -> logger.warn("Failed to invalidate cache for trashed role {}: {}", roleId, err.getMessage()))
              .map(role);
        })
        .map(role -> {
          tracingMetrics.completeSpanSuccess(tracingContext, "trashed", "Role trashed successfully");
          return ApiResponse.success("Role trashed successfully", RoleResponseDeleteAt.from(role));
        })
        .recover(err -> {
          logger.error("Failed to trash role: {}", roleId, err);
          tracingMetrics.completeSpanError(tracingContext, "trashed", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<RoleResponseDeleteAt>error(
                  "Failed to trash role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<RoleResponseDeleteAt>> restore(Integer roleId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "RoleService.restore",
        io.opentelemetry.api.common.Attributes.builder()
            .put("role.id", roleId)
            .build());

    logger.info("Restoring role: {}", roleId);

    return repo.restore(roleId)
        .compose(role -> {
          if (role == null) {
            return Future.failedFuture(new NotFoundException("Role not found with id: " + roleId));
          }
          String cacheKey = "role:" + roleId;
          return redisService.delete(cacheKey)
              .onSuccess(deleted -> {
                if (deleted > 0) {
                  logger.debug("Role {} cache invalidated on restore", roleId);
                }
              })
              .onFailure(
                  err -> logger.warn("Failed to invalidate cache for restored role {}: {}", roleId, err.getMessage()))
              .map(role);
        })
        .map(role -> {
          tracingMetrics.completeSpanSuccess(tracingContext, "restore", "Role restored successfully");
          return ApiResponse.success(
              "Role restored successfully",
              RoleResponseDeleteAt.from(role));
        })
        .recover(err -> {
          logger.error("Failed to restore role: {}", roleId, err);
          tracingMetrics.completeSpanError(tracingContext, "restore", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<RoleResponseDeleteAt>error(
                  "Failed to restore role: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Void>> deletePermanent(Integer roleId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "RoleService.deletePermanent",
        io.opentelemetry.api.common.Attributes.builder()
            .put("role.id", roleId)
            .build());

    logger.info("Permanently deleting role: {}", roleId);

    return repo.deletePermanent(roleId)
        .compose(v -> {
          String cacheKey = "role:" + roleId;
          return redisService.delete(cacheKey)
              .onSuccess(deleted -> {
                if (deleted > 0) {
                  logger.debug("Role {} cache invalidated on permanent delete", roleId);
                }
              })
              .onFailure(
                  err -> logger.warn("Failed to invalidate cache for deleted role {}: {}", roleId, err.getMessage()))
              .map(v);
        })
        .map(v -> {
          logger.info("Role deleted successfully: {}", roleId);
          tracingMetrics.completeSpanSuccess(tracingContext, "deletePermanent", "Role deleted permanently");
          return ApiResponse.<Void>success("success", null);
        })
        .recover(throwable -> {
          logger.error("Failed to deletePermanent role: {}", roleId, throwable);
          tracingMetrics.completeSpanError(tracingContext, "deletePermanent", throwable.getMessage());
          return Future.succeededFuture(
              ApiResponse.<Void>error("Failed to delete role: " + throwable.getMessage()));
        });
  }

  public Future<ApiResponse<Integer>> restoreAll() {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("UserService.restoreAll");
    return repo.restoreAllRoles()
        .map(count -> {
          tracingMetrics.completeSpanSuccess(tracingContext, "restore_all", "Success");
          return ApiResponse.success("All users restored", count);
        })
        .recover(err -> {
          logger.error("Failed to restore all users", err);
          tracingMetrics.completeSpanError(tracingContext, "restore_all", err.getMessage());
          return Future.succeededFuture(ApiResponse.<Integer>error("Failed to restore all: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Integer>> deleteAllPermanent() {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("UserService.deleteAllPermanent");
    return repo.deleteAllPermanentRoles()
        .map(count -> {
          tracingMetrics.completeSpanSuccess(tracingContext, "delete_all", "Success");
          return ApiResponse.success("All users deleted permanently", count);
        })
        .recover(err -> {
          logger.error("Failed to delete all users permanently", err);
          tracingMetrics.completeSpanError(tracingContext, "delete_all", err.getMessage());
          return Future.succeededFuture(ApiResponse.<Integer>error("Failed to delete all: " + err.getMessage()));
        });
  }

  private <T, R> Future<R> handleCacheOrRepo(String cached, String cacheKey,
      java.util.concurrent.Callable<Future<T>> repoCall, TypeReference<T> typeRef,
      java.util.function.Function<T, R> mapper,
      TracingMetrics.TracingContext tracingCtx, String operation, Duration ttl) {

    if (cached != null) {
      try {
        T data = objectMapper.readValue(cached, typeRef);
        tracingMetrics.completeSpanSuccess(tracingCtx, operation, "Success");
        return Future.succeededFuture(mapper.apply(data));
      } catch (Exception e) {
        logger.warn("Cache parse error", e);
      }
    }

    try {
      return repoCall.call().map(res -> {
        redisService.set(cacheKey, Json.encode(res), ttl);
        tracingMetrics.completeSpanSuccess(tracingCtx, operation, "Success");
        return mapper.apply(res);
      });
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private ApiResponsePagination<List<RoleResponse>> mapRolePagination(
      PagedResult<Role> result,
      FindAllRoles req,
      String message) {

    int pageSize = req.getPageSize();
    int totalRecords = result.getTotalRecords();
    int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    List<RoleResponse> data = result.getData()
        .stream()
        .map(RoleResponse::from)
        .toList();

    return new ApiResponsePagination<>(
        "success",
        message,
        data,
        new PaginationMeta(
            req.getPage() + 1,
            pageSize,
            totalPages,
            totalRecords));
  }

  private ApiResponsePagination<List<RoleResponseDeleteAt>> mapRolePaginationDeleteAt(
      PagedResult<Role> result,
      FindAllRoles req,
      String message) {

    int pageSize = req.getPageSize();
    int totalRecords = result.getTotalRecords();
    int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    List<RoleResponseDeleteAt> data = result.getData()
        .stream()
        .map(RoleResponseDeleteAt::from)
        .toList();

    return new ApiResponsePagination<>(
        "success",
        message,
        data,
        new PaginationMeta(
            req.getPage() + 1,
            pageSize,
            totalPages,
            totalRecords));
  }
}
