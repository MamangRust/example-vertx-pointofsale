package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.product.CreateProductRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductByCategoryRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductByMerchantRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductRequest;
import com.sanedge.example_crud.domain.requests.product.UpdateProductRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.domain.response.product.ProductResponse;
import com.sanedge.example_crud.domain.response.product.ProductResponseDeleteAt;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.model.Product;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.ProductRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;

    public Future<ApiResponsePagination<List<ProductResponse>>> getAll(FindAllProductRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.getAll");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedProducts(req, "products:all", page, pageSize, keyword,
                repository::getProducts, ProductResponse::from, ctx, "get_all",
                "Products fetched successfully");
    }

    public Future<ApiResponsePagination<List<ProductResponse>>> getActive(FindAllProductRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.getActive");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedProducts(req, "products:active", page, pageSize, keyword,
                repository::getProductsActive, ProductResponse::from, ctx, "get_active",
                "Active products fetched successfully");
    }

    public Future<ApiResponsePagination<List<ProductResponseDeleteAt>>> getTrashed(FindAllProductRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.getTrashed");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedProducts(req, "products:trashed", page, pageSize, keyword,
                repository::getProductsTrashed, ProductResponseDeleteAt::from, ctx, "get_trashed",
                "Trashed products fetched successfully");
    }

    public Future<ApiResponsePagination<List<ProductResponse>>> getByMerchant(FindAllProductByMerchantRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.getByMerchant",
                Attributes.builder().put("merchant.id", req.getMerchantId()).build());

        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() != null ? req.getPageSize() : 10;

        String cachePrefix = String.format("products:merchant:%d:search:%s:cat:%s:min:%s:max:%s",
                req.getMerchantId(), req.getSearch(), req.getCategoryId(), req.getMinPrice(), req.getMaxPrice());

        return fetchPaginatedProducts(req, cachePrefix, page, pageSize, "",
                repository::getProductsByMerchant, ProductResponse::from, ctx, "get_by_merchant",
                "Products by merchant fetched successfully");
    }

    public Future<ApiResponsePagination<List<ProductResponse>>> getByCategoryName(FindAllProductByCategoryRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.getByCategoryName");

        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() != null ? req.getPageSize() : 10;

        String cachePrefix = String.format("products:category:%s:search:%s:min:%s:max:%s",
                req.getCategoryName(), req.getSearch(), req.getMinPrice(), req.getMaxPrice());

        return fetchPaginatedProducts(req, cachePrefix, page, pageSize, "",
                repository::getProductsByCategoryName, ProductResponse::from, ctx, "get_by_category",
                "Products by category fetched successfully");
    }

    public Future<ApiResponse<ProductResponse>> getById(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.getById",
                Attributes.builder().put("id", id).build());
        Span span = Span.fromContext(ctx.getContext());

        String cacheKey = "product:" + id;

        return redisService.get(cacheKey)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        span.setAttribute("cache.hit", true);
                        try {
                            Product product = Product.fromJson(new JsonObject(cached));
                            tracingMetrics.completeSpanSuccess(ctx, "get_by_id", "Product fetched from cache");
                            return Future.succeededFuture(
                                    ApiResponse.success("Product fetched successfully (from cache)",
                                            ProductResponse.from(product)));
                        } catch (Exception e) {
                            logger.warn("Failed to parse cached product data for product {}: {}", id, e.getMessage());
                            return fetchProductFromDatabase(id, ctx);
                        }
                    }
                    span.setAttribute("cache.hit", false);
                    return fetchProductFromDatabase(id, ctx);
                })
                .recover(err -> {
                    logger.error("Failed to fetch product by id: {}", id, err);
                    tracingMetrics.completeSpanError(ctx, "get_by_id", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponse>error("Failed to fetch product: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponse>> create(CreateProductRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.create",
                Attributes.builder().put("product.name", req.getName()).build());
        Span span = Span.fromContext(ctx.getContext());

        return repository.createProduct(req)
                .map(data -> {
                    span.setAttribute("id", data.getProductId());
                    tracingMetrics.completeSpanSuccess(ctx, "create", "Success");
                    return ApiResponse.success("Product created", ProductResponse.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to create product", err);
                    tracingMetrics.completeSpanError(ctx, "create", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponse>error("Failed to create: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponse>> update(UpdateProductRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.update",
                Attributes.builder().put("id", req.getProductId()).build());

        return repository.updateProduct(req)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Product not found"));
                    }
                    invalidateCache(req.getProductId().longValue());
                    tracingMetrics.completeSpanSuccess(ctx, "update", "Success");
                    return Future.succeededFuture(ApiResponse.success("Product updated", ProductResponse.from(data)));
                })
                .recover(err -> {
                    logger.error("Failed to update product: {}", req.getProductId(), err);
                    tracingMetrics.completeSpanError(ctx, "update", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponse>error("Failed to update: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponseDeleteAt>> trash(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.trash",
                Attributes.builder().put("id", id).build());

        return repository.trashProduct(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Product not found"));
                    }
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "trash", "Success");
                    return Future.succeededFuture(
                            ApiResponse.success("Product trashed", ProductResponseDeleteAt.from(data)));
                })
                .recover(err -> {
                    logger.error("Failed to trash product: {}", id, err);
                    tracingMetrics.completeSpanError(ctx, "trash", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponseDeleteAt>error("Failed to trash: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponseDeleteAt>> restore(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.restore",
                Attributes.builder().put("id", id).build());

        return repository.findByTrashed(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Product not found"));
                    }
                    return repository.restoreProduct(id);
                })
                .compose(data -> {
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "restore", "Success");
                    return Future.succeededFuture(
                            ApiResponse.success("Product restored", ProductResponseDeleteAt.from(data)));
                })
                .recover(err -> {
                    logger.error("Failed to restore product: {}", id, err);
                    tracingMetrics.completeSpanError(ctx, "restore", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponseDeleteAt>error("Failed to restore: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> deletePermanent(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.deletePermanent",
                Attributes.builder().put("id", id).build());

        return repository.findByTrashed(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Product not found"));
                    }
                    return repository.deleteProductPermanently(id);
                })
                .map(v -> {
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "delete_permanent", "Success");
                    return ApiResponse.success("Product deleted permanently", true);
                })
                .recover(err -> {
                    logger.error("Failed to delete product: {}", id, err);
                    tracingMetrics.completeSpanError(ctx, "delete_permanent", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(ApiResponse.<Boolean>error("Failed to delete: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> restoreAll() {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.restoreAll");
        return repository.restoreAllProducts()
                .compose(count -> {
                    if (count == 0) {
                        return Future.failedFuture(new NotFoundException("No trashed products found"));
                    }
                    tracingMetrics.completeSpanSuccess(ctx, "restore_all", "Success");
                    return Future.succeededFuture(ApiResponse.success("All products restored", true));
                })
                .recover(err -> {
                    logger.error("Failed to restore all products", err);
                    tracingMetrics.completeSpanError(ctx, "restore_all", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future
                            .succeededFuture(ApiResponse.<Boolean>error("Failed to restore all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> deleteAllPermanent() {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("ProductService.deleteAllPermanent");
        return repository.deleteAllPermanentProducts()
                .compose(count -> {
                    if (count == 0) {
                        return Future.failedFuture(new NotFoundException("No trashed products found"));
                    }
                    tracingMetrics.completeSpanSuccess(ctx, "delete_all", "Success");
                    return Future.succeededFuture(ApiResponse.success("All products deleted permanently", true));
                })
                .recover(err -> {
                    logger.error("Failed to delete all products", err);
                    tracingMetrics.completeSpanError(ctx, "delete_all", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future
                            .succeededFuture(ApiResponse.<Boolean>error("Failed to delete all: " + err.getMessage()));
                });
    }

    private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedProducts(T req, String cachePrefix,
            int page, int pageSize, String keyword,
            Function<T, Future<PagedResult<Product>>> dbFetcher,
            Function<Product, R> responseMapper, TracingMetrics.TracingContext tracingContext,
            String spanName, String successMessage) {

        Span span = Span.fromContext(tracingContext.getContext());
        String cacheKey = String.format("%s:page:%d:size:%d:search:%s", cachePrefix, page, pageSize, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        span.setAttribute("cache.hit", true);
                        try {
                            JsonObject json = new JsonObject(cached);
                            int totalRecords = json.getInteger("totalRecords");
                            int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

                            List<R> data = json.getJsonArray("data").stream()
                                    .map(obj -> responseMapper.apply(Product.fromJson((JsonObject) obj)))
                                    .toList();

                            tracingMetrics.completeSpanSuccess(tracingContext, spanName, "Products fetched from cache");
                            return Future.succeededFuture(new ApiResponsePagination<>("success", successMessage, data,
                                    new PaginationMeta(page + 1, pageSize, totalPages, totalRecords)));
                        } catch (Exception e) {
                            logger.warn("Failed to parse cached paginated products: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("cache.hit", false);
                    return dbFetcher.apply(req)
                            .map(result -> {
                                JsonObject jsonToCache = new JsonObject()
                                        .put("totalRecords", result.getTotalRecords())
                                        .put("data", new JsonArray(
                                                result.getData().stream().map(Product::toJson).toList()));

                                redisService.set(cacheKey, jsonToCache.encode(), Duration.ofMinutes(10))
                                        .onFailure(err -> logger.warn("Failed to cache {}: {}", cachePrefix,
                                                err.getMessage()));

                                span.setAttribute("records.count", result.getData().size());
                                span.setAttribute("records.total", result.getTotalRecords());
                                tracingMetrics.completeSpanSuccess(tracingContext, spanName, successMessage);

                                return mapPagination(result, page, pageSize, responseMapper, successMessage);
                            });
                })
                .recover(throwable -> {
                    logger.error("Failed to fetch paginated products for {}", cachePrefix, throwable);
                    tracingMetrics.completeSpanError(tracingContext, spanName, throwable.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination.<List<R>>error("Failed to fetch data: " + throwable.getMessage()));
                });
    }

    private Future<ApiResponse<ProductResponse>> fetchProductFromDatabase(Long id,
            TracingMetrics.TracingContext tracingContext) {
        Span span = Span.fromContext(tracingContext.getContext());

        return repository.getProductById(id)
                .compose(data -> {
                    if (data == null)
                        return Future.failedFuture(new NotFoundException("Product not found"));

                    span.setAttribute("product.id", data.getProductId());

                    redisService.setJson("product:" + id, data.toJson(), Duration.ofMinutes(60))
                            .onFailure(err -> logger.warn("Failed to cache product {}: {}", id, err.getMessage()));

                    return Future.succeededFuture(
                            ApiResponse.success("Product fetched successfully", ProductResponse.from(data)));
                });
    }

    private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<Product> result, int page, int pageSize,
            Function<Product, R> mapper, String message) {
        int totalRecords = result.getTotalRecords();
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        List<R> data = result.getData().stream().map(mapper).toList();

        return new ApiResponsePagination<>("success", message, data,
                new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
    }

    private void invalidateCache(Long productId) {
        if (productId != null) {
            redisService.delete("product:" + productId)
                    .onSuccess(deleted -> {
                        if (deleted > 0)
                            logger.debug("Cache product:{} invalidated successfully", productId);
                    })
                    .onFailure(err -> logger.warn("Failed to invalidate cache for product {}: {}", productId,
                            err.getMessage()));
        }
    }
}