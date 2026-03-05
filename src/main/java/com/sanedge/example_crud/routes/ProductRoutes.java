package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.ProductHandler;
import com.sanedge.example_crud.middleware.JwtMiddleware;
import com.sanedge.example_crud.middleware.RoleMiddleware;

import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;

public final class ProductRoutes {
    private ProductRoutes() {
    }

    public static void mount(Router router, JWTAuth jwtAuth, ProductHandler handler) {
        router.route("/products*").handler(JwtMiddleware.jwt(jwtAuth));

        
        router.get("/products")
                .handler(handler::findAll);

        router.get("/products/active")
                .handler(handler::findActive);

        router.get("/products/trashed")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::findTrashed);

        router.get("/products/merchant/:merchantId")
                .handler(handler::findByMerchant);

        router.get("/products/category/:categoryName")
                .handler(handler::findByCategory);

        router.get("/products/:id")
                .handler(handler::findById);


        router.post("/products")
                .handler(RoleMiddleware.requireRole("ADMIN", "MERCHANT"))
                .handler(handler::create);

        router.post("/products/:id")
                .handler(RoleMiddleware.requireRole("ADMIN", "MERCHANT"))
                .handler(handler::update);

        router.post("/products/trashed/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::trash);

        router.post("/products/restore/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::restore);

        router.delete("/products/deletePermanent/:id")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::deletePermanent);

        router.post("/products/restore-all")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::restoreAll);

        router.delete("/products/delete-all-permanent")
                .handler(RoleMiddleware.requireRole("ADMIN"))
                .handler(handler::deleteAllPermanent);
    }
}