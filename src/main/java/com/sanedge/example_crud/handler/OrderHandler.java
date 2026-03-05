package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.order.*;
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
        service.getOrders(ctx, req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getOrdersActive(ctx, req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findById(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.getOrderById(ctx, id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        String search = ctx.queryParams().get("search");
        int page = getQueryParamInt(ctx, "page", 1);
        int pageSize = getQueryParamInt(ctx, "pageSize", 10);

        service.getOrdersByMerchant(ctx, merchantId, search, page, pageSize)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void create(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        
        Long merchantId = body.getLong("merchantId");
        Long cashierId = body.getLong("cashierId");
        Long totalPrice = body.getLong("totalPrice");

        service.createOrder(merchantId, cashierId, totalPrice)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void update(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        JsonObject body = ctx.body().asJsonObject();
        
        Long totalPrice = body.getLong("totalPrice");

        service.updateOrder(id, totalPrice)
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
        service.restoreAllOrders(ctx)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deleteAllPermanent(RoutingContext ctx) {
        service.deleteAllPermanentOrders(ctx)
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
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}