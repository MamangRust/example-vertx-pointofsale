package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.example_crud.domain.requests.order.FindAllOrderRequest;
import com.sanedge.example_crud.domain.requests.order.MonthOrderMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.MonthTotalRevenue;
import com.sanedge.example_crud.domain.requests.order.MonthTotalRevenueMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.YearOrderMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.YearTotalRevenueMerchantRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.model.order.Order;
import com.sanedge.example_crud.model.order.OrderMonth;
import com.sanedge.example_crud.model.order.OrderMonthTotalRevenue;
import com.sanedge.example_crud.model.order.OrderYear;
import com.sanedge.example_crud.model.order.OrderYearTotalRevenue;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.CashierRepository;
import com.sanedge.example_crud.repository.MerchantRepository;
import com.sanedge.example_crud.repository.OrderRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final MerchantRepository merchantRepository;
    private final CashierRepository cashierRepository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public Future<ApiResponse<PagedResult<Order>>> getOrders(RoutingContext ctx, FindAllOrderRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getOrders");
        String cacheKey = String.format("orders:list:%s:%d:%d", req.getSearch(), req.getPage(), req.getPageSize());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getOrders(req.getSearch(), req.getPage(), req.getPageSize()),
                        new TypeReference<PagedResult<Order>>() {}, tracingCtx, "get_orders"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<PagedResult<Order>>> getOrdersActive(RoutingContext ctx, FindAllOrderRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getOrdersActive");
        String cacheKey = String.format("orders:active:%s:%d:%d", req.getSearch(), req.getPage(), req.getPageSize());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getOrdersActive(req.getSearch(), req.getPage(), req.getPageSize()),
                        new TypeReference<PagedResult<Order>>() {}, tracingCtx, "get_orders_active"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<Order>> getOrderById(RoutingContext ctx, Long orderId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getOrderById");
        String cacheKey = "order:detail:" + orderId;

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getOrderById(orderId).map(res -> {
                            if (res == null) throw new CustomException("Order not found");
                            return res;
                        }),
                        new TypeReference<Order>() {}, tracingCtx, "get_order_by_id"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<PagedResult<Order>>> getOrdersByMerchant(RoutingContext ctx, Long merchantId, String search, int page, int pageSize) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getOrdersByMerchant");
        String cacheKey = String.format("orders:merchant:%d:%s:%d:%d", merchantId, search, page, pageSize);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getOrdersByMerchant(search, page, pageSize, merchantId),
                        new TypeReference<PagedResult<Order>>() {}, tracingCtx, "get_orders_merchant"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<Order>> createOrder(Long merchantId, Long cashierId,
            Long totalPrice) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan(
                "OrderService.createOrder",
                Attributes.builder()
                        .put("merchant.id", merchantId)
                        .put("cashier.id", cashierId)
                        .build());
        Span span = Span.fromContext(tracingCtx.getContext());

        log.info("Creating order for merchant: {} cashier: {}", merchantId, cashierId);

        return merchantRepository.getMerchantById(merchantId)
                .compose(merchant -> {
                    if (merchant == null)
                        return Future.failedFuture(new CustomException("Merchant not found"));
                    return cashierRepository.getCashierById(cashierId);
                })
                .compose(cashier -> {
                    if (cashier == null)
                        return Future.failedFuture(new CustomException("Cashier not found"));
                    return orderRepository.createOrder(merchantId, cashierId, totalPrice);
                })
                .map(order -> {
                    span.setAttribute("order.id", order.getOrderId());
                    tracingMetrics.completeSpanSuccess(tracingCtx, "create_order", "Order created");
                    return ApiResponse.success("Order created successfully", order);
                })
                .recover(err -> handleError(tracingCtx, "create_order", err));
    }

    public Future<ApiResponse<Order>> updateOrder(Long orderId, Long totalPrice) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan(
                "OrderService.updateOrder",
                Attributes.builder().put("order.id", orderId).build());

        return orderRepository.getOrderById(orderId)
                .compose(order -> {
                    if (order == null)
                        return Future.failedFuture(new CustomException("Order not found"));
                    return orderRepository.updateOrder(orderId, totalPrice);
                })
                .map(updated -> {
                    invalidateCache(orderId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "update_order", "Order updated");
                    return ApiResponse.success("Order updated successfully", updated);
                })
                .recover(err -> handleError(tracingCtx, "update_order", err));
    }

    public Future<ApiResponse<Order>> trashedOrder(Long orderId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan(
                "OrderService.trashedOrder",
                Attributes.builder().put("order.id", orderId).build());

        return orderRepository.trashedOrder(orderId)
                .map(order -> {
                    if (order == null)
                        throw new CustomException("Order not found or already trashed");
                    invalidateCache(orderId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "trash_order", "Order trashed");
                    return ApiResponse.success("Order moved to trash", order);
                })
                .recover(err -> handleError(tracingCtx, "trash_order", err));
    }

    public Future<ApiResponse<Order>> restoreOrder(Long orderId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan(
                "OrderService.restoreOrder",
                Attributes.builder().put("order.id", orderId).build());

        return orderRepository.restoreOrder(orderId)
                .map(order -> {
                    if (order == null)
                        throw new CustomException("Order not found or not in trash");
                    invalidateCache(orderId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "restore_order", "Order restored");
                    return ApiResponse.success("Order restored successfully", order);
                })
                .recover(err -> handleError(tracingCtx, "restore_order", err));
    }

    public Future<ApiResponse<Void>> deleteOrderPermanently(Long orderId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan(
                "OrderService.deleteOrderPermanently",
                Attributes.builder().put("order.id", orderId).build());

        log.info("Permanently deleting order: {}", orderId);

        return orderRepository.deleteOrderPermanently(orderId)
                .map(v -> {
                    invalidateCache(orderId);
                    log.info("Order deleted successfully: {}", orderId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "delete_permanent", "Order deleted permanently");
                    return ApiResponse.<Void>success("Order deleted permanently", null);
                })
                .recover(throwable -> {
                    log.error("Failed to deletePermanent order: {}", orderId, throwable);
                    tracingMetrics.completeSpanError(tracingCtx, "delete_permanent", throwable.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<Void>error("Failed to delete order: " + throwable.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> restoreAllOrders(RoutingContext ctx) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.restoreAllOrders");
        return orderRepository.restoreAllOrders()
                .map(count -> {
                    tracingMetrics.completeSpanSuccess(tracingCtx, "restore_all", "Success");
                    return ApiResponse.success("All orders restored", count);
                })
                .recover(err -> handleError(tracingCtx, "restore_all", err));
    }

    public Future<ApiResponse<Integer>> deleteAllPermanentOrders(RoutingContext ctx) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.deleteAllPermanentOrders");
        return orderRepository.deleteAllPermanentOrders()
                .map(count -> {
                    tracingMetrics.completeSpanSuccess(tracingCtx, "delete_all", "Success");
                    return ApiResponse.success("All orders deleted permanently", count);
                })
                .recover(err -> handleError(tracingCtx, "delete_all", err));
    }

    public Future<ApiResponse<List<OrderMonthTotalRevenue>>> getMonthlyTotalRevenue(MonthTotalRevenue req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getMonthlyTotalRevenue");
        String cacheKey = String.format("order:report:monthly_revenue:%d:%d", req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getMonthlyTotalRevenue(req),
                        new TypeReference<List<OrderMonthTotalRevenue>>() {
                        }, tracingCtx, "report_monthly_revenue"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<OrderYearTotalRevenue>>> getYearlyTotalRevenue(Integer year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getYearlyTotalRevenue");
        String cacheKey = String.format("order:report:yearly_revenue:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getYearlyTotalRevenue(year),
                        new TypeReference<List<OrderYearTotalRevenue>>() {
                        }, tracingCtx, "report_yearly_revenue"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<OrderMonth>>> getMonthlyOrder(Integer year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getMonthlyOrder");
        String cacheKey = String.format("order:report:monthly_order:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getMonthlyOrder(year),
                        new TypeReference<List<OrderMonth>>() {
                        }, tracingCtx, "report_monthly_order"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<OrderYear>>> getYearlyOrder(Integer year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getYearlyOrder");
        String cacheKey = String.format("order:report:yearly_order:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getYearlyOrder(year),
                        new TypeReference<List<OrderYear>>() {
                        }, tracingCtx, "report_yearly_order"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<OrderMonthTotalRevenue>>> getMonthlyTotalRevenueByMerchant(
            MonthTotalRevenueMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("OrderService.getMonthlyTotalRevenueByMerchant");
        String cacheKey = String.format("order:report:monthly_revenue_merchant:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getMonthlyTotalRevenueByMerchant(req),
                        new TypeReference<List<OrderMonthTotalRevenue>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<OrderYearTotalRevenue>>> getYearlyTotalRevenueByMerchant(
            YearTotalRevenueMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("OrderService.getYearlyTotalRevenueByMerchant");
        String cacheKey = String.format("order:report:yearly_revenue_merchant:%d:%d", req.getMerchantId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getYearlyTotalRevenueByMerchant(req),
                        new TypeReference<List<OrderYearTotalRevenue>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<OrderMonth>>> getMonthlyOrderByMerchant(MonthOrderMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getMonthlyOrderByMerchant");
        String cacheKey = String.format("order:report:monthly_order_merchant:%d:%d", req.getMerchantId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getMonthlyOrderByMerchant(req),
                        new TypeReference<List<OrderMonth>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<OrderYear>>> getYearlyOrderByMerchant(YearOrderMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("OrderService.getYearlyOrderByMerchant");
        String cacheKey = String.format("order:report:yearly_order_merchant:%d:%d", req.getMerchantId(), req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> orderRepository.getYearlyOrderByMerchant(req),
                        new TypeReference<List<OrderYear>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    private <T> Future<ApiResponse<T>> handleCacheOrRepo(String cached, String cacheKey,
            java.util.concurrent.Callable<Future<T>> repoCall, TypeReference<T> typeRef,
            TracingMetrics.TracingContext tracingCtx, String operation) {
        if (cached != null) {
            try {
                T data = objectMapper.readValue(cached, typeRef);
                return Future.succeededFuture(ApiResponse.success("Success", data));
            } catch (Exception e) {
                log.warn("Cache parse error", e);
            }
        }
        try {
            return repoCall.call().map(res -> {
                redisService.set(cacheKey, Json.encode(res), Duration.ofMinutes(30));
                tracingMetrics.completeSpanSuccess(tracingCtx, operation, "Success");
                return ApiResponse.success("Success", res);
            });
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    private <T> Future<ApiResponse<T>> handleReportError(TracingMetrics.TracingContext ctx, Throwable err) {
        log.error("Error in cashier report: {}", err.getMessage(), err);
        tracingMetrics.completeSpanError(ctx, "report", err.getMessage());
        return Future.succeededFuture(ApiResponse.<T>error("Failed to generate report: " + err.getMessage()));
    }

    private void invalidateCache(Long orderId) {
        if (orderId != null) {
            redisService.delete("order:detail:" + orderId);
        }
        redisService.delete("orders:list:");
    }

    private <T> Future<T> handleError(TracingMetrics.TracingContext ctx, String operation, Throwable err) {
        log.error("Error in {}: {}", operation, err.getMessage(), err);
        tracingMetrics.completeSpanError(ctx, operation, err.getMessage());
        return Future.failedFuture(err);
    }
}