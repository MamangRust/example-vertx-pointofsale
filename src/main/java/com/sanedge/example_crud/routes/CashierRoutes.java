package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.CashierHandler;
import com.sanedge.example_crud.middleware.JwtMiddleware;
import com.sanedge.example_crud.middleware.RoleMiddleware;

import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;

public final class CashierRoutes {
    private CashierRoutes() {}

    public static void mount(Router router, JWTAuth jwtAuth, CashierHandler handler) {
        router.route("/cashiers*").handler(JwtMiddleware.jwt(jwtAuth));

        router.get("/cashiers").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::findAll);
        router.get("/cashiers/active").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::findActive);
        router.get("/cashiers/trashed").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::findTrashed);
        router.get("/cashiers/merchant/:merchantId").handler(handler::findByMerchant);
        router.get("/cashiers/:id").handler(handler::findById);

        router.post("/cashiers").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::create);
        router.post("/cashiers/:id").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::update);
        
        router.post("/cashiers/trashed/:id").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::trash);
        router.post("/cashiers/restore/:id").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::restore);
        router.delete("/cashiers/deletePermanent/:id").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::deletePermanent);
        
        router.post("/cashiers/restore-all").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::restoreAll);
        router.delete("/cashiers/delete-all-permanent").handler(RoleMiddleware.requireRole("ADMIN")).handler(handler::deleteAllPermanent);

        router.get("/cashiers/reports/monthly-total").handler(handler::getMonthlyTotalSales);
        router.get("/cashiers/reports/yearly-total").handler(handler::getYearlyTotalSales);
        router.get("/cashiers/reports/monthly-cashier").handler(handler::getMonthlyCashier);
        router.get("/cashiers/reports/yearly-cashier").handler(handler::getYearlyCashier);

        router.get("/cashiers/reports/monthly-total/:id").handler(handler::getMonthlyTotalSalesById);
        router.get("/cashiers/reports/yearly-total/:id").handler(handler::getYearlyTotalSalesById);
        router.get("/cashiers/reports/monthly-cashier/:id").handler(handler::getMonthlyCashierById);
        router.get("/cashiers/reports/yearly-cashier/:id").handler(handler::getYearlyCashierById);

        router.get("/cashiers/reports/monthly-total/merchant/:merchantId").handler(handler::getMonthlyTotalSalesByMerchant);
        router.get("/cashiers/reports/yearly-total/merchant/:merchantId").handler(handler::getYearlyTotalSalesByMerchant);
        router.get("/cashiers/reports/monthly-cashier/merchant/:merchantId").handler(handler::getMonthlyCashierByMerchant);
        router.get("/cashiers/reports/yearly-cashier/merchant/:merchantId").handler(handler::getYearlyCashierByMerchant);
    }
}