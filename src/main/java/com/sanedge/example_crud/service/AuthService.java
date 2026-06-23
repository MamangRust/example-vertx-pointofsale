package com.sanedge.example_crud.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.user.CreateUserRequest;
import com.sanedge.example_crud.domain.response.TokenResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.user.UserResponse;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.model.Role;
import com.sanedge.example_crud.model.User;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.RefreshTokenRepository;
import com.sanedge.example_crud.repository.UserRepository;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthService {

  private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository repo;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RedisService redisService;
  private final JWTAuth jwtProvider;
  private final TracingMetrics tracingMetrics;

  public Future<ApiResponse<TokenResponse>> login(String email, String password) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "AuthService.login",
        Attributes.builder().put("auth.email", email).build());

    Span span = Span.fromContext(tracingContext.getContext());

    // Selalu ambil dari DB karena kita perlu verifikasi password secara real-time
    return repo.getUserByEmailWithRoles(email)
        .compose(user -> {
          if (user == null) {
            return Future.failedFuture(new CustomException("Invalid email or password"));
          }

          BCrypt.Result res = BCrypt.verifyer().verify(password.toCharArray(), user.getPassword());
          if (!res.verified) {
            return Future.failedFuture(new CustomException("Invalid email or password"));
          }

          span.setAttribute("auth.user_id", user.getUserId());

          // Update user cache untuk keperluan lookup lain jika diperlukan
          String userCacheKey = "user:email:" + email;
          JsonObject userCache = new JsonObject()
              .put("userId", user.getUserId())
              .put("email", user.getEmail())
              .put("roles", user.getRoles().stream().map(Role::getRoleName).toList());

          redisService.set(userCacheKey, userCache.encode(), Duration.ofMinutes(10))
              .onFailure(err -> logger.warn("Failed to cache user {}: {}", email, err.getMessage()));

          String accessToken = generateAccessToken(user);
          String jti = UUID.randomUUID().toString();
          String refreshTokenStr = generateRefreshToken(user.getUserId(), jti);
          LocalDateTime refreshExpiry = LocalDateTime.now().plusDays(7);

          return refreshTokenRepository.deleteByUserId(user.getUserId())
              .recover(err -> {
                logger.warn("Failed to delete old refresh token for user {}: {}",
                    user.getUserId(), err.getMessage());
                return Future.succeededFuture();
              })
              .compose(v -> refreshTokenRepository.create(user.getUserId(), refreshTokenStr, refreshExpiry))
              .recover(err -> {
                logger.error("Failed to create refresh token - database schema issue: {}",
                    err.getMessage());
                return Future
                    .failedFuture(new CustomException("Database configuration error. Please contact administrator."));
              })
              .compose(rt -> {
                String sessionCacheKey = "session:" + user.getUserId();
                JsonObject sessionData = new JsonObject()
                    .put("userId", user.getUserId())
                    .put("email", user.getEmail())
                    .put("accessToken", accessToken)
                    .put("refreshToken", rt.getToken())
                    .put("roles", user.getRoles().stream().map(Role::getRoleName).toList());

                return redisService.set(sessionCacheKey, sessionData.encode(), Duration.ofHours(1))
                    .map(v -> rt);
              })
              .map(rt -> {
                TokenResponse tokenResponse = TokenResponse.builder()
                    .access_token(accessToken)
                    .refresh_token(rt.getToken())
                    .build();

                tracingMetrics.completeSpanSuccess(tracingContext, "login", "Login success");
                return ApiResponse.success("Login success", tokenResponse);
              });
        })
        .recover(err -> {
          logger.error("Login failed for email: {}", email, err);
          tracingMetrics.completeSpanError(tracingContext, "login", err.getMessage());
          if (err instanceof CustomException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<TokenResponse>error("Failed to login: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<UserResponse>> register(CreateUserRequest user) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "AuthService.register",
        Attributes.builder()
            .put("user.email", user.getEmail())
            .put("user.firstname", user.getFirstName())
            .put("user.lastname", user.getLastName())
            .build());

    logger.info("Registration attempt for email: {}", user.getEmail());

    String hashed = BCrypt.withDefaults().hashToString(12, user.getPassword().toCharArray());
    user.setPassword(hashed);

    return repo.createUser(user)
        .map(createdUser -> {
          UserResponse userResponse = UserResponse.from(createdUser);

          Span.fromContext(tracingContext.getContext())
              .setAttribute("auth.user_id", createdUser.getUserId());

          tracingMetrics.completeSpanSuccess(tracingContext, "register", "User created");

          return ApiResponse.success("User created", userResponse);
        })
        .recover(err -> {
          logger.error("Registration failed for email: {}", user.getEmail(), err);
          tracingMetrics.completeSpanError(tracingContext, "register", err.getMessage());
          if (err instanceof CustomException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<UserResponse>error("Failed to register user: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<String>> logout(Integer userId) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
        "AuthService.logout",
        Attributes.builder().put("auth.user_id", userId).build());

    logger.info("Attempting to logout user: {}", userId);

    String sessionCacheKey = "session:" + userId;

    return refreshTokenRepository.deleteByUserId(userId)
        .compose(v -> redisService.delete(sessionCacheKey))
        .map(deletedCount -> {
          logger.info("User {} logged out successfully. {} cache keys deleted.", userId, deletedCount);
          tracingMetrics.completeSpanSuccess(tracingContext, "logout", "Logged out successfully");
          return ApiResponse.success("Logged out successfully", "Session and refresh tokens cleared");
        })
        .recover(err -> {
          logger.error("Failed to logout user: {}", userId, err);
          tracingMetrics.completeSpanError(tracingContext, "logout", err.getMessage());
          if (err instanceof CustomException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<String>error("Failed to logout: " + err.getMessage()));
        });
  }

  public Future<ApiResponse<TokenResponse>> refreshToken(String refreshTokenStr) {
    TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("AuthService.refreshToken");
    Span span = Span.fromContext(tracingContext.getContext());

    return refreshTokenRepository.findByToken(refreshTokenStr)
        .compose(refreshToken -> {
          if (refreshToken == null) {
            return Future.failedFuture(new CustomException("Invalid or expired refresh token"));
          }

          LocalDateTime now = LocalDateTime.now();
          LocalDateTime expiry = refreshToken.getExpiration();
          boolean needsRenewal = expiry.minusDays(1).isBefore(now);

          return repo.getUserByIdWithRoles(refreshToken.getUserId())
              .compose(user -> {
                if (user == null) {
                  return Future.failedFuture(new CustomException("User not found"));
                }

                span.setAttribute("auth.user_id", user.getUserId());
                span.setAttribute("auth.renewed", needsRenewal);

                String accessToken = generateAccessToken(user);
                Future<Void> renewalFuture;
                String finalRefreshTokenStr;

                if (needsRenewal) {
                  String jti = UUID.randomUUID().toString();
                  finalRefreshTokenStr = generateRefreshToken(user.getUserId(), jti);
                  LocalDateTime refreshExpiry = LocalDateTime.now().plusDays(7);

                  renewalFuture = refreshTokenRepository.deleteByUserId(refreshToken.getUserId())
                      .compose(
                          v -> refreshTokenRepository.create(user.getUserId(), finalRefreshTokenStr, refreshExpiry))
                      .mapEmpty();
                } else {
                  finalRefreshTokenStr = refreshTokenStr;
                  renewalFuture = Future.succeededFuture();
                }

                return renewalFuture.compose(v -> {
                  String sessionCacheKey = "session:" + user.getUserId();
                  JsonObject sessionData = new JsonObject()
                      .put("userId", user.getUserId())
                      .put("email", user.getEmail())
                      .put("accessToken", accessToken)
                      .put("refreshToken", finalRefreshTokenStr)
                      .put("roles", user.getRoles().stream().map(Role::getRoleName).toList());

                  redisService.set(sessionCacheKey, sessionData.encode(), Duration.ofHours(1))
                      .onFailure(err -> logger.warn("Failed to cache updated session: {}", err.getMessage()));

                  TokenResponse tokenResponse = TokenResponse.builder()
                      .access_token(accessToken)
                      .refresh_token(finalRefreshTokenStr)
                      .build();

                  tracingMetrics.completeSpanSuccess(tracingContext, "refresh_token", "Token refreshed successfully");
                  return Future.succeededFuture(ApiResponse.success("Token refreshed successfully", tokenResponse));
                });
              });
        })
        .recover(err -> {
          logger.error("Failed to refresh token", err);
          tracingMetrics.completeSpanError(tracingContext, "refresh_token", err.getMessage());
          if (err instanceof CustomException) {
            return Future.failedFuture(err);
          }
          return Future.succeededFuture(
              ApiResponse.<TokenResponse>error("Failed to refresh token: " + err.getMessage()));
        });
  }

  private String generateAccessToken(User user) {
    return jwtProvider.generateToken(
        new JsonObject()
            .put("sub", "access")
            .put("userId", user.getUserId())
            .put("email", user.getEmail())
            .put("roleNames", user.getRoles().stream().map(Role::getRoleName).toList()),
        new JWTOptions().setExpiresInMinutes(60));
  }

  private String generateRefreshToken(Integer userId, String jti) {
    return jwtProvider.generateToken(
        new JsonObject()
            .put("sub", "refresh")
            .put("userId", userId)
            .put("jti", jti),
        new JWTOptions().setExpiresInMinutes(60 * 24 * 7));
  }
}