package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.transactions.*;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.service.TransactionService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionHandler {
    private final TransactionService service;

    public void findAll(RoutingContext ctx) {
        FindAllTransactionRequest req = mapFindAll(ctx);
        service.getAll(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllTransactionRequest req = mapFindAll(ctx);
        service.getActive(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findTrashed(RoutingContext ctx) {
        FindAllTransactionRequest req = mapFindAll(ctx);
        service.getTrashed(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findById(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.getById(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findByOrderId(RoutingContext ctx) {
        Long orderId = Long.parseLong(ctx.pathParam("orderId"));
        service.getByOrderId(orderId)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        FindAllTransactionByMerchantRequest req = new FindAllTransactionByMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setSearch(ctx.queryParams().get("search"));
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));

        service.getByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void create(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        CreateTransactionRequest req = body.mapTo(CreateTransactionRequest.class);
        service.createTransaction(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void update(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        JsonObject body = ctx.body().asJsonObject();
        UpdateTransactionRequest req = body.mapTo(UpdateTransactionRequest.class);
        req.setTransactionID(id.intValue());

        service.updateTransaction(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void trash(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.trash(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void restore(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.restore(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deletePermanent(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.deletePermanent(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void restoreAll(RoutingContext ctx) {
        service.restoreAll()
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deleteAllPermanent(RoutingContext ctx) {
        service.deleteAllPermanent()
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyAmountSuccess(RoutingContext ctx) {
        MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyAmountSuccess(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyAmountFailed(RoutingContext ctx) {
        MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyAmountFailed(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyAmountSuccess(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyAmountSuccess(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyAmountFailed(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyAmountFailed(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyMethodsSuccess(RoutingContext ctx) {
        MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyMethodsSuccess(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyMethodsFailed(RoutingContext ctx) {
        MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyMethodsFailed(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyMethodsSuccess(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyMethodsSuccess(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyMethodsFailed(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyMethodsFailed(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    private Long getPathMerchantId(RoutingContext ctx) {
        return Long.parseLong(ctx.pathParam("merchantId"));
    }

    public void getMonthlyAmountSuccessByMerchant(RoutingContext ctx) {
        MonthAmountTransactionMerchant req = new MonthAmountTransactionMerchant();
        req.setMerchantId(getPathMerchantId(ctx).intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyAmountSuccessByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyAmountFailedByMerchant(RoutingContext ctx) {
        MonthAmountTransactionMerchant req = new MonthAmountTransactionMerchant();
        req.setMerchantId(getPathMerchantId(ctx).intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyAmountFailedByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyAmountSuccessByMerchant(RoutingContext ctx) {
        YearAmountTransactionMerchant req = new YearAmountTransactionMerchant();
        req.setMerchantId(getPathMerchantId(ctx).intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getYearlyAmountSuccessByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyAmountFailedByMerchant(RoutingContext ctx) {
        YearAmountTransactionMerchant req = new YearAmountTransactionMerchant();
        req.setMerchantId(getPathMerchantId(ctx).intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getYearlyAmountFailedByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyMethodsByMerchantSuccess(RoutingContext ctx) {
        MonthMethodTransactionMerchantRequest req = new MonthMethodTransactionMerchantRequest();
        req.setMerchantId(getPathMerchantId(ctx).intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyMethodsByMerchantSuccess(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyMethodsByMerchantFailed(RoutingContext ctx) {
        MonthMethodTransactionMerchantRequest req = new MonthMethodTransactionMerchantRequest();
        req.setMerchantId(getPathMerchantId(ctx).intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        service.getMonthlyMethodsByMerchantFailed(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyMethodsByMerchantSuccess(RoutingContext ctx) {
        YearMethodTransactionMerchantRequest req = new YearMethodTransactionMerchantRequest();
        req.setMerchantId(getPathMerchantId(ctx).intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getYearlyMethodsByMerchantSuccess(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyMethodsByMerchantFailed(RoutingContext ctx) {
        YearMethodTransactionMerchantRequest req = new YearMethodTransactionMerchantRequest();
        req.setMerchantId(getPathMerchantId(ctx).intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        service.getYearlyMethodsByMerchantFailed(req)
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

    private FindAllTransactionRequest mapFindAll(RoutingContext ctx) {
        FindAllTransactionRequest req = new FindAllTransactionRequest();
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