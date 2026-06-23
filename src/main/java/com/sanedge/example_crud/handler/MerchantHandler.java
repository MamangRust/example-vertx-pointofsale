package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.merchant.CreateMerchantRequest;
import com.sanedge.example_crud.domain.requests.merchant.FindAllMerchants;
import com.sanedge.example_crud.domain.requests.merchant.UpdateMerchantRequest;
import com.sanedge.example_crud.service.MerchantService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MerchantHandler {
    private final MerchantService service;

    public void findAll(RoutingContext ctx) {
        FindAllMerchants req = mapFindAll(ctx);
        service.getAll(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllMerchants req = mapFindAll(ctx);
        service.getActive(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findTrashed(RoutingContext ctx) {
        FindAllMerchants req = mapFindAll(ctx);
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

    public void create(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            sendErrorResponse(ctx, new com.sanedge.example_crud.exception.BadRequestException("Request body cannot be empty"));
            return;
        }
        CreateMerchantRequest req = body.mapTo(CreateMerchantRequest.class);

        service.create(req)
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
        UpdateMerchantRequest req = body.mapTo(UpdateMerchantRequest.class);
        req.setMerchantId(id.intValue());

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

    private FindAllMerchants mapFindAll(RoutingContext ctx) {
        FindAllMerchants req = new FindAllMerchants();
        req.setSearch(ctx.queryParams().get("search"));
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));
        return req;
    }

    private int getQueryParamInt(RoutingContext ctx, String key, int def) {
        String v = ctx.queryParams().get(key);
        return v == null ? def : Integer.parseInt(v);
    }

    private void sendResponse(RoutingContext ctx, Object res) {
        ctx.response().setStatusCode(200).putHeader("Content-Type", "application/json").end(Json.encodePrettily(res));
    }

    private void sendErrorResponse(RoutingContext ctx, Throwable err) {
        ctx.response().setStatusCode(500).putHeader("Content-Type", "application/json").end(
                Json.encodePrettily(com.sanedge.example_crud.domain.response.api.ApiResponse.error(err.getMessage())));
    }
}