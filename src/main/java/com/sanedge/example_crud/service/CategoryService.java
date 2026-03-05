package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.example_crud.domain.requests.category.CreateCategoryRequest;
import com.sanedge.example_crud.domain.requests.category.FindAllCategory;
import com.sanedge.example_crud.domain.requests.category.MonthPriceMerchant;
import com.sanedge.example_crud.domain.requests.category.MonthTotalPrice;
import com.sanedge.example_crud.domain.requests.category.MonthTotalPriceCategory;
import com.sanedge.example_crud.domain.requests.category.MonthTotalPriceMerchant;
import com.sanedge.example_crud.domain.requests.category.UpdateCategoryRequest;
import com.sanedge.example_crud.domain.requests.category.YearPriceId;
import com.sanedge.example_crud.domain.requests.category.YearPriceMerchant;
import com.sanedge.example_crud.domain.requests.category.YearTotalPriceCategory;
import com.sanedge.example_crud.domain.requests.category.YearTotalPriceMerchant;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.model.category.Category;
import com.sanedge.example_crud.model.category.CategoryMonthPrice;
import com.sanedge.example_crud.model.category.CategoryMonthTotalPrice;
import com.sanedge.example_crud.model.category.CategoryYearPrice;
import com.sanedge.example_crud.model.category.CategoryYearTotalPrice;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.CategoryRepository;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Future<ApiResponse<PagedResult<Category>>> getCategories(FindAllCategory req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getCategories");
        String cacheKey = String.format("categories:list:%s:%d:%d", req.getSearch(), req.getPage(), req.getPageSize());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getCategories(req),
                        new TypeReference<PagedResult<Category>>() {
                        },
                        tracingCtx, "get_categories"))
                .recover(err -> handleError(tracingCtx, "get_categories", err));
    }

    public Future<ApiResponse<PagedResult<Category>>> getCategoriesActive(FindAllCategory req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getCategoriesActive");
        String cacheKey = String.format("categories:active:%s:%d:%d", req.getSearch(), req.getPage(),
                req.getPageSize());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getCategoriesActive(req),
                        new TypeReference<PagedResult<Category>>() {
                        },
                        tracingCtx, "get_categories_active"))
                .recover(err -> handleError(tracingCtx, "get_categories_active", err));
    }

    public Future<ApiResponse<PagedResult<Category>>> getTrashedCategories(FindAllCategory req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getTrashedCategories");
        String cacheKey = String.format("categories:trashed:%s:%d:%d", req.getSearch(), req.getPage(),
                req.getPageSize());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getCategoriesTrashed(req),
                        new TypeReference<PagedResult<Category>>() {
                        },
                        tracingCtx, "get_trashed_categories"))
                .recover(err -> handleError(tracingCtx, "get_trashed_categories", err));
    }

    public Future<ApiResponse<Category>> getCategoryById(Long categoryId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getCategoryById");
        String cacheKey = String.format("category:detail:%d", categoryId);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getCategoryById(categoryId)
                                .map(res -> {
                                    if (res == null) {
                                        throw new CustomException("Category not found");
                                    }
                                    return res;
                                }),
                        new TypeReference<Category>() {
                        },
                        tracingCtx, "get_category_by_id"))
                .recover(err -> handleError(tracingCtx, "get_category_by_id", err));
    }

    public Future<ApiResponse<Category>> createCategory(CreateCategoryRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.createCategory");
        String slug = generateSlug(req.getName());

        req.setSlugCategory(slug);

        return categoryRepository.getCategoryByName(req.getName())
                .compose(existing -> {
                    if (existing != null) {
                        return Future.failedFuture(new CustomException("Category name already exists"));
                    }
                    return categoryRepository.createCategory(req);
                })
                .map(cat -> {
                    invalidateCache(cat.getCategoryId());
                    tracingMetrics.completeSpanSuccess(tracingCtx, "create_category", "Created");
                    return ApiResponse.success("Category created successfully", cat);
                })
                .recover(err -> handleError(tracingCtx, "create_category", err));
    }

    public Future<ApiResponse<Category>> updateCategory(UpdateCategoryRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.updateCategory");
        String slug = generateSlug(req.getName());

        req.setSlugCategory(slug);

        return categoryRepository.getCategoryById(req.getCategoryId().longValue())
                .compose(existing -> {
                    if (existing == null) {
                        return Future.failedFuture(new CustomException("Category not found"));
                    }
                    return categoryRepository.getCategoryByName(req.getName())
                            .compose(checkName -> {
                                if (checkName != null && !checkName.getName().equals(req.getName())) {
                                    return Future.failedFuture(
                                            new CustomException("Category name already used by another category"));
                                }
                                return categoryRepository.updateCategory(req);
                            });
                })
                .map(cat -> {
                    invalidateCache(req.getCategoryId().longValue());
                    tracingMetrics.completeSpanSuccess(tracingCtx, "update_category", "Updated");
                    return ApiResponse.success("Category updated successfully", cat);
                })
                .recover(err -> handleError(tracingCtx, "update_category", err));
    }

    public Future<ApiResponse<Category>> trashCategory(Long categoryId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.trashCategory");

        return categoryRepository.trashCategory(categoryId)
                .map(cat -> {
                    if (cat == null)
                        throw new CustomException("Category not found or already trashed");
                    invalidateCache(categoryId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "trash_category", "Trashed");
                    return ApiResponse.success("Category moved to trash", cat);
                })
                .recover(err -> handleError(tracingCtx, "trash_category", err));
    }

    public Future<ApiResponse<Category>> restoreCategory(Long categoryId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.restoreCategory");

        return categoryRepository.restoreCategory(categoryId)
                .map(cat -> {
                    if (cat == null)
                        throw new CustomException("Category not found or not in trash");
                    invalidateCache(categoryId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "restore_category", "Restored");
                    return ApiResponse.success("Category restored successfully", cat);
                })
                .recover(err -> handleError(tracingCtx, "restore_category", err));
    }

    public Future<ApiResponse<Void>> deleteCategoryPermanently(Long categoryId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan(
                "CategoryService.deleteCategoryPermanently",
                io.opentelemetry.api.common.Attributes.builder()
                        .put("category.id", categoryId)
                        .build());

        log.info("Permanently deleting category: {}", categoryId);

        return categoryRepository.deleteCategoryPermanently(categoryId)
                .map(v -> {
                    log.info("Category deleted successfully: {}", categoryId);

                    invalidateCache(categoryId);

                    tracingMetrics.completeSpanSuccess(tracingCtx, "delete_permanent", "Category deleted permanently");
                    return ApiResponse.<Void>success("Category deleted permanently", null);
                })
                .recover(throwable -> {
                    log.error("Failed to deletePermanent category: {}", categoryId, throwable);
                    tracingMetrics.completeSpanError(tracingCtx, "delete_permanent", throwable.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<Void>error("Failed to delete category: " + throwable.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> restoreAllCategories(RoutingContext ctx) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.restoreAllCategories");

        return categoryRepository.restoreAllCategories()
                .map(count -> {
                    tracingMetrics.completeSpanSuccess(tracingCtx, "restore_all", "Success");
                    log.info("Restored {} categories", count);
                    return ApiResponse.success("All categories restored", count);
                })
                .recover(err -> {
                    log.error("Failed to restore all categories", err);
                    tracingMetrics.completeSpanError(tracingCtx, "restore_all", err.getMessage());
                    return Future
                            .succeededFuture(ApiResponse.<Integer>error("Failed to restore all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> deleteAllPermanentCategories(RoutingContext ctx) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CategoryService.deleteAllPermanentCategories");

        return categoryRepository.deleteAllPermanentCategories()
                .map(count -> {
                    tracingMetrics.completeSpanSuccess(tracingCtx, "delete_all", "Success");
                    log.info("Permanently deleted {} categories", count);
                    return ApiResponse.success("All categories deleted permanently", count);
                })
                .recover(err -> {
                    log.error("Failed to delete all categories permanently", err);
                    tracingMetrics.completeSpanError(tracingCtx, "delete_all", err.getMessage());
                    return Future
                            .succeededFuture(ApiResponse.<Integer>error("Failed to delete all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<List<CategoryMonthTotalPrice>>> getMonthlyTotalPrice(MonthTotalPrice req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getMonthlyTotalPrice");
        String cacheKey = String.format("category:report:monthly_total:%d:%d", req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getMonthlyTotalPrice(req),
                        new TypeReference<List<CategoryMonthTotalPrice>>() {
                        },
                        tracingCtx, "report_monthly_total"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    public Future<ApiResponse<List<CategoryYearTotalPrice>>> getYearlyTotalPrice(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getYearlyTotalPrice");
        String cacheKey = String.format("category:report:yearly_total:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getYearlyTotalPrice(year),
                        new TypeReference<List<CategoryYearTotalPrice>>() {
                        },
                        tracingCtx, "report_yearly_total"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CategoryMonthTotalPrice>>> getMonthlyTotalPriceByMerchant(
            MonthTotalPriceMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CategoryService.getMonthlyTotalPriceByMerchant");
        String cacheKey = String.format("category:report:monthly_total_merchant:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getMonthlyTotalPriceByMerchant(req),
                        new TypeReference<List<CategoryMonthTotalPrice>>() {
                        },
                        tracingCtx, "report_monthly_total_merchant"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    public Future<ApiResponse<List<CategoryYearTotalPrice>>> getYearlyTotalPriceByMerchant(YearTotalPriceMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CategoryService.getYearlyTotalPriceByMerchant");
        String cacheKey = String.format("category:report:yearly_total_merchant:%d:%d", req.getMerchantId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getYearlyTotalPriceByMerchant(req),
                        new TypeReference<List<CategoryYearTotalPrice>>() {
                        },
                        tracingCtx, "report_yearly_total_merchant"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CategoryMonthTotalPrice>>> getMonthlyTotalPriceById(MonthTotalPriceCategory req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getMonthlyTotalPriceById");
        String cacheKey = String.format("category:report:monthly_total_id:%d:%d:%d", req.getCategoryId(), req.getYear(),
                req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getMonthlyTotalPriceById(req),
                        new TypeReference<List<CategoryMonthTotalPrice>>() {
                        },
                        tracingCtx, "report_monthly_total_id"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    public Future<ApiResponse<List<CategoryYearTotalPrice>>> getYearlyTotalPriceById(YearTotalPriceCategory req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getYearlyTotalPriceById");
        String cacheKey = String.format("category:report:yearly_total_id:%d:%d", req.getCategoryId(), req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getYearlyTotalPriceById(req),
                        new TypeReference<List<CategoryYearTotalPrice>>() {
                        },
                        tracingCtx, "report_yearly_total_id"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    public Future<ApiResponse<List<CategoryMonthPrice>>> getMonthlyCategory(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getMonthlyCategory");
        String cacheKey = String.format("category:report:monthly_category:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getMonthlyCategory(year),
                        new TypeReference<List<CategoryMonthPrice>>() {
                        },
                        tracingCtx, "report_monthly_category"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    public Future<ApiResponse<List<CategoryYearPrice>>> getYearlyCategory(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getYearlyCategory");
        String cacheKey = String.format("category:report:yearly_category:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getYearlyCategory(year),
                        new TypeReference<List<CategoryYearPrice>>() {
                        },
                        tracingCtx, "report_yearly_category"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    public Future<ApiResponse<List<CategoryMonthPrice>>> getMonthlyCategoryByMerchant(MonthPriceMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CategoryService.getMonthlyCategoryByMerchant");
        String cacheKey = String.format("category:report:monthly_category_merchant:%d:%d", req.getMerchantId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getMonthlyCategoryByMerchant(req),
                        new TypeReference<List<CategoryMonthPrice>>() {
                        },
                        tracingCtx, "report_monthly_category_merchant"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    public Future<ApiResponse<List<CategoryYearPrice>>> getYearlyCategoryByMerchant(YearPriceMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CategoryService.getYearlyCategoryByMerchant");
        String cacheKey = String.format("category:report:yearly_category_merchant:%d:%d", req.getMerchantId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getYearlyCategoryByMerchant(req),
                        new TypeReference<List<CategoryYearPrice>>() {
                        },
                        tracingCtx, "report_yearly_category_merchant"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CategoryMonthPrice>>> getMonthlyCategoryById(YearPriceId req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getMonthlyCategoryById");
        String cacheKey = String.format("category:report:monthly_category_id:%d:%d", req.getCategoryId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getMonthlyCategoryById(req),
                        new TypeReference<List<CategoryMonthPrice>>() {
                        },
                        tracingCtx, "report_monthly_category_id"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    public Future<ApiResponse<List<CategoryYearPrice>>> getYearlyCategoryById(YearPriceId req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CategoryService.getYearlyCategoryById");
        String cacheKey = String.format("category:report:yearly_category_id:%d:%d", req.getCategoryId(), req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(
                        cached, cacheKey,
                        () -> categoryRepository.getYearlyCategoryById(req),
                        new TypeReference<List<CategoryYearPrice>>() {
                        },
                        tracingCtx, "report_yearly_category_id"))
                .recover(err -> handleReportError(tracingCtx,  err));
    }

    private void invalidateCache(Long categoryId) {
        if (categoryId != null) {
            redisService.delete("category:detail:" + categoryId);
        }
        redisService.delete("categories:list:");
    }

    private <T> Future<ApiResponse<T>> handleCacheOrRepo(String cached, String cacheKey,
            java.util.concurrent.Callable<Future<T>> repoCall, TypeReference<T> typeRef,
            TracingMetrics.TracingContext tracingCtx, String operation) {
        if (cached != null) {
            try {
                T data = objectMapper.readValue(cached, typeRef);
                return Future.succeededFuture(ApiResponse.success("Success", data));
            } catch (Exception e) {
                log.warn("Cache parse error", e);
            }
        }
        try {
            return repoCall.call().map(res -> {
                redisService.set(cacheKey, Json.encode(res), Duration.ofMinutes(30));
                tracingMetrics.completeSpanSuccess(tracingCtx, operation, "Success");
                return ApiResponse.success("Success", res);
            });
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    private <T> Future<ApiResponse<T>> handleReportError(TracingMetrics.TracingContext ctx, Throwable err) {
        log.error("Error in cashier report: {}", err.getMessage(), err);
        tracingMetrics.completeSpanError(ctx, "report", err.getMessage());
        return Future.succeededFuture(ApiResponse.<T>error("Failed to generate report: " + err.getMessage()));
    }

    private String generateSlug(String name) {
        if (name == null)
            return "";
        return name.toLowerCase().trim().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-");
    }

    private <T> Future<T> handleError(TracingMetrics.TracingContext ctx, String operation, Throwable err) {
        log.error("Error in {}: {}", operation, err.getMessage(), err);
        tracingMetrics.completeSpanError(ctx, operation, err.getMessage());
        return Future.failedFuture(err);
    }
}