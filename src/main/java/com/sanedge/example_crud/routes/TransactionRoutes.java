package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.TransactionHandler;
import com.sanedge.example_crud.middleware.JwtMiddleware;
import com.sanedge.example_crud.middleware.RoleMiddleware;

import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;

public final class TransactionRoutes {
    private TransactionRoutes() {
    }

    public static void mount(Router router, JWTAuth jwtAuth, TransactionHandler handler) {
        router.route("/transactions*").handler(JwtMiddleware.jwt(jwtAuth));

        router.get("/transactions")
                .handler(RoleMiddleware.requireRole("ADMIN", "CASHIER"))
                .handler(handler::findAll);

        router.get("/transactions/active")
                .handler(RoleMiddleware.requireRole("ADMIN", "CASHIER"))
                .handler(handler::findActive);

        router.get("/transactions/trashed")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::findTrashed);

        router.get("/transactions/order/:orderId")
                .handler(handler::findByOrderId);

        router.get("/transactions/merchant/:merchantId")
                .handler(RoleMiddleware.requireRole("ADMIN", "MERCHANT"))
                .handler(handler::findByMerchant);

        router.get("/transactions/:id")
                .handler(handler::findById);

        router.post("/transactions")
                .handler(RoleMiddleware.requireRole("ADMIN", "CASHIER"))
                .handler(handler::create);

        router.post("/transactions/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::update);

        router.post("/transactions/trashed/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::trash);

        router.post("/transactions/restore/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::restore);

        router.delete("/transactions/deletePermanent/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::deletePermanent);

        router.post("/transactions/restore-all")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::restoreAll);

        router.delete("/transactions/delete-all-permanent")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::deleteAllPermanent);

        router.get("/transactions/reports/monthly-amount-success")
                .handler(handler::getMonthlyAmountSuccess);
        router.get("/transactions/reports/monthly-amount-failed")
                .handler(handler::getMonthlyAmountFailed);
        router.get("/transactions/reports/yearly-amount-success")
                .handler(handler::getYearlyAmountSuccess);
        router.get("/transactions/reports/yearly-amount-failed")
                .handler(handler::getYearlyAmountFailed);

        router.get("/transactions/reports/monthly-methods-success")
                .handler(handler::getMonthlyMethodsSuccess);
        router.get("/transactions/reports/monthly-methods-failed")
                .handler(handler::getMonthlyMethodsFailed);
        router.get("/transactions/reports/yearly-methods-success")
                .handler(handler::getYearlyMethodsSuccess);
        router.get("/transactions/reports/yearly-methods-failed")
                .handler(handler::getYearlyMethodsFailed);

        router.get("/transactions/reports/merchant/:merchantId/monthly-amount-success")
                .handler(handler::getMonthlyAmountSuccessByMerchant);
        router.get("/transactions/reports/merchant/:merchantId/monthly-amount-failed")
                .handler(handler::getMonthlyAmountFailedByMerchant);
        router.get("/transactions/reports/merchant/:merchantId/yearly-amount-success")
                .handler(handler::getYearlyAmountSuccessByMerchant);
        router.get("/transactions/reports/merchant/:merchantId/yearly-amount-failed")
                .handler(handler::getYearlyAmountFailedByMerchant);

        router.get("/transactions/reports/merchant/:merchantId/monthly-methods-success")
                .handler(handler::getMonthlyMethodsByMerchantSuccess);
        router.get("/transactions/reports/merchant/:merchantId/monthly-methods-failed")
                .handler(handler::getMonthlyMethodsByMerchantFailed);
        router.get("/transactions/reports/merchant/:merchantId/yearly-methods-success")
                .handler(handler::getYearlyMethodsByMerchantSuccess);
        router.get("/transactions/reports/merchant/:merchantId/yearly-methods-failed")
                .handler(handler::getYearlyMethodsByMerchantFailed);
    }
}