package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.Json;

public class ProductService {
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductService(
            ProductRepository repository,
            RedisService redisService,
            TracingMetrics tracingMetrics) {
        this.repository = repository;
        this.redisService = redisService;
        this.tracingMetrics = tracingMetrics;
    }

        public Future<ApiResponsePagination<List<ProductResponse>>> getAll(FindAllProductRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.getAll");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching all products | search={}, page={}, pageSize={}", keyword, page, pageSize);
        String cacheKey = String.format("products:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached, cacheKey,
                            () -> repository.getProducts(req),
                            new TypeReference<PagedResult<Product>>() {},
                            result -> mapToPagedResponse(result, req),
                            tracingContext, "get_all", Duration.ofMinutes(10)
                    );
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total", response.pagination().totalRecords());
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch products", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_all", err.getMessage());
                    return Future.succeededFuture(ApiResponsePagination.<List<ProductResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<ProductResponse>>> getActive(FindAllProductRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.getActive");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching active products | search={}, page={}, pageSize={}", keyword, page, pageSize);
        String cacheKey = String.format("products:active:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached, cacheKey,
                            () -> repository.getProductsActive(req),
                            new TypeReference<PagedResult<Product>>() {},
                            result -> mapToPagedResponse(result, req),
                            tracingContext, "get_active", Duration.ofMinutes(10)
                    );
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total", response.pagination().totalRecords());
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch active products", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_active", err.getMessage());
                    return Future.succeededFuture(ApiResponsePagination.<List<ProductResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<ProductResponseDeleteAt>>> getTrashed(FindAllProductRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.getTrashed");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching trashed products | search={}, page={}, pageSize={}", keyword, page, pageSize);
        String cacheKey = String.format("products:trashed:page:%d:search:%s", page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached, cacheKey,
                            () -> repository.getProductsTrashed(req),
                            new TypeReference<PagedResult<Product>>() {},
                            result -> mapToPagedResponseDeleteAt(result, req),
                            tracingContext, "get_trashed", Duration.ofMinutes(10)
                    );
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total", response.pagination().totalRecords());
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch trashed products", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_trashed", err.getMessage());
                    return Future.succeededFuture(ApiResponsePagination.<List<ProductResponseDeleteAt>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<ProductResponse>>> getByMerchant(FindAllProductByMerchantRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.getByMerchant");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("merchant.id", req.getMerchantId());

        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() != null ? req.getPageSize() : 10;

        String cacheKey = String.format("products:merchant:%d:search:%s:cat:%s:min:%s:max:%s:page:%d:size:%d",
                req.getMerchantId(), req.getSearch(), req.getCategoryId(), req.getMinPrice(), req.getMaxPrice(), page, pageSize);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached, cacheKey,
                            () -> repository.getProductsByMerchant(req),
                            new TypeReference<PagedResult<Product>>() {},
                            result -> mapToPagedResponse(result, page, pageSize),
                            tracingContext, "get_by_merchant", Duration.ofMinutes(10)
                    );
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total", response.pagination().totalRecords());
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch products by merchant", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_by_merchant", err.getMessage());
                    return Future.succeededFuture(ApiResponsePagination.<List<ProductResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<ProductResponse>>> getByCategoryName(FindAllProductByCategoryRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.getByCategoryName");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() != null ? req.getPageSize() : 10;

        String cacheKey = String.format("products:category:%s:search:%s:min:%s:max:%s:page:%d:size:%d",
                req.getCategoryName(), req.getSearch(), req.getMinPrice(), req.getMaxPrice(), page, pageSize);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached, cacheKey,
                            () -> repository.getProductsByCategoryName(req),
                            new TypeReference<PagedResult<Product>>() {},
                            result -> mapToPagedResponse(result, page, pageSize),
                            tracingContext, "get_by_category", Duration.ofMinutes(10)
                    );
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total", response.pagination().totalRecords());
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch products by category", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_by_category", err.getMessage());
                    return Future.succeededFuture(ApiResponsePagination.<List<ProductResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponse>> getById(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
                "ProductService.getById",
                io.opentelemetry.api.common.Attributes.builder().put("id", id).build());
        Span span = Span.fromContext(tracingContext.getContext());

        logger.info("Fetching product by id: {}", id);
        String cacheKey = "product:" + id;

        return redisService.get(cacheKey)
                .compose(cached -> {
                    span.setAttribute("cache.hit", cached != null);
                    return handleCacheOrRepo(
                            cached, cacheKey,
                            () -> repository.getProductById(id).map(data -> {
                                if (data == null) throw new NotFoundException("Product not found");
                                return data;
                            }),
                            new TypeReference<Product>() {},
                            data -> ApiResponse.success("Success", ProductResponse.from(data)),
                            tracingContext, "get_by_id", Duration.ofMinutes(60)
                    );
                })
                .recover(err -> {
                    logger.error("Failed to fetch by id", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_by_id", err.getMessage());
                    return Future.succeededFuture(ApiResponse.<ProductResponse>error(err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponse>> create(CreateProductRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.create");
        Span span = Span.fromContext(tracingContext.getContext());

        logger.info("Creating product: {}", req.getName());

        return repository.createProduct(req)
                .map(data -> {
                    span.setAttribute("id", data.getProductId());
                    tracingMetrics.completeSpanSuccess(tracingContext, "create", "Success");
                    return ApiResponse.success("Product created", ProductResponse.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to create product", err);
                    tracingMetrics.completeSpanError(tracingContext, "create", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponse>error("Failed to create: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponse>> update(UpdateProductRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.update");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", req.getProductId());

        return repository.updateProduct(req)
                .compose(data -> {
                    if (data == null) {
                        return Future.<Product>failedFuture(new NotFoundException("Product not found"));
                    }
                    return redisService.delete("product:" + req.getProductId()).map(data);
                })
                .map(data -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "update", "Success");
                    return ApiResponse.success("Product updated", ProductResponse.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to update product", err);
                    tracingMetrics.completeSpanError(tracingContext, "update", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponse>error("Failed to update: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponseDeleteAt>> trash(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.trash");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return repository.trashProduct(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.<Product>failedFuture(new NotFoundException("Product not found"));
                    }
                    return redisService.delete("product:" + id).map(data);
                })
                .map(data -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "trash", "Success");
                    return ApiResponse.success("Product trashed", ProductResponseDeleteAt.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to trash product", err);
                    tracingMetrics.completeSpanError(tracingContext, "trash", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponseDeleteAt>error("Failed to trash: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<ProductResponseDeleteAt>> restore(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.restore");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return repository.restoreProduct(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.<Product>failedFuture(new NotFoundException("Product not found"));
                    }
                    return redisService.delete("product:" + id).map(data);
                })
                .map(data -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "restore", "Success");
                    return ApiResponse.success("Product restored", ProductResponseDeleteAt.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to restore product", err);
                    tracingMetrics.completeSpanError(tracingContext, "restore", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<ProductResponseDeleteAt>error("Failed to restore: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> deletePermanent(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.deletePermanent");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return repository.deleteProductPermanently(id)
                .compose(v -> redisService.delete("product:" + id).map(v))
                .map(v -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "delete_permanent", "Success");
                    return ApiResponse.success("Product deleted permanently", true);
                })
                .recover(err -> {
                    logger.error("Failed to delete product", err);
                    tracingMetrics.completeSpanError(tracingContext, "delete_permanent", err.getMessage());
                    return Future.succeededFuture(ApiResponse.<Boolean>error("Failed to delete: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> restoreAll() {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.restoreAll");
        return repository.restoreAllProducts()
                .map(v -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "restore_all", "Success");
                    return ApiResponse.success("All products restored", true);
                })
                .recover(err -> {
                    logger.error("Failed to restore all products", err);
                    tracingMetrics.completeSpanError(tracingContext, "restore_all", err.getMessage());
                    return Future
                            .succeededFuture(ApiResponse.<Boolean>error("Failed to restore all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> deleteAllPermanent() {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("ProductService.deleteAllPermanent");
        return repository.deleteAllPermanentProducts()
                .map(v -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "delete_all", "Success");
                    return ApiResponse.success("All products deleted permanently", true);
                })
                .recover(err -> {
                    logger.error("Failed to delete all products", err);
                    tracingMetrics.completeSpanError(tracingContext, "delete_all", err.getMessage());
                    return Future
                            .succeededFuture(ApiResponse.<Boolean>error("Failed to delete all: " + err.getMessage()));
                });
    }

    private <T, R> Future<R> handleCacheOrRepo(String cached, String cacheKey,
            java.util.concurrent.Callable<Future<T>> repoCall, TypeReference<T> typeRef,
            java.util.function.Function<T, R> mapper,
            TracingMetrics.TracingContext tracingCtx, String operation, Duration ttl) {

        if (cached != null) {
            try {
                T data = objectMapper.readValue(cached, typeRef);
                tracingMetrics.completeSpanSuccess(tracingCtx, operation, "Success");
                return Future.succeededFuture(mapper.apply(data));
            } catch (Exception e) {
                logger.warn("Cache parse error", e);
            }
        }

        try {
            return repoCall.call().map(res -> {
                redisService.set(cacheKey, Json.encode(res), ttl);
                tracingMetrics.completeSpanSuccess(tracingCtx, operation, "Success");
                return mapper.apply(res);
            });
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    private ApiResponsePagination<List<ProductResponse>> mapToPagedResponse(PagedResult<Product> result,
            FindAllProductRequest req) {
        return mapToPagedResponse(result, req.getPage(), req.getPageSize());
    }

    private ApiResponsePagination<List<ProductResponse>> mapToPagedResponse(PagedResult<Product> result, int page,
            int pageSize) {
        List<ProductResponse> data = result.getData().stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        return new ApiResponsePagination<>(
                "success", "Data fetched", data,
                new PaginationMeta(page, pageSize,
                        (int) Math.ceil((double) result.getTotalRecords() / pageSize),
                        result.getTotalRecords()));
    }

    private ApiResponsePagination<List<ProductResponseDeleteAt>> mapToPagedResponseDeleteAt(PagedResult<Product> result,
            FindAllProductRequest req) {
        List<ProductResponseDeleteAt> data = result.getData().stream()
                .map(ProductResponseDeleteAt::from)
                .collect(Collectors.toList());
        return new ApiResponsePagination<>(
                "success", "Data fetched", data,
                new PaginationMeta(req.getPage(), req.getPageSize(),
                        (int) Math.ceil((double) result.getTotalRecords() / req.getPageSize()),
                        result.getTotalRecords()));
    }
}