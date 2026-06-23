package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.order.FindAllOrderRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.domain.response.orderitem.OrderItemResponse;
import com.sanedge.example_crud.domain.response.orderitem.OrderItemResponseDeleteAt;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.model.order.OrderItem;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.OrderItemRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderItemService {

        private static final Logger logger = LoggerFactory.getLogger(OrderItemService.class);

        private final OrderItemRepository repository;
        private final RedisService redisService;
        private final TracingMetrics tracingMetrics;

        public Future<ApiResponsePagination<List<OrderItemResponse>>> getAll(FindAllOrderRequest req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.getAll");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setPage(page);
                req.setPageSize(pageSize);
                req.setSearch(keyword);

                return fetchPaginatedOrderItems(req, "order_items:all", page, pageSize, keyword,
                                repository::getOrderItems, OrderItemResponse::from, ctx, "get_all",
                                "Order items fetched successfully");
        }

        public Future<ApiResponsePagination<List<OrderItemResponse>>> getActive(FindAllOrderRequest req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.getActive");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setPage(page);
                req.setPageSize(pageSize);
                req.setSearch(keyword);

                return fetchPaginatedOrderItems(req, "order_items:active", page, pageSize, keyword,
                                repository::getOrderItemsActive, OrderItemResponse::from, ctx, "get_active",
                                "Active order items fetched successfully");
        }

        public Future<ApiResponsePagination<List<OrderItemResponseDeleteAt>>> getTrashed(FindAllOrderRequest req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.getTrashed");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setPage(page);
                req.setPageSize(pageSize);
                req.setSearch(keyword);

                return fetchPaginatedOrderItems(req, "order_items:trashed", page, pageSize, keyword,
                                repository::getOrderItemsTrashed, OrderItemResponseDeleteAt::from, ctx, "get_trashed",
                                "Trashed order items fetched successfully");
        }

        public Future<ApiResponse<List<OrderItemResponse>>> getByOrderId(Integer orderId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.getByOrderId",
                                Attributes.builder().put("order.id", orderId).build());
                Span span = Span.fromContext(ctx.getContext());

                String cacheKey = String.format("order_items:order:%d", orderId);

                return redisService.get(cacheKey)
                                .compose(cached -> {
                                        if (cached != null && !cached.isEmpty()) {
                                                span.setAttribute("cache.hit", true);
                                                try {
                                                        JsonArray jsonArray = new JsonArray(cached);
                                                        List<OrderItemResponse> data = jsonArray.stream()
                                                                        .map(obj -> OrderItemResponse.from(OrderItem
                                                                                        .fromJson((JsonObject) obj)))
                                                                        .toList();
                                                        tracingMetrics.completeSpanSuccess(ctx, "get_by_order",
                                                                        "Order items fetched from cache");
                                                        return Future
                                                                        .succeededFuture(ApiResponse.success(
                                                                                        "Order items fetched (from cache)",
                                                                                        data));
                                                } catch (Exception e) {
                                                        logger.warn("Failed to parse cached order items for order {}: {}",
                                                                        orderId, e.getMessage());
                                                        return fetchOrderItemsByOrderFromDb(orderId, ctx);
                                                }
                                        }
                                        span.setAttribute("cache.hit", false);
                                        return fetchOrderItemsByOrderFromDb(orderId, ctx);
                                })
                                .recover(err -> {
                                        logger.error("Failed to fetch items for order {}", orderId, err);
                                        tracingMetrics.completeSpanError(ctx, "get_by_order", err.getMessage());
                                        return Future.succeededFuture(
                                                        ApiResponse.<List<OrderItemResponse>>error(
                                                                        "Failed to fetch items: " + err.getMessage()));
                                });
        }

        private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedOrderItems(T req, String cachePrefix,
                        int page, int pageSize, String keyword,
                        Function<T, Future<PagedResult<OrderItem>>> dbFetcher,
                        Function<OrderItem, R> responseMapper, TracingMetrics.TracingContext tracingContext,
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
                                                        int totalPages = (int) Math
                                                                        .ceil((double) totalRecords / pageSize);

                                                        List<R> data = json.getJsonArray("data").stream()
                                                                        .map(obj -> responseMapper.apply(OrderItem
                                                                                        .fromJson((JsonObject) obj)))
                                                                        .toList();

                                                        tracingMetrics.completeSpanSuccess(tracingContext, spanName,
                                                                        "Order items fetched from cache");
                                                        return Future.succeededFuture(new ApiResponsePagination<>(
                                                                        "success", successMessage, data,
                                                                        new PaginationMeta(page + 1, pageSize,
                                                                                        totalPages, totalRecords)));
                                                } catch (Exception e) {
                                                        logger.warn("Failed to parse cached paginated order items: {}",
                                                                        e.getMessage());
                                                }
                                        }

                                        span.setAttribute("cache.hit", false);
                                        return dbFetcher.apply(req)
                                                        .map(result -> {
                                                                JsonObject jsonToCache = new JsonObject()
                                                                                .put("totalRecords", result
                                                                                                .getTotalRecords())
                                                                                .put("data", new JsonArray(
                                                                                                result.getData().stream()
                                                                                                                .map(OrderItem::toJson)
                                                                                                                .toList()));

                                                                redisService.set(cacheKey, jsonToCache.encode(),
                                                                                Duration.ofMinutes(10))
                                                                                .onFailure(err -> logger.warn(
                                                                                                "Failed to cache {}: {}",
                                                                                                cachePrefix,
                                                                                                err.getMessage()));

                                                                span.setAttribute("records.count",
                                                                                result.getData().size());
                                                                span.setAttribute("records.total",
                                                                                result.getTotalRecords());
                                                                tracingMetrics.completeSpanSuccess(tracingContext,
                                                                                spanName, successMessage);

                                                                return mapPagination(result, page, pageSize,
                                                                                responseMapper, successMessage);
                                                        });
                                })
                                .recover(throwable -> {
                                        logger.error("Failed to fetch paginated order items for {}", cachePrefix,
                                                        throwable);
                                        tracingMetrics.completeSpanError(tracingContext, spanName,
                                                        throwable.getMessage());
                                        return Future.succeededFuture(
                                                        ApiResponsePagination.<List<R>>error("Failed to fetch data: "
                                                                        + throwable.getMessage()));
                                });
        }

        private Future<ApiResponse<List<OrderItemResponse>>> fetchOrderItemsByOrderFromDb(Integer orderId,
                        TracingMetrics.TracingContext tracingContext) {
                Span span = Span.fromContext(tracingContext.getContext());

                return repository.getOrderItemsByOrder(orderId.longValue())
                                .map(items -> {
                                        List<OrderItemResponse> data = items.stream()
                                                        .map(OrderItemResponse::from)
                                                        .collect(Collectors.toList());

                                        JsonArray jsonArray = new JsonArray(
                                                        items.stream().map(OrderItem::toJson).toList());
                                        redisService.set("order_items:order:" + orderId, jsonArray.encode(),
                                                        Duration.ofMinutes(10))
                                                        .onFailure(err -> logger.warn(
                                                                        "Failed to cache order items for order {}: {}",
                                                                        orderId,
                                                                        err.getMessage()));

                                        span.setAttribute("records.count", data.size());
                                        tracingMetrics.completeSpanSuccess(tracingContext, "get_by_order",
                                                        "Order items fetched from DB");
                                        return ApiResponse.success("Order items fetched", data);
                                });
        }

        private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<OrderItem> result, int page, int pageSize,
                        Function<OrderItem, R> mapper, String message) {
                int totalRecords = result.getTotalRecords();
                int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
                List<R> data = result.getData().stream().map(mapper).toList();

                return new ApiResponsePagination<>("success", message, data,
                                new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
        }

        public Future<ApiResponse<OrderItemResponseDeleteAt>> trash(Long id) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.trash",
                                Attributes.builder().put("orderItem.id", id).build());

                return repository.trashOrderItem(id)
                                .map(data -> {
                                        if (data == null) {
                                                throw new CustomException("Order item not found or already trashed");
                                        }
                                        tracingMetrics.completeSpanSuccess(ctx, "trash", "Success");
                                        return ApiResponse.success("Order item trashed",
                                                        OrderItemResponseDeleteAt.from(data));
                                })
                                .recover(err -> {
                                        logger.error("Failed to trash order item: {}", id, err);
                                        tracingMetrics.completeSpanError(ctx, "trash", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<OrderItemResponseDeleteAt>error(
                                                                        "Failed to trash order item: "
                                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<OrderItemResponseDeleteAt>> restore(Long id) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.restore",
                                Attributes.builder().put("orderItem.id", id).build());

                return repository.findByTrashed(id)
                                .compose(data -> {
                                        if (data == null) {
                                                return Future.failedFuture(new CustomException(
                                                                "Order item not found or not trashed"));
                                        }
                                        return repository.restoreOrderItem(id);
                                })
                                .map(data -> {
                                        tracingMetrics.completeSpanSuccess(ctx, "restore", "Success");
                                        return ApiResponse.success("Order item restored",
                                                        OrderItemResponseDeleteAt.from(data));
                                })
                                .recover(err -> {
                                        logger.error("Failed to restore order item: {}", id, err);
                                        tracingMetrics.completeSpanError(ctx, "restore", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<OrderItemResponseDeleteAt>error(
                                                                        "Failed to restore order item: "
                                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Boolean>> deletePermanent(Long id) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.deletePermanent",
                                Attributes.builder().put("orderItem.id", id).build());

                return repository.findByTrashed(id)
                                .compose(data -> {
                                        if (data == null) {
                                                return Future.failedFuture(new CustomException(
                                                                "Order item not found or not trashed"));
                                        }
                                        return repository.deleteOrderItemPermanently(id);
                                })
                                .map(v -> {
                                        tracingMetrics.completeSpanSuccess(ctx, "delete_permanent", "Success");
                                        return ApiResponse.success("Order item permanently deleted", true);
                                })
                                .recover(err -> {
                                        logger.error("Failed to permanently delete order item: {}", id, err);
                                        tracingMetrics.completeSpanError(ctx, "delete_permanent", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Boolean>error("Failed to delete order item: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Integer>> restoreAll() {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.restoreAll");
                return repository.restoreAllOrdersItem()
                                .compose(count -> {
                                        if (count == 0) {
                                                return Future.failedFuture(new CustomException("No trashed order items found"));
                                        }
                                        tracingMetrics.completeSpanSuccess(ctx, "restore_all", "Success");
                                        return Future.succeededFuture(ApiResponse.success("All order items restored", count));
                                })
                                .recover(err -> {
                                         logger.error("Failed to restore all order items", err);
                                         tracingMetrics.completeSpanError(ctx, "restore_all", err.getMessage());
                                         if (err instanceof CustomException) {
                                                 return Future.failedFuture(err);
                                         }
                                         return Future.succeededFuture(
                                                         ApiResponse.<Integer>error(
                                                                         "Failed to restore all: " + err.getMessage()));
                                 });
        }

        public Future<ApiResponse<Integer>> deleteAllPermanent() {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderItemService.deleteAllPermanent");
                return repository.deleteAllPermanentOrdersItem()
                                .compose(count -> {
                                        if (count == 0) {
                                                return Future.failedFuture(new CustomException("No trashed order items found"));
                                        }
                                        tracingMetrics.completeSpanSuccess(ctx, "delete_all_permanent", "Success");
                                        return Future.succeededFuture(ApiResponse.success("All order items deleted permanently", count));
                                })
                                .recover(err -> {
                                         logger.error("Failed to delete all order items permanently", err);
                                         tracingMetrics.completeSpanError(ctx, "delete_all_permanent", err.getMessage());
                                         if (err instanceof CustomException) {
                                                 return Future.failedFuture(err);
                                         }
                                         return Future.succeededFuture(
                                                         ApiResponse.<Integer>error(
                                                                         "Failed to delete all: " + err.getMessage()));
                                 });
        }
}