package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.MerchantHandler;
import com.sanedge.example_crud.middleware.JwtMiddleware;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;

public final class MerchantRoutes {
    private MerchantRoutes() {}
    public static void mount(Router router, JWTAuth jwtAuth, MerchantHandler handler) {
        router.route("/merchants*").handler(JwtMiddleware.jwt(jwtAuth));

        router.get("/merchants").handler(handler::findAll);
        router.get("/merchants/active").handler(handler::findActive);
        router.get("/merchants/trashed").handler(handler::findTrashed);
        router.get("/merchants/:id").handler(handler::findById);
    }
}