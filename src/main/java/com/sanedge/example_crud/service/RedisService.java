package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;

public class RedisService {
  private static final Logger logger = LoggerFactory.getLogger(RedisService.class.getName());

  private final RedisAPI redisAPI;
  private final Tracer tracer;
  private final Meter meter;
  private final LongCounter cacheHitCounter;
  private final LongCounter cacheMissCounter;
  private final LongCounter cacheSetCounter;

  public RedisService(RedisAPI redisAPI, OpenTelemetry openTelemetry) {
    this.redisAPI = redisAPI;
    this.tracer = openTelemetry.getTracer(RedisService.class.getName());
    this.meter = openTelemetry.getMeter(RedisService.class.getName());

    this.cacheHitCounter = meter.counterBuilder("redis.cache.hits")
        .setDescription("Number of cache hits")
        .setUnit("1")
        .build();

    this.cacheMissCounter = meter.counterBuilder("redis.cache.misses")
        .setDescription("Number of cache misses")
        .setUnit("1")
        .build();

    this.cacheSetCounter = meter.counterBuilder("redis.cache.sets")
        .setDescription("Number of cache sets")
        .setUnit("1")
        .build();
  }

  public Future<String> get(String key) {
    Span span = tracer.spanBuilder("redis.get")
        .setAttribute("redis.key", key)
        .startSpan();

    return redisAPI.get(key)
        .onSuccess(response -> {
          if (response != null && !response.toString().isEmpty()) {
            cacheHitCounter.add(1);
            logger.debug("Cache hit for key: {}", key);
          } else {
            cacheMissCounter.add(1);
            logger.debug("Cache miss for key: {}", key);
          }
        })
        .onFailure(err -> {
          logger.error("Redis GET error for key {}: {}", key, err.getMessage());
          span.recordException(err);
        })
        .map(response -> response != null ? response.toString() : null)
        .onComplete(ar -> span.end());
  }

  public Future<String> set(String key, String value) {
    return set(key, value, null);
  }

  public Future<String> set(String key, String value, Duration ttl) {
    Span span = tracer.spanBuilder("redis.set")
        .setAttribute("redis.key", key)
        .setAttribute("redis.ttl_seconds", ttl != null ? ttl.getSeconds() : 0)
        .startSpan();

    List<String> args = ttl != null
        ? Arrays.asList(key, value, "EX", String.valueOf(ttl.getSeconds()))
        : Arrays.asList(key, value);

    return redisAPI.set(args)
        .onSuccess(response -> {
          cacheSetCounter.add(1);
          logger.debug("Cache set for key: {} with TTL: {} seconds", key,
              ttl != null ? ttl.getSeconds() : "none");
        })
        .onFailure(err -> {
          logger.error("Redis SET error for key {}: {}", key, err.getMessage());
          span.recordException(err);
        })
        .map(response -> response.toString())
        .onComplete(ar -> span.end());
  }

  public Future<Long> delete(String key) {
    Span span = tracer.spanBuilder("redis.delete")
        .setAttribute("redis.key", key)
        .startSpan();

    return redisAPI.del(List.of(key))
        .onSuccess(response -> {
          logger.debug("Deleted key: {}", key);
        })
        .onFailure(err -> {
          logger.error("Redis DELETE error for key {}: {}", key, err.getMessage());
          span.recordException(err);
        })
        .map(response -> response.toLong())
        .onComplete(ar -> span.end());
  }

  public Future<Boolean> exists(String key) {
    Span span = tracer.spanBuilder("redis.exists")
        .setAttribute("redis.key", key)
        .startSpan();

    return redisAPI.exists(List.of(key))
        .onSuccess(response -> {
          logger.debug("Exists check for key: {} = {}", key, response.toLong() > 0);
        })
        .onFailure(err -> {
          logger.error("Redis EXISTS error for key {}: {}", key, err.getMessage());
          span.recordException(err);
        })
        .map(response -> response.toLong() > 0)
        .onComplete(ar -> span.end());
  }

  public <T> Future<List<T>> getJsonList(String key, Class<T> clazz) {
    Span span = tracer.spanBuilder("redis.getJsonList")
        .setAttribute("redis.key", key)
        .startSpan();

    return redisAPI.exists(List.of(key))
        .compose(existsResult -> {
          if (existsResult.toInteger() == 0) {
            cacheMissCounter.add(1);
            logger.debug("Cache miss for list key: {}", key);
            return Future.succeededFuture(new ArrayList<T>());
          }
          return redisAPI.lrange(key, "0", "-1")
              .map(response -> {
                List<T> result = new ArrayList<>();
                if (response != null) {
                  for (int i = 0; i < response.size(); i++) {
                    try {
                      String jsonStr = response.get(i).toString();
                      T item = Json.decodeValue(jsonStr, clazz);
                      result.add(item);
                    } catch (Exception e) {
                      logger.error("Failed to parse JSON item from list key {}: {}", key, e.getMessage());
                    }
                  }
                }
                return result;
              });
        })
        .onSuccess(result -> {
          if (!result.isEmpty()) {
            cacheHitCounter.add(1);
            logger.debug("Cache hit for list key: {} with {} items", key, result.size());
          }
        })
        .onFailure(err -> {
          logger.error("Redis GET JSON LIST error for key {}: {}", key, err.getMessage());
          span.recordException(err);
        })
        .onComplete(ar -> span.end());
  }

  public <T> Future<Void> setJsonList(String key, List<T> values, Duration ttl) {
    Span span = tracer.spanBuilder("redis.setJsonList")
        .setAttribute("redis.key", key)
        .setAttribute("redis.list_size", values.size())
        .setAttribute("redis.ttl_seconds", ttl != null ? ttl.getSeconds() : 0)
        .startSpan();

    if (values.isEmpty()) {
      logger.debug("Skipping cache set for empty list key: {}", key);
      span.end();
      return Future.succeededFuture();
    }

    return redisAPI.del(List.of(key))
        .<Void>compose(delResult -> {
          List<String> jsonValues = new ArrayList<>();
          for (T value : values) {
            try {
              jsonValues.add(Json.encode(value));
            } catch (Exception e) {
              logger.error("Failed to encode JSON for list item: {}", e.getMessage());
              return Future.failedFuture(e);
            }
          }

          List<String> rpushArgs = new ArrayList<>();
          rpushArgs.add(key);
          rpushArgs.addAll(jsonValues);

          return redisAPI.rpush(rpushArgs)
              .compose(pushResult -> {
                if (ttl != null) {
                  return redisAPI.expire(List.of(key, String.valueOf(ttl.getSeconds())))
                      .mapEmpty();
                }
                return Future.succeededFuture();
              });
        })
        .onSuccess(v -> {
          cacheSetCounter.add(1);
          logger.debug("Cache set for list key: {} with {} items and TTL: {} seconds",
              key, values.size(), ttl != null ? ttl.getSeconds() : "none");
        })
        .onFailure(err -> {
          logger.error("Redis SET JSON LIST error for key {}: {}", key, err.getMessage());
          span.recordException(err);
        })
        .onComplete(ar -> span.end());
  }

  public Future<String> setJson(String key, JsonObject value, Duration ttl) {
    return set(key, value.encode(), ttl);
  }

  public Future<JsonObject> getJson(String key) {
    return get(key)
        .compose(jsonStr -> {
          if (jsonStr == null || jsonStr.isEmpty()) {
            return Future.succeededFuture(null);
          }
          try {
            return Future.succeededFuture(new JsonObject(jsonStr));
          } catch (Exception e) {
            logger.error("Failed to parse JSON from Redis for key {}: {}", key, e.getMessage());
            return Future.failedFuture(e);
          }
        });
  }

  public Future<String> ping() {
    Span span = tracer.spanBuilder("redis.ping").startSpan();

    return redisAPI.ping(Collections.emptyList())
        .onSuccess(response -> {
          logger.debug("Redis PING response: {}", response.toString());
        })
        .onFailure(err -> {
          logger.error("Redis PING error: {}", err.getMessage());
          span.recordException(err);
        })
        .map(response -> response.toString())
        .onComplete(ar -> span.end());
  }
}
