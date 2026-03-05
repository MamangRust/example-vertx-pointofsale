package com.sanedge.example_crud.routes;

import com.sanedge.example_crud.handler.AuthHandler;
import com.sanedge.example_crud.handler.CashierHandler;
import com.sanedge.example_crud.handler.CategoryHandler;
import com.sanedge.example_crud.handler.MerchantHandler;
import com.sanedge.example_crud.handler.OrderHandler;
import com.sanedge.example_crud.handler.OrderItemHandler;
import com.sanedge.example_crud.handler.ProductHandler;
import com.sanedge.example_crud.handler.RoleHandler;
import com.sanedge.example_crud.handler.TransactionHandler;
import com.sanedge.example_crud.handler.UserHandler;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public final class RouteRegistrar {

  private RouteRegistrar() {
  }

  public static Router register(
      Vertx vertx,
      JWTAuth jwtAuth,
      AuthHandler authHandler,
      UserHandler userHandler, RoleHandler roleHandler, CashierHandler cashierHandler, CategoryHandler categoryHandler, MerchantHandler merchantHandler, OrderHandler orderHandler, OrderItemHandler orderItemHandler, ProductHandler productHandler, TransactionHandler transactionHandler) {

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    AuthRoutes.mount(router, jwtAuth, authHandler);
    UserRoutes.mount(router, jwtAuth, userHandler);
    HealthRoutes.mount(router);
    RoleRoutes.mount(router, jwtAuth, roleHandler);
    CashierRoutes.mount(router, jwtAuth, cashierHandler);
    CategoryRoutes.mount(router, jwtAuth, categoryHandler);
    MerchantRoutes.mount(router, jwtAuth, merchantHandler);
    OrderRoutes.mount(router, jwtAuth, orderHandler);
    OrderItemRoutes.mount(router, jwtAuth, orderItemHandler);
    ProductRoutes.mount(router, jwtAuth, productHandler);
    TransactionRoutes.mount(router, jwtAuth, transactionHandler);

    return router;
  }
}
