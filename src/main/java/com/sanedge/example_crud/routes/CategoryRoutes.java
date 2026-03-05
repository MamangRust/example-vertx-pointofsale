package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.CategoryHandler;
import com.sanedge.example_crud.middleware.JwtMiddleware;
import com.sanedge.example_crud.middleware.RoleMiddleware;

import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;

public final class CategoryRoutes {
    private CategoryRoutes() {
    }

    public static void mount(Router router, JWTAuth jwtAuth, CategoryHandler handler) {
        router.route("/categories*").handler(JwtMiddleware.jwt(jwtAuth));

        router.get("/categories")
                .handler(RoleMiddleware.requireRole("ADMIN", "USER"))
                .handler(handler::findAll);

        router.get("/categories/active")
                .handler(RoleMiddleware.requireRole("ADMIN", "USER"))
                .handler(handler::findActive);

        router.get("/categories/trashed")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::findTrashed);

        router.get("/categories/:id")
                .handler(handler::findById);

        router.post("/categories")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::create);

        router.post("/categories/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::update);

        router.post("/categories/trashed/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::trash);

        router.post("/categories/restore/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::restore);

        router.delete("/categories/deletePermanent/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::deletePermanent);

        router.post("/categories/restore-all")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::restoreAll);

        router.delete("/categories/delete-all-permanent")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::deleteAllPermanent);

        router.get("/categories/reports/monthly-total")
                .handler(handler::getMonthlyTotalPrice);

        router.get("/categories/reports/yearly-total")
                .handler(handler::getYearlyTotalPrice);

        router.get("/categories/reports/monthly-total/merchant/:merchantId")
                .handler(handler::getMonthlyTotalPriceByMerchant);

        router.get("/categories/reports/yearly-total/merchant/:merchantId")
                .handler(handler::getYearlyTotalPriceByMerchant);

        router.get("/categories/reports/monthly-total/:id")
                .handler(handler::getMonthlyTotalPriceById);

        router.get("/categories/reports/yearly-total/:id")
                .handler(handler::getYearlyTotalPriceById);

        router.get("/categories/reports/monthly-category")
                .handler(handler::getMonthlyCategory);

        router.get("/categories/reports/yearly-category")
                .handler(handler::getYearlyCategory);

        router.get("/categories/reports/monthly-category/merchant/:merchantId")
                .handler(handler::getMonthlyCategoryByMerchant);

        router.get("/categories/reports/yearly-category/merchant/:merchantId")
                .handler(handler::getYearlyCategoryByMerchant);

        router.get("/categories/reports/monthly-category/:id")
                .handler(handler::getMonthlyCategoryById);

        router.get("/categories/reports/yearly-category/:id")
                .handler(handler::getYearlyCategoryById);
    }
}