package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.Json;

public class MerchantService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantService.class);

    private final MerchantRepository repository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MerchantService(
            MerchantRepository repository,
            RedisService redisService,
            TracingMetrics tracingMetrics) {
        this.repository = repository;
        this.redisService = redisService;
        this.tracingMetrics = tracingMetrics;
    }

    public Future<ApiResponsePagination<List<MerchantResponse>>> getAll(FindAllMerchants req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.getAll");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching all merchants | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("merchants:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);

                    return handleCacheOrRepo(
                            cached,
                            cacheKey,
                            () -> repository.getMerchants(req),
                            new TypeReference<PagedResult<Merchant>>() {
                            },
                            result -> mapToPagedResponse(result, req),
                            tracingContext,
                            "get_all",
                            Duration.ofMinutes(10));
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total", response.pagination().totalRecords());
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch all merchants", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_all", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination
                                    .<List<MerchantResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<MerchantResponse>>> getActive(FindAllMerchants req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.getActive");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching active merchants | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("merchants:active:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);

                    return handleCacheOrRepo(
                            cached,
                            cacheKey,
                            () -> repository.getMerchantsActive(req),
                            new TypeReference<PagedResult<Merchant>>() {
                            },
                            result -> mapToPagedResponse(result, req), 
                            tracingContext,
                            "get_active",
                            Duration.ofMinutes(10));
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total", response.pagination().totalRecords());
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch active merchants", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_active", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination
                                    .<List<MerchantResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<MerchantResponseDeleteAt>>> getTrashed(FindAllMerchants req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.getTrashed");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching trashed merchants | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("merchants:trashed:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);

                    return handleCacheOrRepo(
                            cached,
                            cacheKey,
                            () -> repository.getMerchantsTrashed(req),
                            new TypeReference<PagedResult<Merchant>>() {
                            },
                            result -> mapToPagedResponseDeleteAt(result, req), 
                            tracingContext,
                            "get_trashed",
                            Duration.ofMinutes(10));
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total", response.pagination().totalRecords());
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch trashed merchants", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_trashed", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination.<List<MerchantResponseDeleteAt>>error(
                                    "Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponse>> getById(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
                "MerchantService.getById",
                io.opentelemetry.api.common.Attributes.builder().put("id", id).build());
        Span span = Span.fromContext(tracingContext.getContext());

        logger.info("Fetching merchant by id: {}", id);
        String cacheKey = "merchant:" + id;

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);

                    return handleCacheOrRepo(
                            cached,
                            cacheKey,
                            () -> repository.getMerchantById(id).map(data -> {
                                if (data == null)
                                    throw new NotFoundException("Merchant not found");
                                return data;
                            }),
                            new TypeReference<Merchant>() {
                            },
                            data -> ApiResponse.success("Success", MerchantResponse.from(data)),
                            tracingContext,
                            "get_by_id",
                            Duration.ofMinutes(10));
                })
                .recover(err -> {
                    logger.error("Failed to fetch by id", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_by_id", err.getMessage());
                    return Future.succeededFuture(ApiResponse.<MerchantResponse>error(err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponse>> create(CreateMerchantRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.create");
        Span span = Span.fromContext(tracingContext.getContext());

        logger.info("Creating merchant: {}", req.getName());

        return repository.createMerchant(req)
                .map(data -> {
                    span.setAttribute("id", data.getMerchantId());
                    tracingMetrics.completeSpanSuccess(tracingContext, "create", "Success");
                    return ApiResponse.success("Merchant created", MerchantResponse.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to create merchant", err);
                    tracingMetrics.completeSpanError(tracingContext, "create", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponse>error("Failed to create: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponse>> update(UpdateMerchantRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.update");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", req.getMerchantId());

        return repository.updateMerchant(req)
                .compose(data -> {
                    if (data == null) {
                        return Future.<Merchant>failedFuture(new NotFoundException("Merchant not found"));
                    }
                    return redisService.delete("merchant:" + req.getMerchantId()).map(data);
                })
                .map(data -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "update", "Success");
                    return ApiResponse.success("Merchant updated", MerchantResponse.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to update merchant", err);
                    tracingMetrics.completeSpanError(tracingContext, "update", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponse>error("Failed to update: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponseDeleteAt>> trash(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.trash");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return repository.trashMerchant(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.<Merchant>failedFuture(new NotFoundException("Merchant not found"));
                    }
                    return redisService.delete("merchant:" + id).map(data);
                })
                .map(data -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "trash", "Success");
                    return ApiResponse.success("Merchant trashed", MerchantResponseDeleteAt.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to trash merchant", err);
                    tracingMetrics.completeSpanError(tracingContext, "trash", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponseDeleteAt>error("Failed to trash: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<MerchantResponseDeleteAt>> restore(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.restore");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return repository.restoreMerchant(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.<Merchant>failedFuture(new NotFoundException("Merchant not found"));
                    }
                    return redisService.delete("merchant:" + id).map(data);
                })
                .map(data -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "restore", "Success");
                    return ApiResponse.success("Merchant restored", MerchantResponseDeleteAt.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to restore merchant", err);
                    tracingMetrics.completeSpanError(tracingContext, "restore", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<MerchantResponseDeleteAt>error("Failed to restore: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> deletePermanent(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.deletePermanent");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return repository.deleteMerchantPermanently(id)
                .compose(v -> redisService.delete("merchant:" + id).map(v))
                .map(v -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "delete_permanent", "Success");
                    return ApiResponse.success("Merchant deleted permanently", true);
                })
                .recover(err -> {
                    logger.error("Failed to delete merchant", err);
                    tracingMetrics.completeSpanError(tracingContext, "delete_permanent", err.getMessage());
                    return Future.succeededFuture(ApiResponse.<Boolean>error("Failed to delete: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> restoreAll() {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.restoreAll");
        return repository.restoreAllMerchants()
                .map(count -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "restore_all", "Success");
                    return ApiResponse.success("All merchants restored", count);
                })
                .recover(err -> {
                    logger.error("Failed to restore all", err);
                    tracingMetrics.completeSpanError(tracingContext, "restore_all", err.getMessage());
                    return Future
                            .succeededFuture(ApiResponse.<Integer>error("Failed to restore all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> deleteAllPermanent() {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("MerchantService.deleteAllPermanent");
        return repository.deleteAllPermanentMerchants()
                .map(count -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "delete_all", "Success");
                    return ApiResponse.success("All merchants deleted permanently", count);
                })
                .recover(err -> {
                    logger.error("Failed to delete all", err);
                    tracingMetrics.completeSpanError(tracingContext, "delete_all", err.getMessage());
                    return Future
                            .succeededFuture(ApiResponse.<Integer>error("Failed to delete all: " + err.getMessage()));
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

    private ApiResponsePagination<List<MerchantResponse>> mapToPagedResponse(PagedResult<Merchant> result,
            FindAllMerchants req) {
        List<MerchantResponse> data = result.getData().stream()
                .map(MerchantResponse::from)
                .collect(Collectors.toList());
        return new ApiResponsePagination<>(
                "success", "Data fetched", data,
                new PaginationMeta(req.getPage(), req.getPageSize(),
                        (int) Math.ceil((double) result.getTotalRecords() / req.getPageSize()),
                        result.getTotalRecords()));
    }

    private ApiResponsePagination<List<MerchantResponseDeleteAt>> mapToPagedResponseDeleteAt(
            PagedResult<Merchant> result, FindAllMerchants req) {
        List<MerchantResponseDeleteAt> data = result.getData().stream()
                .map(MerchantResponseDeleteAt::from)
                .collect(Collectors.toList());
        return new ApiResponsePagination<>(
                "success", "Data fetched", data,
                new PaginationMeta(req.getPage(), req.getPageSize(),
                        (int) Math.ceil((double) result.getTotalRecords() / req.getPageSize()),
                        result.getTotalRecords()));
    }
}