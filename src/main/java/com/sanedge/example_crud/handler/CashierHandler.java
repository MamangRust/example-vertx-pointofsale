package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.cashier.*;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.service.CashierService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CashierHandler {
    private final CashierService service;


    public void findAll(RoutingContext ctx) {
        FindAllCashiers req = mapFindAllCashiers(ctx);
        service.getCashiers(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllCashiers req = mapFindAllCashiers(ctx);
        service.getCashiersActive(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findTrashed(RoutingContext ctx) {
        FindAllCashiers req = mapFindAllCashiers(ctx);
        service.getCashiersTrashed(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findById(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.getCashierById(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        FindAllCashierMerchant req = new FindAllCashierMerchant();
        req.setMerchantId(merchantId.intValue());
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));
        req.setSearch(ctx.queryParams().get("search"));

        service.getCashiersByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void create(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        CreateCashierRequest req = body.mapTo(CreateCashierRequest.class);
        service.createCashier(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void update(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        JsonObject body = ctx.body().asJsonObject();
        UpdateCashierRequest req = body.mapTo(UpdateCashierRequest.class);
        req.setCashierId(id.intValue()); 

        service.updateCashier(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void trash(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.trashCashier(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void restore(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.restoreCashier(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deletePermanent(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.deleteCashierPermanently(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }
    
    public void restoreAll(RoutingContext ctx) {
        service.restoreAllCashiers()
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deleteAllPermanent(RoutingContext ctx) {
        service.deleteAllCashiersPermanent()
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }


    public void getMonthlyTotalSales(RoutingContext ctx) {
        MonthTotalSales req = new MonthTotalSales();
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyTotalSalesCashier(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyTotalSales(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyTotalSalesCashier(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyTotalSalesById(RoutingContext ctx) {
        Long cashierId = Long.parseLong(ctx.pathParam("id"));
        MonthTotalSalesCashier req = new MonthTotalSalesCashier();
        req.setCashierId(cashierId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyTotalSalesById(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyTotalSalesById(RoutingContext ctx) {
        Long cashierId = Long.parseLong(ctx.pathParam("id"));
        YearTotalSalesCashier req = new YearTotalSalesCashier();
        req.setCashierId(cashierId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getYearlyTotalSalesById(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyTotalSalesByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        MonthTotalSalesMerchant req = new MonthTotalSalesMerchant();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyTotalSalesByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyTotalSalesByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        YearTotalSalesMerchant req = new YearTotalSalesMerchant();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getYearlyTotalSalesByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyCashier(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getMonthlyCashier(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyCashier(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyCashier(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyCashierById(RoutingContext ctx) {
        Long cashierId = Long.parseLong(ctx.pathParam("id"));
        MonthCashierIdRequest req = new MonthCashierIdRequest();
        req.setCashierId(cashierId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getMonthlyCashierByCashierId(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyCashierById(RoutingContext ctx) {
        Long cashierId = Long.parseLong(ctx.pathParam("id"));
        YearCashierIdRequest req = new YearCashierIdRequest();
        req.setCashierId(cashierId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getYearlyCashierByCashierId(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyCashierByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        MonthCashierMerchantRequest req = new MonthCashierMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getMonthlyCashierByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyCashierByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        YearCashierMerchantRequest req = new YearCashierMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getYearlyCashierByMerchant(req)
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

    private FindAllCashiers mapFindAllCashiers(RoutingContext ctx) {
        FindAllCashiers req = new FindAllCashiers();
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