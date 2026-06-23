package com.sanedge.example_crud.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.product.CreateProductRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductByCategoryRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductByMerchantRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductRequest;
import com.sanedge.example_crud.domain.requests.product.UpdateProductRequest;
import com.sanedge.example_crud.exception.BadRequestException;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.exception.UnauthorizedException;
import com.sanedge.example_crud.service.ProductService;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductHandler {
    private final ProductService service;
    private static final Logger logger = LoggerFactory.getLogger(ProductHandler.class);
    private static final String UPLOAD_DIRECTORY = "uploads/products/";

    public void findAll(RoutingContext ctx) {
        FindAllProductRequest req = mapFindAll(ctx);
        service.getAll(req)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void findActive(RoutingContext ctx) {
        FindAllProductRequest req = mapFindAll(ctx);
        service.getActive(req)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void findTrashed(RoutingContext ctx) {
        FindAllProductRequest req = mapFindAll(ctx);
        service.getTrashed(req)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void findById(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.getById(id)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void findByMerchant(RoutingContext ctx) {
        Long merchantId = Long.parseLong(ctx.pathParam("merchantId"));

        FindAllProductByMerchantRequest req = new FindAllProductByMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setSearch(ctx.queryParams().get("search"));
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));

        if (ctx.queryParams().contains("categoryId")) {
            req.setCategoryId(getQueryParamInt(ctx, "categoryId", 0));
        }
        if (ctx.queryParams().contains("minPrice")) {
            req.setMinPrice(getQueryParamInt(ctx, "minPrice", 0));
        }
        if (ctx.queryParams().contains("maxPrice")) {
            req.setMaxPrice(getQueryParamInt(ctx, "maxPrice", 0));
        }

        service.getByMerchant(req)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void findByCategory(RoutingContext ctx) {
        String categoryName = ctx.pathParam("categoryName");

        FindAllProductByCategoryRequest req = new FindAllProductByCategoryRequest();
        req.setCategoryName(categoryName);
        req.setSearch(ctx.queryParams().get("search"));
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));

        if (ctx.queryParams().contains("minPrice")) {
            req.setMinPrice(getQueryParamInt(ctx, "minPrice", 0));
        }
        if (ctx.queryParams().contains("maxPrice")) {
            req.setMaxPrice(getQueryParamInt(ctx, "maxPrice", 0));
        }

        service.getByCategoryName(req)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void create(RoutingContext ctx) {
        try {
            FileUpload imageUpload = ctx.fileUploads()
                    .stream()
                    .filter(f -> "image".equals(f.name()) || "image_product".equals(f.name()))
                    .findFirst()
                    .orElse(null);

            String imageUrl = null;
            if (imageUpload != null) {
                imageUrl = storeUploadedFile(imageUpload);
            }

            CreateProductRequest req = new CreateProductRequest();
            req.setMerchantId(Integer.parseInt(getRequiredFormAttr(ctx, "merchant_id")));
            req.setCategoryId(Integer.parseInt(getRequiredFormAttr(ctx, "category_id")));
            req.setName(getRequiredFormAttr(ctx, "name"));
            req.setDescription(ctx.request().getFormAttribute("description"));
            req.setPrice(Integer.parseInt(getRequiredFormAttr(ctx, "price")));
            req.setCountInStock(Integer.parseInt(getRequiredFormAttr(ctx, "count_in_stock")));
            req.setBrand(ctx.request().getFormAttribute("brand"));
            req.setWeight(ctx.request().getFormAttribute("weight") != null ? Integer.parseInt(ctx.request().getFormAttribute("weight")) : null);
            req.setRating(ctx.request().getFormAttribute("rating") != null ? Integer.parseInt(ctx.request().getFormAttribute("rating")) : null);
            req.setSlugProduct(ctx.request().getFormAttribute("slug_product"));
            req.setImageProduct(imageUrl);

            service.create(req)
                    .onSuccess(res -> sendSuccess(ctx, 201, res))
                    .onFailure(err -> handleError(ctx, err));
        } catch (NumberFormatException e) {
            handleError(ctx, new BadRequestException("numeric fields must be valid numbers"));
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    public void update(RoutingContext ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));

            FileUpload imageUpload = ctx.fileUploads()
                    .stream()
                    .filter(f -> "image".equals(f.name()) || "image_product".equals(f.name()))
                    .findFirst()
                    .orElse(null);

            String imageUrl = null;
            if (imageUpload != null) {
                imageUrl = storeUploadedFile(imageUpload);
            }

            UpdateProductRequest req = new UpdateProductRequest();
            req.setProductId(id.intValue());
            req.setMerchantId(Integer.parseInt(getRequiredFormAttr(ctx, "merchant_id")));
            req.setCategoryId(Integer.parseInt(getRequiredFormAttr(ctx, "category_id")));
            req.setName(getRequiredFormAttr(ctx, "name"));
            req.setDescription(ctx.request().getFormAttribute("description"));
            req.setPrice(Integer.parseInt(getRequiredFormAttr(ctx, "price")));
            req.setCountInStock(Integer.parseInt(getRequiredFormAttr(ctx, "count_in_stock")));
            req.setBrand(ctx.request().getFormAttribute("brand"));
            req.setWeight(ctx.request().getFormAttribute("weight") != null ? Integer.parseInt(ctx.request().getFormAttribute("weight")) : null);
            req.setRating(ctx.request().getFormAttribute("rating") != null ? Integer.parseInt(ctx.request().getFormAttribute("rating")) : null);
            req.setSlugProduct(ctx.request().getFormAttribute("slug_product"));
            if (imageUrl != null) {
                req.setImageProduct(imageUrl);
            } else {
                req.setImageProduct(ctx.request().getFormAttribute("image_product"));
            }

            service.update(req)
                    .onSuccess(res -> sendSuccess(ctx, 200, res))
                    .onFailure(err -> handleError(ctx, err));
        } catch (NumberFormatException e) {
            handleError(ctx, new BadRequestException("numeric fields must be valid numbers"));
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    public void trash(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.trash(id)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void restore(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.restore(id)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void deletePermanent(RoutingContext ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        service.deletePermanent(id)
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void restoreAll(RoutingContext ctx) {
        service.restoreAll()
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    public void deleteAllPermanent(RoutingContext ctx) {
        service.deleteAllPermanent()
                .onSuccess(res -> sendSuccess(ctx, 200, res))
                .onFailure(err -> handleError(ctx, err));
    }

    private void sendSuccess(RoutingContext ctx, int statusCode, Object res) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(res));
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        int statusCode = 500;
        if (err instanceof BadRequestException) {
            statusCode = 400;
        } else if (err instanceof NotFoundException) {
            statusCode = 404;
        } else if (err instanceof UnauthorizedException) {
            statusCode = 401;
        }

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(new JsonObject()
                        .put("status", "error")
                        .put("message", err.getMessage())));
    }

    private FindAllProductRequest mapFindAll(RoutingContext ctx) {
        FindAllProductRequest req = new FindAllProductRequest();
        req.setSearch(ctx.queryParams().get("search"));
        req.setPage(getQueryParamInt(ctx, "page", 1));
        req.setPageSize(getQueryParamInt(ctx, "pageSize", 10));
        return req;
    }

    private int getQueryParamInt(RoutingContext ctx, String key, int defaultValue) {
        String val = ctx.queryParams().get(key);
        if (val == null || val.isEmpty())
            return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getRequiredFormAttr(RoutingContext ctx, String key) {
        String value = ctx.request().getFormAttribute(key);
        if (value == null || value.isBlank()) {
            throw new BadRequestException(key + " is required");
        }
        return value;
    }

    private String storeUploadedFile(FileUpload fileUpload) {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(UPLOAD_DIRECTORY));

            String fileName = System.currentTimeMillis() + "_" + fileUpload.fileName();
            java.nio.file.Path source = java.nio.file.Paths.get(fileUpload.uploadedFileName());
            java.nio.file.Path target = java.nio.file.Paths.get(UPLOAD_DIRECTORY + fileName);

            java.nio.file.Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return "/downloads/" + fileName;
        } catch (java.io.IOException e) {
            logger.error("Failed to store uploaded file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store uploaded file: " + e.getMessage(), e);
        }
    }
}