package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class UserService {
  private static final Logger logger = LoggerFactory.getLogger(UserService.class);
  private final UserRepository repository;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;
  private final RedisService redisService;
  private final TracingMetrics tracingMetrics;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public UserService(UserRepository repository, RoleRepository roleRepository, UserRoleRepository userRoleRepository,
      RedisService redisService, TracingMetrics tracingMetrics) {
    this.repository = repository;
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
    this.redisService = redisService;
    this.tracingMetrics = tracingMetrics;
  }

  public Future<ApiResponsePagination<List<UserResponse>>> getAllUsers(FindAllUsers req) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("UserService.getAllUsers");
    Span span = Span.fromContext(tracingContext.getContext());

    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

    logger.info("Fetching users | search={}, page={}, pageSize={}", keyword, page, pageSize);

    req.setPage(page);
    req.setPageSize(pageSize);
    req.setSearch(keyword);

    String cacheKey = String.format("users:page:%d:search:%s", page, keyword);

    return redisService.get(cacheKey)
        .compose(cached -> handleCacheOrRepo(
            cached,
            cacheKey,
            () -> repository.getUsers(req),
            new TypeReference<PagedResult<User>>() {
            }, 
            result -> mapUserPagination(result, req, "Users fetched successfully"),
            tracingContext,
            "get_all",
            Duration.ofMinutes(5)))
        .map(response -> {
          span.setAttribute("users.count", response.data().size());
          span.setAttribute("users.total_records", response.pagination().totalRecords());
          return response;
        })
        .onSuccess(v -> tracingMetrics.completeSpanSuccess(tracingContext, "get_all", "Users fetched successfully"))
        .recover(throwable -> {
          logger.error("Failed to fetch users", throwable);
          tracingMetrics.completeSpanError(tracingContext, "get_all", throwable.getMessage());
          return Future.succeededFuture(
              ApiResponsePagination.<List<UserResponse>>error("Failed to fetch users: " + throwable.getMessage()));
        });
  }

  public Future<ApiResponsePagination<List<UserResponseDeleteAt>>> getActiveUsers(FindAllUsers req) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("UserService.getActiveUsers");
    Span span = Span.fromContext(tracingContext.getContext());

    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

    logger.info("Fetching active users | search={}, page={}, pageSize={}", keyword, page, pageSize);

    req.setPage(page);
    req.setPageSize(pageSize);
    req.setSearch(keyword);

    String cacheKey = String.format("users:active:page:%d:search:%s", page, keyword);

    return redisService.get(cacheKey)
        .compose(cached -> handleCacheOrRepo(
            cached,
            cacheKey,
            () -> repository.getActiveUsers(req),
            new TypeReference<PagedResult<User>>() {
            },
            result -> mapUserPaginationDeleteAt(result, req, "Active users fetched successfully"),
            tracingContext,
            "get_active",
            Duration.ofMinutes(5)))
        .map(response -> {
          span.setAttribute("users.count", response.data().size());
          span.setAttribute("users.total_records", response.pagination().totalRecords());
          return response;
        })
        .onSuccess(
            v -> tracingMetrics.completeSpanSuccess(tracingContext, "get_active", "Active users fetched successfully"))
        .recover(throwable -> {
          logger.error("Failed to fetch active users", throwable);
          tracingMetrics.completeSpanError(tracingContext, "get_active", throwable.getMessage());
          return Future.succeededFuture(
              ApiResponsePagination
                  .<List<UserResponseDeleteAt>>error("Failed to fetch active users: " + throwable.getMessage()));
        });
  }

  public Future<ApiResponsePagination<List<UserResponseDeleteAt>>> getTrashedUsers(FindAllUsers req) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("UserService.getTrashedUsers");
    Span span = Span.fromContext(tracingContext.getContext());

    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
    int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
    String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

    logger.info("Fetching trashed users | search={}, page={}, pageSize={}", keyword, page, pageSize);

    req.setPage(page);
    req.setPageSize(pageSize);
    req.setSearch(keyword);

    String cacheKey = String.format("users:trashed:page:%d:search:%s", page, keyword);

    return redisService.get(cacheKey)
        .compose(cached -> handleCacheOrRepo(
            cached,
            cacheKey,
            () -> repository.getTrashedUsers(req),
            new TypeReference<PagedResult<User>>() {
            },
            result -> mapUserPaginationDeleteAt(result, req, "Trashed users fetched successfully"),
            tracingContext,
            "get_trashed",
            Duration.ofMinutes(5)))
        .map(response -> {
          span.setAttribute("users.count", response.data().size());
          span.setAttribute("users.total_records", response.pagination().totalRecords());
          return response;
        })
        .onSuccess(v -> tracingMetrics.completeSpanSuccess(tracingContext, "get_trashed",
            "Trashed users fetched successfully"))
        .recover(throwable -> {
          logger.error("Failed to fetch trashed users", throwable);
          tracingMetrics.completeSpanError(tracingContext, "get_trashed", throwable.getMessage());
          return Future.succeededFuture(
              ApiResponsePagination
                  .<List<UserResponseDeleteAt>>error("Failed to fetch trashed users: " + throwable.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponse>> getById(Integer userId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "UserService.getById",
        io.opentelemetry.api.common.Attributes.builder().put("user.id", userId).build());

    logger.info("Fetching user by id: {}", userId);
    String cacheKey = "user:" + userId;

    return redisService.get(cacheKey)
        .compose(cached -> handleCacheOrRepo(
            cached,
            cacheKey,
            () -> repository.getUserById(userId)
                .map(user -> {
                  if (user == null) {
                    throw new NotFoundException("User not found");
                  }
                  return user;
                }),
            new TypeReference<User>() {
            },
            user -> ApiResponse.success("User fetched successfully", UserResponse.from(user)),
            tracingContext,
            "get_by_id",
            Duration.ofMinutes(60)))
        .recover(err -> {
          logger.error("Failed to fetch user by id: {}", userId, err);
          tracingMetrics.completeSpanError(tracingContext, "get_by_id", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<UserResponse>error("Failed to fetch user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponse>> createUser(CreateUserRequest req) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "UserService.createUser",
        io.opentelemetry.api.common.Attributes.builder()
            .put("user.email", req.getEmail())
            .put("user.firstname", req.getFirstName())
            .put("user.lastname", req.getLastName())
            .build());
    Span span = Span.fromContext(tracingContext.getContext());

    logger.info("Creating user: {} {}, email: {}", req.getFirstName(), req.getLastName(), req.getEmail());

    String hashed = BCrypt.withDefaults().hashToString(12, req.getPassword().toCharArray());
    req.setPassword(hashed);

    return repository.createUser(req)
        .compose((User createdUser) -> {
          logger.info("User created in DB: {}, user_id: {}", createdUser.getEmail(), createdUser.getUserId());
          span.setAttribute("user.user_id", createdUser.getUserId());
          return roleRepository.getRoleByName("ADMIN")
              .compose(role -> {
                if (role == null) {
                  return Future
                      .failedFuture(new IllegalStateException("Default 'ADMIN' role not found in the database."));
                }

                UserRole userRole = new UserRole();
                userRole.setRoleId(role.getRoleId());
                userRole.setUserId(createdUser.getUserId());

                return userRoleRepository.assignRoleToUser(userRole)
                    .map(v -> createdUser);
              });
        })
        .map(createdUser -> {
          logger.info("User created and role assigned successfully: {}, user_id: {}", createdUser.getEmail(),
              createdUser.getUserId());

          tracingMetrics.completeSpanSuccess(tracingContext, "create", "User created successfully");
          return ApiResponse.success("User created successfully", UserResponse.from(createdUser));
        })
        .recover(throwable -> {
          logger.error("Failed to create user: {}", req.getEmail(), throwable);
          tracingMetrics.completeSpanError(tracingContext, "create", throwable.getMessage());
          return Future.succeededFuture(ApiResponse.error("Failed to create user: " + throwable.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponse>> getUserById(Integer userId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "UserService.getUserById",
        io.opentelemetry.api.common.Attributes.builder()
            .put("user.id", userId)
            .build());
    Span span = Span.fromContext(tracingContext.getContext());

    logger.info("Fetching user by id: {}", userId);
    String cacheKey = "user:" + userId;

    return redisService.get(cacheKey)
        .compose(cachedUser -> {
          if (cachedUser != null && !cachedUser.isEmpty()) {
            logger.info("User {} found in cache", userId);
            span.setAttribute("user.cache_hit", true);
            try {
              User user = User.fromJson(new JsonObject(cachedUser));
              tracingMetrics.completeSpanSuccess(tracingContext, "get_by_id", "User fetched from cache");
              return Future.succeededFuture(ApiResponse.success(
                  "User fetched successfully (from cache)",
                  UserResponse.from(user)));
            } catch (Exception e) {
              logger.warn("Failed to parse cached user data for user {}: {}", userId, e.getMessage());
              return fetchUserFromDatabase(userId, tracingContext);
            }
          } else {
            span.setAttribute("user.cache_hit", false);
            return fetchUserFromDatabase(userId, tracingContext);
          }
        })
        .recover(err -> {
          logger.error("Failed to fetch user by id: {}", userId, err);
          tracingMetrics.completeSpanError(tracingContext, "get_by_id", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<UserResponse>error(
                  "Failed to fetch user: " + err.getMessage()));
        });
  }

  private Future<ApiResponse<UserResponse>> fetchUserFromDatabase(Integer userId,
      TracingMetrics.TracingContext tracingContext) {
    Span span = Span.fromContext(tracingContext.getContext());

    return repository.getUserById(userId)
        .compose((User user) -> {
          if (user == null) {
            return Future.failedFuture("User not found");
          }

          span.setAttribute("user.email", user.getEmail());

          String cacheKey = "user:" + userId;
          redisService.setJson(cacheKey, user.toJson(), Duration.ofMinutes(30))
              .onSuccess(v -> logger.debug("User {} cached successfully", userId))
              .onFailure(err -> logger.warn("Failed to cache user {}: {}", userId, err.getMessage()));

          return Future.succeededFuture(ApiResponse.success(
              "User fetched successfully",
              UserResponse.from(user)));
        });
  }

  public Future<ApiResponse<UserResponse>> updateUser(UpdateUserRequest req) {
    Integer userId = req.getUserId();
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "UserService.updateUser",
        io.opentelemetry.api.common.Attributes.builder()
            .put("user.id", userId)
            .put("user.email", req.getEmail())
            .put("user.firstname", req.getFirstName())
            .put("user.lastname", req.getLastName())
            .build());

    logger.info("Updating user: {}, email: {}", userId, req.getEmail());

    return repository.updateUser(req)
        .compose(user -> {
          String cacheKey = "user:" + user.getUserId();
          return redisService.delete(cacheKey)
              .onSuccess(deleted -> {
                if (deleted > 0) {
                  logger.debug("User {} cache invalidated", user.getUserId());
                }
              })
              .onFailure(
                  err -> logger.warn("Failed to invalidate cache for user {}: {}", user.getUserId(), err.getMessage()))
              .map(deleted -> user);
        })
        .map(user -> {
          logger.info("User updated successfully: {}", user.getUserId());
          tracingMetrics.completeSpanSuccess(tracingContext, "update", "User updated successfully");
          return ApiResponse.success(
              "User updated successfully",
              UserResponse.from(user));
        })
        .recover(err -> {
          logger.error("Failed to update user: {}", userId, err);
          tracingMetrics.completeSpanError(tracingContext, "update", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<UserResponse>error(
                  "Failed to update user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponseDeleteAt>> trashed(Integer userId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "UserService.trashed",
        io.opentelemetry.api.common.Attributes.builder()
            .put("user.id", userId)
            .build());

    logger.info("Trashing user: {}", userId);

    return repository.trashed(userId)
        .compose(user -> {
          if (user == null) {
            return Future.failedFuture(new NotFoundException("User not found with id: " + userId));
          }
          String cacheKey = "user:" + userId;
          return redisService.delete(cacheKey)
              .onSuccess(deleted -> {
                if (deleted > 0) {
                  logger.debug("User {} cache invalidated on trash", userId);
                }
              })
              .onFailure(
                  err -> logger.warn("Failed to invalidate cache for trashed user {}: {}", userId, err.getMessage()))
              .map(user);
        })
        .map(user -> {
          logger.info("User trashed successfully: {}", userId);
          tracingMetrics.completeSpanSuccess(tracingContext, "trashed", "User trashed successfully");
          return ApiResponse.success(
              "User trashed successfully",
              UserResponseDeleteAt.from(user));
        })
        .recover(err -> {
          logger.error("Failed to trash user: {}", userId, err);
          tracingMetrics.completeSpanError(tracingContext, "trashed", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<UserResponseDeleteAt>error(
                  "Failed to trash user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponseDeleteAt>> restore(Integer userId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "UserService.restore",
        io.opentelemetry.api.common.Attributes.builder()
            .put("user.id", userId)
            .build());

    logger.info("Restoring user: {}", userId);

    return repository.restore(userId)
        .compose(user -> {
          if (user == null) {
            return Future.failedFuture(new NotFoundException("User not found with id: " + userId));
          }
          String cacheKey = "user:" + userId;
          return redisService.delete(cacheKey)
              .onSuccess(deleted -> {
                if (deleted > 0) {
                  logger.debug("User {} cache invalidated on restore", userId);
                }
              })
              .onFailure(
                  err -> logger.warn("Failed to invalidate cache for restored user {}: {}", userId, err.getMessage()))
              .map(user);
        })
        .map(user -> {
          logger.info("User restored successfully: {}", userId);
          tracingMetrics.completeSpanSuccess(tracingContext, "restore", "User restored successfully");
          return ApiResponse.success(
              "User restored successfully",
              UserResponseDeleteAt.from(user));
        })
        .recover(err -> {
          logger.error("Failed to restore user: {}", userId, err);
          tracingMetrics.completeSpanError(tracingContext, "restore", err.getMessage());
          return Future.succeededFuture(
              ApiResponse.<UserResponseDeleteAt>error(
                  "Failed to restore user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<Void>> deletePermanent(Integer userId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "UserService.deletePermanent",
        io.opentelemetry.api.common.Attributes.builder()
            .put("user.id", userId)
            .build());

    logger.info("Permanently deleting user: {}", userId);

    return repository.deletePermanent(userId)
        .compose(v -> {
          String cacheKey = "user:" + userId;
          return redisService.delete(cacheKey)
              .onSuccess(deleted -> {
                if (deleted > 0) {
                  logger.debug("User {} cache invalidated on permanent delete", userId);
                }
              })
              .onFailure(
                  err -> logger.warn("Failed to invalidate cache for deleted user {}: {}", userId, err.getMessage()))
              .map(v);
        })
        .map(v -> {
          logger.info("User deleted successfully: {}", userId);
          tracingMetrics.completeSpanSuccess(tracingContext, "deletePermanent", "User deleted permanently");
          return ApiResponse.<Void>success("User deleted successfully", null);
        })
        .recover(throwable -> {
          logger.error("Failed to delete user: {}", userId, throwable);
          tracingMetrics.completeSpanError(tracingContext, "deletePermanent", throwable.getMessage());
          return Future.succeededFuture(
              ApiResponse.<Void>error("Failed to delete user: " + throwable.getMessage()));
        });
  }

   public Future<ApiResponse<Integer>> restoreAll() {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("UserService.restoreAll");
    return repository.restoreAllUsers()
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
    return repository.deleteAllPermanentUsers()
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
        return Future.succeededFuture(mapper.apply(data));
      } catch (Exception e) {
        logger.warn("Cache parse error", e);
      }
    }

    try {
      return repoCall.call().map(res -> {
        redisService.set(cacheKey, Json.encode(res), ttl);
        return mapper.apply(res);
      });
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private ApiResponsePagination<List<UserResponse>> mapUserPagination(
      PagedResult<User> result,
      FindAllUsers req,
      String message) {

    int pageSize = req.getPageSize();
    int totalRecords = result.getTotalRecords();
    int totalPages = (int) Math.ceil(
        (double) totalRecords / pageSize);
    List<UserResponse> data = result.getData()
        .stream()
        .map(UserResponse::from)
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

  private ApiResponsePagination<List<UserResponseDeleteAt>> mapUserPaginationDeleteAt(
      PagedResult<User> result,
      FindAllUsers req,
      String message) {

    int pageSize = req.getPageSize();
    int totalRecords = result.getTotalRecords();
    int totalPages = (int) Math.ceil(
        (double) totalRecords / pageSize);
    List<UserResponseDeleteAt> data = result.getData()
        .stream()
        .map(UserResponseDeleteAt::from)
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
