package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.order.FindAllOrderRequest;
import com.sanedge.example_crud.service.OrderItemService;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderItemHandler {
    private final OrderItemService service;

    public void findAll(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getAll(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getActive(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    public void findTrashed(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getTrashed(req)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }
    
    public void findByOrderId(RoutingContext ctx) {
        Integer orderId = Integer.parseInt(ctx.pathParam("orderId"));
        service.getByOrderId(orderId)
                .onSuccess(res -> sendResponse(ctx, res))
                .onFailure(err -> sendErrorResponse(ctx, err));
    }

    private FindAllOrderRequest mapFindAll(RoutingContext ctx) {
        FindAllOrderRequest req = new FindAllOrderRequest();
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
        ctx.response().setStatusCode(500).putHeader("Content-Type", "application/json").end(Json.encodePrettily(com.sanedge.example_crud.domain.response.api.ApiResponse.error(err.getMessage())));
    }
}