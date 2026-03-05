package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.OrderHandler;
import com.sanedge.example_crud.middleware.JwtMiddleware;
import com.sanedge.example_crud.middleware.RoleMiddleware;

import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;

public final class OrderRoutes {
    private OrderRoutes() {
    }

    public static void mount(Router router, JWTAuth jwtAuth, OrderHandler handler) {
        router.route("/orders*").handler(JwtMiddleware.jwt(jwtAuth));


        router.get("/orders")
                .handler(RoleMiddleware.requireRole("ADMIN", "CASHIER"))
                .handler(handler::findAll);

        router.get("/orders/active")
                .handler(RoleMiddleware.requireRole("ADMIN", "CASHIER"))
                .handler(handler::findActive);

        router.get("/orders/merchant/:merchantId")
                .handler(RoleMiddleware.requireRole("ADMIN", "MERCHANT"))
                .handler(handler::findByMerchant);

        router.get("/orders/:id")
                .handler(handler::findById);

        router.post("/orders")
                .handler(RoleMiddleware.requireRole("ADMIN", "CASHIER"))
                .handler(handler::create);

        router.post("/orders/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::update);

        router.post("/orders/trashed/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::trash);

        router.post("/orders/restore/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::restore);

        router.delete("/orders/deletePermanent/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::deletePermanent);

        router.post("/orders/restore-all")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::restoreAll);

        router.delete("/orders/delete-all-permanent")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::deleteAllPermanent);

        router.get("/orders/reports/monthly-revenue")
                .handler(handler::getMonthlyTotalRevenue);

        router.get("/orders/reports/yearly-revenue")
                .handler(handler::getYearlyTotalRevenue);

        router.get("/orders/reports/monthly-revenue/merchant/:merchantId")
                .handler(handler::getMonthlyTotalRevenueByMerchant);

        router.get("/orders/reports/yearly-revenue/merchant/:merchantId")
                .handler(handler::getYearlyTotalRevenueByMerchant);

        router.get("/orders/reports/monthly-order")
                .handler(handler::getMonthlyOrder);

        router.get("/orders/reports/yearly-order")
                .handler(handler::getYearlyOrder);

        router.get("/orders/reports/monthly-order/merchant/:merchantId")
                .handler(handler::getMonthlyOrderByMerchant);

        router.get("/orders/reports/yearly-order/merchant/:merchantId")
                .handler(handler::getYearlyOrderByMerchant);
    }
}