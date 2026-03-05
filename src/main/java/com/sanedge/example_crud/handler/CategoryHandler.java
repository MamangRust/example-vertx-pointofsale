package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.category.CreateCategoryRequest;
import com.sanedge.example_crud.domain.requests.category.FindAllCategory;
import com.sanedge.example_crud.domain.requests.category.MonthPriceMerchant;
import com.sanedge.example_crud.domain.requests.category.MonthTotalPrice;
import com.sanedge.example_crud.domain.requests.category.MonthTotalPriceCategory;
import com.sanedge.example_crud.domain.requests.category.MonthTotalPriceMerchant;
import com.sanedge.example_crud.domain.requests.category.UpdateCategoryRequest;
import com.sanedge.example_crud.domain.requests.category.YearPriceId;
import com.sanedge.example_crud.domain.requests.category.YearPriceMerchant;
import com.sanedge.example_crud.domain.requests.category.YearTotalPriceCategory;
import com.sanedge.example_crud.domain.requests.category.YearTotalPriceMerchant;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.service.CategoryService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CategoryHandler {
    private final CategoryService service;


    public void findAll(RoutingContext ctx) {
        FindAllCategory req = mapFindAll(ctx);
        service.getCategories(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllCategory req = mapFindAll(ctx);
        service.getCategoriesActive(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findTrashed(RoutingContext ctx) {
        FindAllCategory req = mapFindAll(ctx);
        service.getTrashedCategories(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findById(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.getCategoryById(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void create(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        CreateCategoryRequest req = body.mapTo(CreateCategoryRequest.class);
        service.createCategory(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void update(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        JsonObject body = ctx.body().asJsonObject();
        UpdateCategoryRequest req = body.mapTo(UpdateCategoryRequest.class);
        req.setCategoryId(id.intValue());
        
        service.updateCategory(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void trash(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.trashCategory(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void restore(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.restoreCategory(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deletePermanent(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.deleteCategoryPermanently(id)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void restoreAll(RoutingContext ctx) {
        service.restoreAllCategories(ctx)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void deleteAllPermanent(RoutingContext ctx) {
        service.deleteAllPermanentCategories(ctx)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyTotalPrice(RoutingContext ctx) {
        MonthTotalPrice req = new MonthTotalPrice();
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));
        
        service.getMonthlyTotalPrice(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyTotalPrice(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyTotalPrice(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyTotalPriceByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        MonthTotalPriceMerchant req = new MonthTotalPriceMerchant();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));

        service.getMonthlyTotalPriceByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyTotalPriceByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        YearTotalPriceMerchant req = new YearTotalPriceMerchant();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getYearlyTotalPriceByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyTotalPriceById(RoutingContext ctx) {
        Long categoryId = Long.parseLong(ctx.pathParam("id"));
        MonthTotalPriceCategory req = new MonthTotalPriceCategory();
        req.setCategoryId(categoryId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));
        req.setMonth(getQueryParamInt(ctx, "month", 1));

        service.getMonthlyTotalPriceById(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyTotalPriceById(RoutingContext ctx) {
        Long categoryId = Long.parseLong(ctx.pathParam("id"));
        YearTotalPriceCategory req = new YearTotalPriceCategory();
        req.setCategoryId(categoryId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getYearlyTotalPriceById(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyCategory(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getMonthlyCategory(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyCategory(RoutingContext ctx) {
        int year = getQueryParamInt(ctx, "year", 2024);
        service.getYearlyCategory(year)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyCategoryByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        MonthPriceMerchant req = new MonthPriceMerchant();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getMonthlyCategoryByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyCategoryByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));
        YearPriceMerchant req = new YearPriceMerchant();
        req.setMerchantId(merchantId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getYearlyCategoryByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getMonthlyCategoryById(RoutingContext ctx) {
        Long categoryId = Long.parseLong(ctx.pathParam("id"));
        YearPriceId req = new YearPriceId(); 
        req.setCategoryId(categoryId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getMonthlyCategoryById(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void getYearlyCategoryById(RoutingContext ctx) {
        Long categoryId = Long.parseLong(ctx.pathParam("id"));
        YearPriceId req = new YearPriceId();
        req.setCategoryId(categoryId.intValue());
        req.setYear(getQueryParamInt(ctx, "year", 2024));

        service.getYearlyCategoryById(req)
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

    private FindAllCategory mapFindAll(RoutingContext ctx) {
        FindAllCategory req = new FindAllCategory();
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