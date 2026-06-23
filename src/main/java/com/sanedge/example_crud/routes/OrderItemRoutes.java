package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.OrderItemHandler;
import com.sanedge.example_crud.middleware.JwtMiddleware;
import com.sanedge.example_crud.middleware.RoleMiddleware;
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

        router.post("/order-items/trashed/:id").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::trash);
        router.post("/order-items/restore/:id").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::restore);
        router.delete("/order-items/deletePermanent/:id").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::deletePermanent);

        router.post("/order-items/restore-all").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::restoreAll);
        router.delete("/order-items/delete-all-permanent").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::deleteAllPermanent);
    }
}