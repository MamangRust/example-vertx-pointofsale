package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.user.CreateUserRequest;
import com.sanedge.example_crud.domain.requests.user.FindAllUsers;
import com.sanedge.example_crud.domain.requests.user.UpdateUserRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.domain.response.user.UserResponse;
import com.sanedge.example_crud.domain.response.user.UserResponseDeleteAt;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.model.User;
import com.sanedge.example_crud.model.UserRole;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.RoleRepository;
import com.sanedge.example_crud.repository.UserRepository;
import com.sanedge.example_crud.repository.UserRoleRepository;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  private final UserRepository repository;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;
  private final RedisService redisService;
  private final TracingMetrics tracingMetrics;

  public Future<ApiResponsePagination<List<UserResponse>>> getAllUsers(FindAllUsers req) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.getAllUsers");
    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
    req.setPage(page);
    req.setPageSize(pageSize);
    req.setSearch(keyword);

    return fetchPaginatedUsers(req, "users:all", page, pageSize, keyword,
        repository::getUsers, UserResponse::from, ctx, "get_all",
        "Users fetched successfully");
  }

  public Future<ApiResponsePagination<List<UserResponseDeleteAt>>> getActiveUsers(FindAllUsers req) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.getActiveUsers");
    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
    req.setPage(page);
    req.setPageSize(pageSize);
    req.setSearch(keyword);

    return fetchPaginatedUsers(req, "users:active", page, pageSize, keyword,
        repository::getActiveUsers, UserResponseDeleteAt::from, ctx, "get_active",
        "Active users fetched successfully");
  }

  public Future<ApiResponsePagination<List<UserResponseDeleteAt>>> getTrashedUsers(FindAllUsers req) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.getTrashedUsers");
    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
    req.setPage(page);
    req.setPageSize(pageSize);
    req.setSearch(keyword);

    return fetchPaginatedUsers(req, "users:trashed", page, pageSize, keyword,
        repository::getTrashedUsers, UserResponseDeleteAt::from, ctx, "get_trashed",
        "Trashed users fetched successfully");
  }

  public Future<ApiResponse<UserResponse>> getUserById(Integer userId) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.getUserById",
        Attributes.builder().put("user.id", userId).build());
    Span span = Span.fromContext(ctx.getContext());

    String cacheKey = "user:" + userId;

    return redisService.get(cacheKey)
        .compose(cached -> {
          if (cached != null && !cached.isEmpty()) {
            span.setAttribute("user.cache_hit", true);
            try {
              User user = User.fromJson(new JsonObject(cached));
              tracingMetrics.completeSpanSuccess(ctx, "get_by_id", "User fetched from cache");
              return Future.succeededFuture(
                  ApiResponse.success("User fetched successfully (from cache)", UserResponse.from(user)));
            } catch (Exception e) {
              logger.warn("Failed to parse cached user data for user {}: {}", userId, e.getMessage());
              return fetchUserFromDatabase(userId, ctx);
            }
          }
          span.setAttribute("user.cache_hit", false);
          return fetchUserFromDatabase(userId, ctx);
        })
        .recover(err -> {
          logger.error("Failed to fetch user by id: {}", userId, err);
          tracingMetrics.completeSpanError(ctx, "get_by_id", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<UserResponse>error("Failed to fetch user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponse>> createUser(CreateUserRequest req) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.createUser",
        Attributes.builder()
            .put("user.email", req.getEmail())
            .put("user.firstname", req.getFirstName())
            .put("user.lastname", req.getLastName())
            .build());
    Span span = Span.fromContext(ctx.getContext());

    String hashed = BCrypt.withDefaults().hashToString(12, req.getPassword().toCharArray());
    req.setPassword(hashed);

    return repository.createUser(req)
        .compose(createdUser -> {
          span.setAttribute("user.user_id", createdUser.getUserId());
          return roleRepository.getRoleByName("ADMIN")
              .compose(role -> {
                if (role == null) {
                  return Future.failedFuture(
                      new IllegalStateException("Default 'ADMIN' role not found in the database."));
                }

                UserRole userRole = new UserRole();
                userRole.setRoleId(role.getRoleId());
                userRole.setUserId(createdUser.getUserId());

                return userRoleRepository.assignRoleToUser(userRole).map(v -> createdUser);
              });
        })
        .map(createdUser -> {
          tracingMetrics.completeSpanSuccess(ctx, "create", "User created successfully");
          return ApiResponse.success("User created successfully", UserResponse.from(createdUser));
        })
        .recover(err -> {
          logger.error("Failed to create user: {}", req.getEmail(), err);
          tracingMetrics.completeSpanError(ctx, "create", err.getMessage());
          if (err instanceof NotFoundException || err instanceof IllegalStateException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<UserResponse>error("Failed to create user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponse>> updateUser(UpdateUserRequest req) {
    Integer userId = req.getUserId();
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.updateUser",
        Attributes.builder()
            .put("user.id", userId)
            .put("user.email", req.getEmail())
            .build());

    return repository.updateUser(req)
        .compose(user -> {
          if (user == null) {
            return Future.failedFuture(new NotFoundException("User not found"));
          }
          invalidateCache(user.getUserId());
          tracingMetrics.completeSpanSuccess(ctx, "update", "User updated successfully");
          return Future.succeededFuture(ApiResponse.success("User updated successfully", UserResponse.from(user)));
        })
        .recover(err -> {
          logger.error("Failed to update user: {}", userId, err);
          tracingMetrics.completeSpanError(ctx, "update", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<UserResponse>error("Failed to update user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponseDeleteAt>> trashed(Integer userId) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.trashed",
        Attributes.builder().put("user.id", userId).build());

    return repository.trashed(userId)
        .compose(user -> {
          if (user == null) {
            return Future.failedFuture(new NotFoundException("User not found with id: " + userId));
          }
          invalidateCache(userId);
          tracingMetrics.completeSpanSuccess(ctx, "trashed", "User trashed successfully");
          return Future.succeededFuture(
              ApiResponse.success("User trashed successfully", UserResponseDeleteAt.from(user)));
        })
        .recover(err -> {
          logger.error("Failed to trash user: {}", userId, err);
          tracingMetrics.completeSpanError(ctx, "trashed", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<UserResponseDeleteAt>error("Failed to trash user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponseDeleteAt>> restore(Integer userId) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.restore",
        Attributes.builder().put("user.id", userId).build());

    return repository.findByTrashed(userId)
        .compose(user -> {
          if (user == null) {
            return Future.failedFuture(new NotFoundException("User not found with id: " + userId));
          }
          return repository.restore(userId);
        })
        .compose(user -> {
          invalidateCache(userId);
          tracingMetrics.completeSpanSuccess(ctx, "restore", "User restored successfully");
          return Future.succeededFuture(
              ApiResponse.success("User restored successfully", UserResponseDeleteAt.from(user)));
        })
        .recover(err -> {
          logger.error("Failed to restore user: {}", userId, err);
          tracingMetrics.completeSpanError(ctx, "restore", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<UserResponseDeleteAt>error("Failed to restore user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Void>> deletePermanent(Integer userId) {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.deletePermanent",
        Attributes.builder().put("user.id", userId).build());

    return repository.findByTrashed(userId)
        .compose(user -> {
          if (user == null) {
            return Future.failedFuture(new NotFoundException("User not found with id: " + userId));
          }
          return repository.deletePermanent(userId);
        })
        .map(v -> {
          invalidateCache(userId);
          tracingMetrics.completeSpanSuccess(ctx, "deletePermanent", "User deleted permanently");
          return ApiResponse.<Void>success("User deleted successfully", null);
        })
        .recover(err -> {
          logger.error("Failed to delete user: {}", userId, err);
          tracingMetrics.completeSpanError(ctx, "deletePermanent", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<Void>error("Failed to delete user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Integer>> restoreAll() {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.restoreAll");
    return repository.restoreAllUsers()
        .compose(count -> {
          if (count == 0) {
            return Future.failedFuture(new NotFoundException("No trashed users found"));
          }
          tracingMetrics.completeSpanSuccess(ctx, "restore_all", "Success");
          return Future.succeededFuture(ApiResponse.success("All users restored", count));
        })
        .recover(err -> {
          logger.error("Failed to restore all users", err);
          tracingMetrics.completeSpanError(ctx, "restore_all", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(ApiResponse.<Integer>error("Failed to restore all: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Integer>> deleteAllPermanent() {
    TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("UserService.deleteAllPermanent");
    return repository.deleteAllPermanentUsers()
        .compose(count -> {
          if (count == 0) {
            return Future.failedFuture(new NotFoundException("No trashed users found"));
          }
          tracingMetrics.completeSpanSuccess(ctx, "delete_all", "Success");
          return Future.succeededFuture(ApiResponse.success("All users deleted permanently", count));
        })
        .recover(err -> {
          logger.error("Failed to delete all users permanently", err);
          tracingMetrics.completeSpanError(ctx, "delete_all", err.getMessage());
          if (err instanceof NotFoundException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(ApiResponse.<Integer>error("Failed to delete all: " + err.getMessage()));
        });
  }

  private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedUsers(T req, String cachePrefix,
      int page, int pageSize, String keyword,
      Function<T, Future<PagedResult<User>>> dbFetcher,
      Function<User, R> responseMapper, TracingMetrics.TracingContext tracingContext,
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
                  .map(obj -> responseMapper.apply(User.fromJson((JsonObject) obj)))
                  .toList();

              tracingMetrics.completeSpanSuccess(tracingContext, spanName, "Users fetched from cache");
              return Future.succeededFuture(new ApiResponsePagination<>("success", successMessage, data,
                  new PaginationMeta(page + 1, pageSize, totalPages, totalRecords)));
            } catch (Exception e) {
              logger.warn("Failed to parse cached paginated users: {}", e.getMessage());
            }
          }

          span.setAttribute("cache.hit", false);
          return dbFetcher.apply(req)
              .map(result -> {
                JsonObject jsonToCache = new JsonObject()
                    .put("totalRecords", result.getTotalRecords())
                    .put("data", new JsonArray(
                        result.getData().stream().map(User::toJson).toList()));

                redisService.set(cacheKey, jsonToCache.encode(), Duration.ofMinutes(5))
                    .onFailure(err -> logger.warn("Failed to cache {}: {}", cachePrefix, err.getMessage()));

                span.setAttribute("users.count", result.getData().size());
                span.setAttribute("users.total_records", result.getTotalRecords());
                tracingMetrics.completeSpanSuccess(tracingContext, spanName, successMessage);

                return mapPagination(result, page, pageSize, responseMapper, successMessage);
              });
        })
        .recover(throwable -> {
          logger.error("Failed to fetch paginated users for {}", cachePrefix, throwable);
          tracingMetrics.completeSpanError(tracingContext, spanName, throwable.getMessage());
          return Future.succeededFuture(
              ApiResponsePagination.<List<R>>error("Failed to fetch users: " + throwable.getMessage()));
        });
  }

  private Future<ApiResponse<UserResponse>> fetchUserFromDatabase(Integer userId,
      TracingMetrics.TracingContext tracingContext) {
    Span span = Span.fromContext(tracingContext.getContext());

    return repository.getUserById(userId)
        .compose(user -> {
          if (user == null) {
            return Future.failedFuture(new NotFoundException("User not found"));
          }

          span.setAttribute("user.email", user.getEmail());

          redisService.setJson("user:" + userId, user.toJson(), Duration.ofMinutes(30))
              .onFailure(err -> logger.warn("Failed to cache user {}: {}", userId, err.getMessage()));

          return Future.succeededFuture(ApiResponse.success("User fetched successfully", UserResponse.from(user)));
        });
  }

  private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<User> result, int page, int pageSize,
      Function<User, R> mapper, String message) {
    int totalRecords = result.getTotalRecords();
    int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    List<R> data = result.getData().stream().map(mapper).toList();

    return new ApiResponsePagination<>("success", message, data,
        new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
  }

  private void invalidateCache(Integer userId) {
    if (userId != null) {
      redisService.delete("user:" + userId)
          .onSuccess(deleted -> {
            if (deleted > 0) {
              logger.debug("User {} cache invalidated", userId);
            }
          })
          .onFailure(err -> logger.warn("Failed to invalidate cache for user {}: {}", userId, err.getMessage()));
    }
  }
}