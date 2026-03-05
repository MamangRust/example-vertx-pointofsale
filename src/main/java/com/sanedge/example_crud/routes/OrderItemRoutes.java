package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.OrderItemHandler;
import com.sanedge.example_crud.middleware.JwtMiddleware;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;

public final class OrderItemRoutes {
    private OrderItemRoutes() {}
    public static void mount(Router router, JWTAuth jwtAuth, OrderItemHandler handler) {
        router.route("/order-items*").handler(JwtMiddleware.jwt(jwtAuth));

        router.get("/order-items").handler(handler::findAll);
        router.get("/order-items/active").handler(handler::findActive);
        router.get("/order-items/trashed").handler(handler::findTrashed);
        router.get("/order-items/order/:orderId").handler(handler::findByOrderId);
    }
}