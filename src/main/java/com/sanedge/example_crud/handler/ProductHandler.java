package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.product.CreateProductRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductByCategoryRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductByMerchantRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductRequest;
import com.sanedge.example_crud.domain.requests.product.UpdateProductRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.service.ProductService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductHandler {
    private final ProductService service;

    public void findAll(RoutingContext ctx) {
        FindAllProductRequest req = mapFindAll(ctx);
        service.getAll(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllProductRequest req = mapFindAll(ctx);
        service.getActive(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findTrashed(RoutingContext ctx) {
        FindAllProductRequest req = mapFindAll(ctx);
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

    public void findByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));

        FindAllProductByMerchantRequest req = new FindAllProductByMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setSearch(ctx.queryParams().get("search"));
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));

        if (ctx.queryParams().contains("categoryId")) {
            req.setCategoryId(Integer.valueOf(ctx.queryParams().get("categoryId")));
        }
        if (ctx.queryParams().contains("minPrice")) {
            req.setMinPrice(Integer.valueOf(ctx.queryParams().get("minPrice")));
        }
        if (ctx.queryParams().contains("maxPrice")) {
            req.setMaxPrice(Integer.valueOf(ctx.queryParams().get("maxPrice")));
        }

        service.getByMerchant(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findByCategory(RoutingContext ctx) {
        String categoryName = ctx.pathParam("categoryName");

        FindAllProductByCategoryRequest req = new FindAllProductByCategoryRequest();
        req.setCategoryName(categoryName);
        req.setSearch(ctx.queryParams().get("search"));
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));

        if (ctx.queryParams().contains("minPrice")) {
            req.setMinPrice(Integer.valueOf(ctx.queryParams().get("minPrice")));
        }
        if (ctx.queryParams().contains("maxPrice")) {
            req.setMaxPrice(Integer.valueOf(ctx.queryParams().get("maxPrice")));
        }

        service.getByCategoryName(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }


    public void create(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        CreateProductRequest req = body.mapTo(CreateProductRequest.class);

        service.create(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void update(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        JsonObject body = ctx.body().asJsonObject();
        UpdateProductRequest req = body.mapTo(UpdateProductRequest.class);
        req.setProductId(id.intValue());

        service.update(req)
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

    private FindAllProductRequest mapFindAll(RoutingContext ctx) {
        FindAllProductRequest req = new FindAllProductRequest();
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