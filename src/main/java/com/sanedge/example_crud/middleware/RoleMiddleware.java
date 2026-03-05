package com.sanedge.example_crud.middleware;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;

public final class RoleMiddleware {

  private RoleMiddleware() {
  }

  public static Handler<RoutingContext> requireRole(String... requiredRoles) {
    return ctx -> {
      JsonArray userRoles = ctx.user().principal().getJsonArray("roleNames");

      if (userRoles == null) {
        ctx.response().setStatusCode(403).end("Forbidden");
        return;
      }

      for (String role : requiredRoles) {
        if (userRoles.contains(role)) {
          ctx.next();
          return;
        }
      }

      ctx.response().setStatusCode(403).end("Forbidden");
    };
  }
}