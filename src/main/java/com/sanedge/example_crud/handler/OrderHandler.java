package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.order.CreateOrderRequest;
import com.sanedge.example_crud.domain.requests.order.FindAllOrderRequest;
import com.sanedge.example_crud.domain.requests.order.MonthOrderMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.MonthTotalRevenue;
import com.sanedge.example_crud.domain.requests.order.MonthTotalRevenueMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.UpdateOrderRequest;
import com.sanedge.example_crud.domain.requests.order.YearOrderMerchantRequest;
import com.sanedge.example_crud.domain.requests.order.YearTotalRevenueMerchantRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.service.OrderService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderHandler {
    private final OrderService service;

    public void findAll(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getOrders(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getOrdersActive(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findById(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.getOrderById(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        String search = ctx.queryParams().get("search");
        int page = getQueryParamInt(ctx, "page", 1);
        int pageSize = getQueryParamInt(ctx, "pageSize", 10);

        service.getOrdersByMerchant(merchantId, search, page, pageSize)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void create(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            sendErrorResponse(ctx, new com.sanedge.example_crud.exception.BadRequestException("Request body cannot be empty"));
            return;
        }

        CreateOrderRequest req = body.mapTo(CreateOrderRequest.class);

        service.createOrder(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void update(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            sendErrorResponse(ctx, new com.sanedge.example_crud.exception.BadRequestException("Request body cannot be empty"));
            return;
        }
        UpdateOrderRequest req = body.mapTo(UpdateOrderRequest.class);
        req.setOrderId(id.intValue());

        service.updateOrder(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void trash(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.trashedOrder(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void restore(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.restoreOrder(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deletePermanent(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.deleteOrderPermanently(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void restoreAll(RoutingContext ctx) {
        service.restoreAllOrders()
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deleteAllPermanent(RoutingContext ctx) {
        service.deleteAllPermanentOrders()
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyTotalRevenue(RoutingContext ctx) {
        MonthTotalRevenue req = new MonthTotalRevenue();
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));

        service.getMonthlyTotalRevenue(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyTotalRevenue(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyTotalRevenue(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyOrder(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getMonthlyOrder(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyOrder(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyOrder(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyTotalRevenueByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        MonthTotalRevenueMerchantRequest req = new MonthTotalRevenueMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));

        service.getMonthlyTotalRevenueByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyTotalRevenueByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        YearTotalRevenueMerchantRequest req = new YearTotalRevenueMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getYearlyTotalRevenueByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyOrderByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        MonthOrderMerchantRequest req = new MonthOrderMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getMonthlyOrderByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyOrderByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        YearOrderMerchantRequest req = new YearOrderMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getYearlyOrderByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    private void sendResponse(RoutingContext ctx, Object res) {
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(res));
    }

    private void sendErrorResponse(RoutingContext ctx, Throwable err) {
        ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(ApiResponse.error(err.getMessage())));
    }

    private FindAllOrderRequest mapFindAll(RoutingContext ctx) {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setSearch(ctx.queryParams().get("search"));
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));
        return req;
    }

    private int getQueryParamInt(RoutingContext ctx, String key, int defaultValue) {
        String val = ctx.queryParams().get(key);
        if (val == null || val.isEmpty())
            return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}