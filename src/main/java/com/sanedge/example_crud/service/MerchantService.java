package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.merchant.CreateMerchantRequest;
import com.sanedge.example_crud.domain.requests.merchant.FindAllMerchants;
import com.sanedge.example_crud.domain.requests.merchant.UpdateMerchantRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.domain.response.merchant.MerchantResponse;
import com.sanedge.example_crud.domain.response.merchant.MerchantResponseDeleteAt;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.model.Merchant;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.MerchantRepository;
import com.sanedge.example_crud.repository.UserRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MerchantService {

    private static final Logger logger = LoggerFactory.getLogger(MerchantService.class);

    private final MerchantRepository repository;
    private final UserRepository userRepository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;

    public Future<ApiResponsePagination<List<MerchantResponse>>> getAll(FindAllMerchants req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.getAll");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedMerchants(req, "merchants:all", page, pageSize, keyword,
                repository::getMerchants, MerchantResponse::from, ctx, "get_all",
                "Merchants fetched successfully");
    }

    public Future<ApiResponsePagination<List<MerchantResponse>>> getActive(FindAllMerchants req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.getActive");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedMerchants(req, "merchants:active", page, pageSize, keyword,
                repository::getMerchantsActive, MerchantResponse::from, ctx, "get_active",
                "Active merchants fetched successfully");
    }

    public Future<ApiResponsePagination<List<MerchantResponseDeleteAt>>> getTrashed(FindAllMerchants req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.getTrashed");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedMerchants(req, "merchants:trashed", page, pageSize, keyword,
                repository::getMerchantsTrashed, MerchantResponseDeleteAt::from, ctx, "get_trashed",
                "Trashed merchants fetched successfully");
    }

    public Future<ApiResponse<MerchantResponse>> getById(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.getById",
                Attributes.builder().put("id", id).build());
        Span span = Span.fromContext(ctx.getContext());

        String cacheKey = "merchant:" + id;

        return redisService.get(cacheKey)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        span.setAttribute("cache.hit", true);
                        try {
                            Merchant merchant = Merchant.fromJson(new JsonObject(cached));
                            tracingMetrics.completeSpanSuccess(ctx, "get_by_id", "Merchant fetched from cache");
                            return Future.succeededFuture(
                                    ApiResponse.success("Merchant fetched successfully (from cache)",
                                            MerchantResponse.from(merchant)));
                        } catch (Exception e) {
                            logger.warn("Failed to parse cached merchant data for merchant {}: {}", id, e.getMessage());
                            return fetchMerchantFromDatabase(id, ctx);
                        }
                    }
                    span.setAttribute("cache.hit", false);
                    return fetchMerchantFromDatabase(id, ctx);
                })
                .recover(err -> {
                    logger.error("Failed to fetch merchant by id: {}", id, err);
                    tracingMetrics.completeSpanError(ctx, "get_by_id", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponse>error("Failed to fetch merchant: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponse>> create(CreateMerchantRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.create",
                Attributes.builder().put("merchant.name", req.getName()).build());
        Span span = Span.fromContext(ctx.getContext());

        logger.info("Creating merchant: {}", req.getName());

        return userRepository.getUserById(req.getUserId())
                .compose(user -> {
                    if (user == null) {
                        return Future.failedFuture(new NotFoundException("User not found"));
                    }
                    return repository.createMerchant(req);
                })
                .map(data -> {
                    span.setAttribute("id", data.getMerchantId());
                    tracingMetrics.completeSpanSuccess(ctx, "create", "Success");
                    return ApiResponse.success("Merchant created", MerchantResponse.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to create merchant", err);
                    tracingMetrics.completeSpanError(ctx, "create", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponse>error("Failed to create: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponse>> update(UpdateMerchantRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.update",
                Attributes.builder().put("id", req.getMerchantId()).build());

        return userRepository.getUserById(req.getUserId())
                .compose(user -> {
                    if (user == null) {
                        return Future.failedFuture(new NotFoundException("User not found"));
                    }
                    return repository.updateMerchant(req);
                })
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Merchant not found"));
                    }
                    invalidateCache(req.getMerchantId().longValue());
                    tracingMetrics.completeSpanSuccess(ctx, "update", "Success");
                    return Future.succeededFuture(ApiResponse.success("Merchant updated", MerchantResponse.from(data)));
                })
                .recover(err -> {
                    logger.error("Failed to update merchant: {}", req.getMerchantId(), err);
                    tracingMetrics.completeSpanError(ctx, "update", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponse>error("Failed to update: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponseDeleteAt>> trash(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.trash",
                Attributes.builder().put("id", id).build());

        return repository.trashMerchant(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Merchant not found"));
                    }
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "trash", "Success");
                    return Future.succeededFuture(
                            ApiResponse.success("Merchant trashed", MerchantResponseDeleteAt.from(data)));
                })
                .recover(err -> {
                    logger.error("Failed to trash merchant: {}", id, err);
                    tracingMetrics.completeSpanError(ctx, "trash", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponseDeleteAt>error("Failed to trash: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponseDeleteAt>> restore(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.restore",
                Attributes.builder().put("id", id).build());

        return repository.findByTrashed(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Merchant not found"));
                    }
                    return repository.restoreMerchant(id);
                })
                .compose(data -> {
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "restore", "Success");
                    return Future.succeededFuture(
                            ApiResponse.success("Merchant restored", MerchantResponseDeleteAt.from(data)));
                })
                .recover(err -> {
                    logger.error("Failed to restore merchant: {}", id, err);
                    tracingMetrics.completeSpanError(ctx, "restore", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponseDeleteAt>error("Failed to restore: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> deletePermanent(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.deletePermanent",
                Attributes.builder().put("id", id).build());

        return repository.findByTrashed(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Merchant not found"));
                    }
                    return repository.deleteMerchantPermanently(id);
                })
                .map(v -> {
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "delete_permanent", "Success");
                    return ApiResponse.success("Merchant deleted permanently", true);
                })
                .recover(err -> {
                    logger.error("Failed to delete merchant: {}", id, err);
                    tracingMetrics.completeSpanError(ctx, "delete_permanent", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(ApiResponse.<Boolean>error("Failed to delete: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> restoreAll() {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.restoreAll");
        return repository.restoreAllMerchants()
                .compose(count -> {
                    if (count == 0) {
                        return Future.failedFuture(new NotFoundException("No trashed merchants found"));
                    }
                    tracingMetrics.completeSpanSuccess(ctx, "restore_all", "Success");
                    return Future.succeededFuture(ApiResponse.success("All merchants restored", count));
                })
                .recover(err -> {
                    logger.error("Failed to restore all", err);
                    tracingMetrics.completeSpanError(ctx, "restore_all", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future
                            .succeededFuture(ApiResponse.<Integer>error("Failed to restore all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> deleteAllPermanent() {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("MerchantService.deleteAllPermanent");
        return repository.deleteAllPermanentMerchants()
                .compose(count -> {
                    if (count == 0) {
                        return Future.failedFuture(new NotFoundException("No trashed merchants found"));
                    }
                    tracingMetrics.completeSpanSuccess(ctx, "delete_all", "Success");
                    return Future.succeededFuture(ApiResponse.success("All merchants deleted permanently", count));
                })
                .recover(err -> {
                    logger.error("Failed to delete all", err);
                    tracingMetrics.completeSpanError(ctx, "delete_all", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future
                            .succeededFuture(ApiResponse.<Integer>error("Failed to delete all: " + err.getMessage()));
                });
    }

    private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedMerchants(T req, String cachePrefix,
            int page, int pageSize, String keyword,
            Function<T, Future<PagedResult<Merchant>>> dbFetcher,
            Function<Merchant, R> responseMapper, TracingMetrics.TracingContext tracingContext,
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
                                    .map(obj -> responseMapper.apply(Merchant.fromJson((JsonObject) obj)))
                                    .toList();

                            tracingMetrics.completeSpanSuccess(tracingContext, spanName,
                                    "Merchants fetched from cache");
                            return Future.succeededFuture(new ApiResponsePagination<>("success", successMessage, data,
                                    new PaginationMeta(page + 1, pageSize, totalPages, totalRecords)));
                        } catch (Exception e) {
                            logger.warn("Failed to parse cached paginated merchants: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("cache.hit", false);
                    return dbFetcher.apply(req)
                            .map(result -> {
                                JsonObject jsonToCache = new JsonObject()
                                        .put("totalRecords", result.getTotalRecords())
                                        .put("data", new JsonArray(
                                                result.getData().stream().map(Merchant::toJson).toList()));

                                redisService.set(cacheKey, jsonToCache.encode(), Duration.ofMinutes(10))
                                        .onFailure(err -> logger.warn("Failed to cache {}: {}", cachePrefix,
                                                err.getMessage()));

                                span.setAttribute("records.count", result.getData().size());
                                span.setAttribute("records.total", result.getTotalRecords());
                                tracingMetrics.completeSpanSuccess(tracingContext, spanName, successMessage);

                                return mapPagination(result, page, pageSize, responseMapper, successMessage);
                            });
                })
                .recover(throwable -> {
                    logger.error("Failed to fetch paginated merchants for {}", cachePrefix, throwable);
                    tracingMetrics.completeSpanError(tracingContext, spanName, throwable.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination.<List<R>>error("Failed to fetch data: " + throwable.getMessage()));
                });
    }

    private Future<ApiResponse<MerchantResponse>> fetchMerchantFromDatabase(Long id,
            TracingMetrics.TracingContext tracingContext) {
        Span span = Span.fromContext(tracingContext.getContext());

        return repository.getMerchantById(id)
                .compose(data -> {
                    if (data == null)
                        return Future.failedFuture(new NotFoundException("Merchant not found"));

                    span.setAttribute("merchant.id", data.getMerchantId());

                    redisService.setJson("merchant:" + id, data.toJson(), Duration.ofMinutes(10))
                            .onFailure(err -> logger.warn("Failed to cache merchant {}: {}", id, err.getMessage()));

                    return Future.succeededFuture(
                            ApiResponse.success("Merchant fetched successfully", MerchantResponse.from(data)));
                });
    }

    private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<Merchant> result, int page, int pageSize,
            Function<Merchant, R> mapper, String message) {
        int totalRecords = result.getTotalRecords();
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        List<R> data = result.getData().stream().map(mapper).toList();

        return new ApiResponsePagination<>("success", message, data,
                new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
    }

    private void invalidateCache(Long merchantId) {
        if (merchantId != null) {
            redisService.delete("merchant:" + merchantId)
                    .onSuccess(deleted -> {
                        if (deleted > 0)
                            logger.debug("Cache merchant:{} invalidated successfully", merchantId);
                    })
                    .onFailure(err -> logger.warn("Failed to invalidate cache for merchant {}: {}", merchantId,
                            err.getMessage()));
        }
    }
}