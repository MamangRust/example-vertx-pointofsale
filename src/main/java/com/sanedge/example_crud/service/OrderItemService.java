package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.example_crud.domain.requests.order.FindAllOrderRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.domain.response.orderitem.OrderItemResponse;
import com.sanedge.example_crud.domain.response.orderitem.OrderItemResponseDeleteAt;
import com.sanedge.example_crud.model.order.OrderItem;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.OrderItemRepository;

import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.Json;

public class OrderItemService {
    private static final Logger logger = LoggerFactory.getLogger(OrderItemService.class);

    private final OrderItemRepository repository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderItemService(
            OrderItemRepository repository,
            RedisService redisService,
            TracingMetrics tracingMetrics) {
        this.repository = repository;
        this.redisService = redisService;
        this.tracingMetrics = tracingMetrics;
    }

    public Future<ApiResponsePagination<List<OrderItemResponse>>> getAll(FindAllOrderRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("OrderItemService.getAll");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching all order items | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("order_items:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached,
                            cacheKey,
                            () -> repository.getOrderItems(req),
                            new TypeReference<PagedResult<OrderItem>>() {
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
                    logger.error("Failed to fetch order items", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_all", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination
                                    .<List<OrderItemResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<OrderItemResponse>>> getActive(FindAllOrderRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("OrderItemService.getActive");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching active order items | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("order_items:active:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached,
                            cacheKey,
                            () -> repository.getOrderItemsActive(req),
                            new TypeReference<PagedResult<OrderItem>>() {
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
                    logger.error("Failed to fetch active order items", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_active", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination
                                    .<List<OrderItemResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<OrderItemResponseDeleteAt>>> getTrashed(FindAllOrderRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("OrderItemService.getTrashed");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching trashed order items | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("order_items:trashed:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached,
                            cacheKey,
                            () -> repository.getOrderItemsTrashed(req),
                            new TypeReference<PagedResult<OrderItem>>() {
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
                    logger.error("Failed to fetch trashed order items", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_trashed", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination.<List<OrderItemResponseDeleteAt>>error(
                                    "Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<List<OrderItemResponse>>> getByOrderId(Integer orderId) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("OrderItemService.getByOrderId");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("order.id", orderId);

        String cacheKey = String.format("order_items:order:%d", orderId);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached,
                            cacheKey,
                            () -> repository.getOrderItemsByOrder(orderId.longValue()),
                            new TypeReference<List<OrderItem>>() {
                            },
                            items -> {
                                List<OrderItemResponse> data = items.stream()
                                        .map(OrderItemResponse::from)
                                        .collect(Collectors.toList());
                                return ApiResponse.success("Order items fetched", data);
                            },
                            tracingContext,
                            "get_by_order",
                            Duration.ofMinutes(10));
                })
                .recover(err -> {
                    logger.error("Failed to fetch items for order {}", orderId, err);
                    tracingMetrics.completeSpanError(tracingContext, "get_by_order", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<List<OrderItemResponse>>error("Failed to fetch items: " + err.getMessage()));
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

    private ApiResponsePagination<List<OrderItemResponse>> mapToPagedResponse(PagedResult<OrderItem> result,
            FindAllOrderRequest req) {
        List<OrderItemResponse> data = result.getData().stream()
                .map(OrderItemResponse::from)
                .collect(Collectors.toList());
        return new ApiResponsePagination<>(
                "success", "Data fetched", data,
                new PaginationMeta(req.getPage(), req.getPageSize(),
                        (int) Math.ceil((double) result.getTotalRecords() / req.getPageSize()),
                        result.getTotalRecords()));
    }

    private ApiResponsePagination<List<OrderItemResponseDeleteAt>> mapToPagedResponseDeleteAt(
            PagedResult<OrderItem> result, FindAllOrderRequest req) {
        List<OrderItemResponseDeleteAt> data = result.getData().stream()
                .map(OrderItemResponseDeleteAt::from)
                .collect(Collectors.toList());
        return new ApiResponsePagination<>(
                "success", "Data fetched", data,
                new PaginationMeta(req.getPage(), req.getPageSize(),
                        (int) Math.ceil((double) result.getTotalRecords() / req.getPageSize()),
                        result.getTotalRecords()));
    }
}