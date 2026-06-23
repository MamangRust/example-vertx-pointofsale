package com.sanedge.example_crud.handler;

import com.sanedge.example_crud.domain.requests.order.FindAllOrderRequest;
import com.sanedge.example_crud.exception.BadRequestException;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.exception.UnauthorizedException;
import com.sanedge.example_crud.service.OrderItemService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderItemHandler {
    private final OrderItemService service;

    public void findAll(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getAll(req)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getActive(req)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void findTrashed(RoutingContext ctx) {
        FindAllOrderRequest req = mapFindAll(ctx);
        service.getTrashed(req)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void findByOrderId(RoutingContext ctx) {
        Integer orderId = Integer.parseInt(ctx.pathParam("orderId"));
        service.getByOrderId(orderId)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
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
        if (v == null || v.isEmpty())
            return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void sendSuccess(RoutingContext ctx, int statusCode, Object res) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(res));
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        int statusCode = 500;
        if (err instanceof BadRequestException || err instanceof CustomException) {
            statusCode = 400;
        } else if (err instanceof NotFoundException) {
            statusCode = 404;
        } else if (err instanceof UnauthorizedException) {
            statusCode = 401;
        }

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(new JsonObject()
                        .put("status", "error")
                        .put("message", err.getMessage())));
    }

    public void trash(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.trash(id)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void restore(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.restore(id)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void deletePermanent(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.deletePermanent(id)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void restoreAll(RoutingContext ctx) {
        service.restoreAll()
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void deleteAllPermanent(RoutingContext ctx) {
        service.deleteAllPermanent()
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }
}