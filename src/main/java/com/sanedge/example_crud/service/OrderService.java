package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.order.CreateOrderRecordRequest;
import com.sanedge.example_crud.domain.requests.order.CreateOrderRequest;
import com.sanedge.example_crud.domain.requests.order.FindAllOrderRequest;
import com.sanedge.example_crud.domain.requests.order.MonthOrderMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.MonthTotalRevenue;
import com.sanedge.example_crud.domain.requests.order.MonthTotalRevenueMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.UpdateOrderRecordRequest;
import com.sanedge.example_crud.domain.requests.order.UpdateOrderRequest;
import com.sanedge.example_crud.domain.requests.order.YearOrderMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.YearTotalRevenueMerchantRequest;
import com.sanedge.example_crud.domain.requests.order_item.CreateOrderItemRecordRequest;
import com.sanedge.example_crud.domain.requests.order_item.CreateOrderItemRequest;
import com.sanedge.example_crud.domain.requests.order_item.UpdateOrderItemRecordRequest;
import com.sanedge.example_crud.domain.requests.order_item.UpdateOrderItemRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.domain.response.order.OrderResponse;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.model.order.Order;
import com.sanedge.example_crud.model.order.OrderMonth;
import com.sanedge.example_crud.model.order.OrderMonthTotalRevenue;
import com.sanedge.example_crud.model.order.OrderYear;
import com.sanedge.example_crud.model.order.OrderYearTotalRevenue;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.CashierRepository;
import com.sanedge.example_crud.repository.MerchantRepository;
import com.sanedge.example_crud.repository.OrderItemRepository;
import com.sanedge.example_crud.repository.OrderRepository;
import com.sanedge.example_crud.repository.ProductRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MerchantRepository merchantRepository;
    private final OrderItemRepository orderItemRepository;
    private final CashierRepository cashierRepository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;

    public Future<ApiResponsePagination<List<Order>>> getOrders(FindAllOrderRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderService.getOrders");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setSearch(keyword);
        req.setPage(page);
        req.setPageSize(pageSize);

        return fetchPaginatedOrders(
                req, "orders:all", page, pageSize, keyword,
                r -> orderRepository.getOrders(r.getSearch(), r.getPage(), r.getPageSize()),
                Function.identity(), ctx, "get_orders", "Orders fetched successfully");
    }

    public Future<ApiResponsePagination<List<Order>>> getOrdersActive(FindAllOrderRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderService.getOrdersActive");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setSearch(keyword);
        req.setPage(page);
        req.setPageSize(pageSize);

        return fetchPaginatedOrders(
                req, "orders:active", page, pageSize, keyword,
                r -> orderRepository.getOrdersActive(r.getSearch(), r.getPage(), r.getPageSize()),
                Function.identity(), ctx, "get_orders_active", "Active orders fetched successfully");
    }

    public Future<ApiResponsePagination<List<Order>>> getOrdersByMerchant(Long merchantId, String search, int page,
            int pageSize) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderService.getOrdersByMerchant",
                Attributes.builder().put("merchant.id", merchantId).build());

        String cachePrefix = String.format("orders:merchant:%d", merchantId);

        return fetchPaginatedOrders(
                null, cachePrefix, page, pageSize, search,
                r -> orderRepository.getOrdersByMerchant(search, page, pageSize, merchantId),
                Function.identity(), ctx, "get_orders_merchant", "Orders by merchant fetched successfully");
    }

    public Future<ApiResponse<Order>> getOrderById(Long orderId) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("OrderService.getOrderById",
                Attributes.builder().put("order.id", orderId).build());
        Span span = Span.fromContext(ctx.getContext());

        String cacheKey = "order:detail:" + orderId;

        return redisService.get(cacheKey)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        span.setAttribute("order.cache_hit", true);
                        try {
                            Order order = Order.fromJson(new JsonObject(cached));
                            tracingMetrics.completeSpanSuccess(ctx, "get_by_id", "Order fetched from cache");
                            return Future.succeededFuture(
                                    ApiResponse.success("Order fetched successfully (from cache)", order));
                        } catch (Exception e) {
                            logger.warn("Failed to parse cached order data for order {}: {}", orderId, e.getMessage());
                            return fetchOrderFromDatabase(orderId, ctx);
                        }
                    }
                    span.setAttribute("order.cache_hit", false);
                    return fetchOrderFromDatabase(orderId, ctx);
                })
                .recover(err -> {
                    logger.error("Failed to fetch order by id: {}", orderId, err);
                    tracingMetrics.completeSpanError(ctx, "get_by_id", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<Order>error("Failed to fetch order: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<OrderResponse>> createOrder(CreateOrderRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan(
                "OrderService.createOrder",
                Attributes.builder()
                        .put("merchant.id", req.getMerchantId())
                        .put("cashier.id", req.getCashierId())
                        .build());
        Span span = Span.fromContext(tracingCtx.getContext());

        logger.info("Creating order for merchant: {} cashier: {}", req.getMerchantId(), req.getCashierId());

        return merchantRepository.getMerchantById(req.getMerchantId().longValue())
                .compose(merchant -> {
                    if (merchant == null)
                        return Future.failedFuture(new CustomException("Merchant not found"));
                    return cashierRepository.getCashierById(req.getCashierId().longValue());
                })
                .compose(cashier -> {
                    if (cashier == null) {
                        return Future.failedFuture(new CustomException("Cashier not found"));
                    }
                    return orderRepository.createOrder(
                            new CreateOrderRecordRequest(req.getMerchantId().longValue(),
                                    req.getCashierId().longValue(), 0));
                })
                .compose(order -> processOrderItems(order, req.getItems()).map(v -> order))
                .compose(order -> orderItemRepository.calculateTotalPrice(order.getOrderId().longValue())
                        .compose(totalPrice -> orderRepository.updateOrder(
                                new UpdateOrderRecordRequest(order.getOrderId().longValue(), totalPrice))))
                .map(updatedOrder -> {
                    span.setAttribute("order.id", updatedOrder.getOrderId());
                    tracingMetrics.completeSpanSuccess(tracingCtx, "create", "Order created successfully");
                    return ApiResponse.success("Order created successfully", OrderResponse.from(updatedOrder));
                })
                .recover(err -> {
                    logger.error("Failed to create order", err);
                    tracingMetrics.completeSpanError(tracingCtx, "create_order", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<OrderResponse>error("Failed to create order: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<OrderResponse>> updateOrder(UpdateOrderRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan(
                "OrderService.updateOrder",
                Attributes.builder().put("order.id", req.getOrderId()).build());
        Span span = Span.fromContext(tracingCtx.getContext());

        logger.info("Updating order: {}", req.getOrderId());

        return orderRepository.getOrderById(req.getOrderId().longValue())
                .compose(order -> {
                    if (order == null) {
                        return Future.failedFuture(new CustomException("Order not found"));
                    }
                    return cashierRepository.getCashierById(req.getCashierId().longValue());
                })
                .compose(cashier -> {
                    if (cashier == null) {
                        return Future.failedFuture(new CustomException("Cashier not found"));
                    }
                    return processUpdateOrderItems(req.getOrderId().longValue(), req.getItems());
                })
                .compose(v -> orderItemRepository.calculateTotalPrice(req.getOrderId().longValue())
                        .compose(totalPrice -> orderRepository.updateOrder(
                                new UpdateOrderRecordRequest(req.getOrderId().longValue(), totalPrice))))
                .map(updatedOrder -> {
                    span.setAttribute("order.id", updatedOrder.getOrderId());
                    invalidateCache(updatedOrder.getOrderId());
                    tracingMetrics.completeSpanSuccess(tracingCtx, "update", "Order updated successfully");
                    return ApiResponse.success("Order updated successfully", OrderResponse.from(updatedOrder));
                })
                .recover(err -> {
                    logger.error("Failed to update order: {}", req.getOrderId(), err);
                    tracingMetrics.completeSpanError(tracingCtx, "update_order", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<OrderResponse>error("Failed to update order: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Order>> trashedOrder(Long orderId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.trashedOrder",
                Attributes.builder().put("order.id", orderId).build());

        return orderRepository.trashedOrder(orderId)
                .compose(order -> {
                    if (order == null)
                        return Future.failedFuture(new CustomException("Order not found or already trashed"));
                    invalidateCache(orderId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "trash_order", "Order trashed");
                    return Future.succeededFuture(ApiResponse.success("Order moved to trash", order));
                })
                .recover(err -> {
                    logger.error("Failed to trash order: {}", orderId, err);
                    tracingMetrics.completeSpanError(tracingCtx, "trash_order", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<Order>error("Failed to trash order: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Order>> restoreOrder(Long orderId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.restoreOrder",
                Attributes.builder().put("order.id", orderId).build());

        return orderRepository.findByTrashed(orderId)
                .compose(order -> {
                    if (order == null) {
                        return Future.failedFuture(new CustomException("Order not found or not in trash"));
                    }
                    return orderRepository.restoreOrder(orderId);
                })
                .compose(order -> {
                    invalidateCache(orderId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "restore_order", "Order restored");
                    return Future.succeededFuture(ApiResponse.success("Order restored successfully", order));
                })
                .recover(err -> {
                    logger.error("Failed to restore order: {}", orderId, err);
                    tracingMetrics.completeSpanError(tracingCtx, "restore_order", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<Order>error("Failed to restore order: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Void>> deleteOrderPermanently(Long orderId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.deleteOrderPermanently",
                Attributes.builder().put("order.id", orderId).build());

        logger.info("Permanently deleting order: {}", orderId);

        return orderRepository.findByTrashed(orderId)
                .compose(order -> {
                    if (order == null) {
                        return Future.failedFuture(new CustomException("Order not found or not in trash"));
                    }
                    return orderRepository.deleteOrderPermanently(orderId);
                })
                .map(v -> {
                    invalidateCache(orderId);
                    logger.info("Order deleted successfully: {}", orderId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "delete_permanent", "Order deleted permanently");
                    return ApiResponse.<Void>success("Order deleted permanently", null);
                })
                .recover(err -> {
                    logger.error("Failed to deletePermanent order: {}", orderId, err);
                    tracingMetrics.completeSpanError(tracingCtx, "delete_permanent", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<Void>error("Failed to delete order: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> restoreAllOrders() {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.restoreAllOrders");
        return orderRepository.restoreAllOrders()
                .compose(count -> {
                    if (count == 0) {
                        return Future.failedFuture(new CustomException("No trashed orders found"));
                    }
                    tracingMetrics.completeSpanSuccess(tracingCtx, "restore_all", "Success");
                    return Future.succeededFuture(ApiResponse.success("All orders restored", count));
                })
                .recover(err -> {
                    logger.error("Failed to restore all orders", err);
                    tracingMetrics.completeSpanError(tracingCtx, "restore_all", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<Integer>error("Failed to restore all orders: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> deleteAllPermanentOrders() {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.deleteAllPermanentOrders");
        return orderRepository.deleteAllPermanentOrders()
                .compose(count -> {
                    if (count == 0) {
                        return Future.failedFuture(new CustomException("No trashed orders found"));
                    }
                    tracingMetrics.completeSpanSuccess(tracingCtx, "delete_all", "Success");
                    return Future.succeededFuture(ApiResponse.success("All orders deleted permanently", count));
                })
                .recover(err -> {
                    logger.error("Failed to permanently delete all orders", err);
                    tracingMetrics.completeSpanError(tracingCtx, "delete_all", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<Integer>error("Failed to delete all orders: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<List<OrderMonthTotalRevenue>>> getMonthlyTotalRevenue(MonthTotalRevenue req) {
        String cacheKey = String.format("order:report:monthly_revenue:%d:%d", req.getYear(), req.getMonth());
        return fetchStats(cacheKey, orderRepository.getMonthlyTotalRevenue(req), Function.identity(),
                OrderMonthTotalRevenue.class, "report_monthly_revenue", "Monthly total revenue fetched successfully");
    }

    public Future<ApiResponse<List<OrderYearTotalRevenue>>> getYearlyTotalRevenue(Integer year) {
        String cacheKey = String.format("order:report:yearly_revenue:%d", year);
        return fetchStats(cacheKey, orderRepository.getYearlyTotalRevenue(year), Function.identity(),
                OrderYearTotalRevenue.class, "report_yearly_revenue", "Yearly total revenue fetched successfully");
    }

    public Future<ApiResponse<List<OrderMonth>>> getMonthlyOrder(Integer year) {
        String cacheKey = String.format("order:report:monthly_order:%d", year);
        return fetchStats(cacheKey, orderRepository.getMonthlyOrder(year), Function.identity(), OrderMonth.class,
                "report_monthly_order", "Monthly orders fetched successfully");
    }

    public Future<ApiResponse<List<OrderYear>>> getYearlyOrder(Integer year) {
        String cacheKey = String.format("order:report:yearly_order:%d", year);
        return fetchStats(cacheKey, orderRepository.getYearlyOrder(year), Function.identity(), OrderYear.class,
                "report_yearly_order", "Yearly orders fetched successfully");
    }

    public Future<ApiResponse<List<OrderMonthTotalRevenue>>> getMonthlyTotalRevenueByMerchant(
            MonthTotalRevenueMerchantRequest req) {
        String cacheKey = String.format("order:report:monthly_revenue_merchant:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());
        return fetchStats(cacheKey, orderRepository.getMonthlyTotalRevenueByMerchant(req), Function.identity(),
                OrderMonthTotalRevenue.class, "report_monthly_revenue_merchant",
                "Monthly total revenue by merchant fetched successfully");
    }

    public Future<ApiResponse<List<OrderYearTotalRevenue>>> getYearlyTotalRevenueByMerchant(
            YearTotalRevenueMerchantRequest req) {
        String cacheKey = String.format("order:report:yearly_revenue_merchant:%d:%d", req.getMerchantId(),
                req.getYear());
        return fetchStats(cacheKey, orderRepository.getYearlyTotalRevenueByMerchant(req), Function.identity(),
                OrderYearTotalRevenue.class, "report_yearly_revenue_merchant",
                "Yearly total revenue by merchant fetched successfully");
    }

    public Future<ApiResponse<List<OrderMonth>>> getMonthlyOrderByMerchant(MonthOrderMerchantRequest req) {
        String cacheKey = String.format("order:report:monthly_order_merchant:%d:%d", req.getMerchantId(),
                req.getYear());
        return fetchStats(cacheKey, orderRepository.getMonthlyOrderByMerchant(req), Function.identity(),
                OrderMonth.class, "report_monthly_order_merchant", "Monthly orders by merchant fetched successfully");
    }

    public Future<ApiResponse<List<OrderYear>>> getYearlyOrderByMerchant(YearOrderMerchantRequest req) {
        String cacheKey = String.format("order:report:yearly_order_merchant:%d:%d", req.getMerchantId(),
                req.getYear());
        return fetchStats(cacheKey, orderRepository.getYearlyOrderByMerchant(req), Function.identity(),
                OrderYear.class, "report_yearly_order_merchant", "Yearly orders by merchant fetched successfully");
    }

    private <T, R> Future<ApiResponse<List<R>>> fetchStats(String cacheKey, Future<List<T>> dbFuture,
            Function<T, R> mapper, Class<R> responseType, String spanName, String successMessage) {

        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("OrderStatsService." + spanName);

        return redisService.getJsonList(cacheKey, responseType)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        tracingMetrics.completeSpanSuccess(tracingContext, spanName, "Data from cache");
                        return Future.succeededFuture(cached);
                    }
                    return dbFuture.map(dbResults -> {
                        List<R> responseList = dbResults.stream().map(mapper).collect(Collectors.toList());
                        redisService.setJsonList(cacheKey, responseList, Duration.ofHours(6))
                                .onFailure(err -> tracingMetrics.completeSpanError(tracingContext, spanName,
                                        "Data fetched but cache failed"))
                                .onSuccess(v -> tracingMetrics.completeSpanSuccess(tracingContext, spanName,
                                        "Data fetched from DB and cached"));
                        return responseList;
                    });
                })
                .map(results -> ApiResponse.success(successMessage, results))
                .recover(
                        err -> Future.succeededFuture(ApiResponse.error("Failed to fetch stats: " + err.getMessage())));
    }

    private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedOrders(T req, String cachePrefix, int page,
            int pageSize, String keyword, Function<T, Future<PagedResult<Order>>> dbFetcher,
            Function<Order, R> responseMapper, TracingMetrics.TracingContext tracingContext, String spanName,
            String successMessage) {

        Span span = Span.fromContext(tracingContext.getContext());
        String cacheKey = String.format("%s:page:%d:size:%d:search:%s", cachePrefix, page, pageSize, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        span.setAttribute("orders.cache_hit", true);
                        try {
                            JsonObject json = new JsonObject(cached);
                            int totalRecords = json.getInteger("totalRecords");
                            int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

                            List<R> data = json.getJsonArray("data").stream()
                                    .map(obj -> responseMapper.apply(Order.fromJson((JsonObject) obj)))
                                    .toList();

                            tracingMetrics.completeSpanSuccess(tracingContext, spanName, "Orders fetched from cache");
                            return Future.succeededFuture(new ApiResponsePagination<>("success", successMessage, data,
                                    new PaginationMeta(page + 1, pageSize, totalPages, totalRecords)));
                        } catch (Exception e) {
                            logger.warn("Failed to parse cached paginated orders: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("orders.cache_hit", false);
                    return dbFetcher.apply(req)
                            .map(result -> {
                                JsonObject jsonToCache = new JsonObject()
                                        .put("totalRecords", result.getTotalRecords())
                                        .put("data", new JsonArray(
                                                result.getData().stream().map(Order::toJson).toList()));

                                redisService.set(cacheKey, jsonToCache.encode(), Duration.ofMinutes(5))
                                        .onFailure(err -> logger.warn("Failed to cache {}: {}", cachePrefix,
                                                err.getMessage()));

                                span.setAttribute("orders.count", result.getData().size());
                                span.setAttribute("orders.total_records", result.getTotalRecords());
                                tracingMetrics.completeSpanSuccess(tracingContext, spanName, successMessage);

                                return mapPagination(result, page, pageSize, responseMapper, successMessage);
                            });
                })
                .recover(throwable -> {
                    logger.error("Failed to fetch paginated orders for {}", cachePrefix, throwable);
                    tracingMetrics.completeSpanError(tracingContext, spanName, throwable.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination.<List<R>>error("Failed to fetch orders: " + throwable.getMessage()));
                });
    }

    private Future<ApiResponse<Order>> fetchOrderFromDatabase(Long orderId,
            TracingMetrics.TracingContext tracingContext) {
        Span span = Span.fromContext(tracingContext.getContext());

        return orderRepository.getOrderById(orderId)
                .compose(order -> {
                    if (order == null)
                        return Future.failedFuture(new CustomException("Order not found"));

                    span.setAttribute("order.id", order.getOrderId());

                    redisService.setJson("order:detail:" + orderId, order.toJson(), Duration.ofMinutes(30))
                            .onFailure(err -> logger.warn("Failed to cache order {}: {}", orderId, err.getMessage()));

                    return Future.succeededFuture(ApiResponse.success("Order fetched successfully", order));
                });
    }

    private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<Order> result, int page, int pageSize,
            Function<Order, R> mapper, String message) {
        int totalRecords = result.getTotalRecords();
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        List<R> data = result.getData().stream().map(mapper).toList();

        return new ApiResponsePagination<>("success", message, data,
                new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
    }

    private void invalidateCache(Long orderId) {
        if (orderId != null) {
            redisService.delete("order:detail:" + orderId)
                    .onSuccess(deleted -> {
                        if (deleted > 0)
                            logger.debug("Cache order:{} invalidated successfully", orderId);
                    })
                    .onFailure(err -> logger.warn("Failed to invalidate cache for order {}: {}", orderId,
                            err.getMessage()));
        }
        redisService.delete("orders:list:")
                .onFailure(err -> logger.warn("Failed to invalidate list cache: {}", err.getMessage()));
    }

    private Future<Void> processOrderItems(Order order, List<CreateOrderItemRequest> items) {
        Future<Void> future = Future.succeededFuture();
        for (CreateOrderItemRequest item : items) {
            future = future.compose(v -> createAndProcessItem(order.getOrderId(), item));
        }
        return future;
    }

    private Future<Void> createAndProcessItem(Long orderId, CreateOrderItemRequest item) {
        return productRepository.getProductById(item.getProductId())
                .compose(product -> {
                    if (product == null) {
                        return Future.failedFuture(new CustomException("Product not found"));
                    }
                    if (product.getCountInStock() < item.getQuantity()) {
                        return Future.failedFuture(new CustomException("Insufficient product stock"));
                    }

                    return orderItemRepository.createOrderItem(
                            new CreateOrderItemRecordRequest(
                                    orderId,
                                    item.getProductId(),
                                    item.getQuantity(),
                                    product.getPrice()))
                            .compose(orderItem -> {
                                int newStock = product.getCountInStock() - item.getQuantity();
                                return productRepository.updateProductCountStock(product.getProductId(), newStock);
                            })
                            .mapEmpty();
                });
    }

    private Future<Void> processUpdateOrderItems(Long orderId, List<UpdateOrderItemRequest> items) {
        Future<Void> future = Future.succeededFuture();
        for (UpdateOrderItemRequest item : items) {
            future = future.compose(v -> updateOrCreateItem(orderId, item));
        }
        return future;
    }

    private Future<Void> updateOrCreateItem(Long orderId, UpdateOrderItemRequest item) {
        return productRepository.getProductById(item.getProductId())
                .compose(product -> {
                    if (product == null) {
                        return Future.failedFuture(new CustomException("Product not found"));
                    }

                    if (item.getOrderItemId() > 0) {
                        return orderItemRepository.updateOrderItem(
                                new UpdateOrderItemRecordRequest(
                                        item.getOrderItemId(),
                                        item.getOrderItemId(),
                                        item.getProductId(),
                                        item.getQuantity(),
                                        product.getPrice()))
                                .mapEmpty();
                    } else {
                        if (product.getCountInStock() < item.getQuantity()) {
                            return Future.failedFuture(new CustomException("Insufficient product stock"));
                        }

                        return orderItemRepository.createOrderItem(
                                new CreateOrderItemRecordRequest(
                                        orderId,
                                        item.getProductId(),
                                        item.getQuantity(),
                                        product.getPrice()))
                                .compose(v -> {
                                    int newStock = product.getCountInStock() - item.getQuantity();
                                    return productRepository.updateProductCountStock(product.getProductId(), newStock);
                                })
                                .mapEmpty();
                    }
                });
    }
}