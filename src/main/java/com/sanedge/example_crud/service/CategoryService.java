package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.model.category.Category;
import com.sanedge.example_crud.model.category.CategoryMonthPrice;
import com.sanedge.example_crud.model.category.CategoryMonthTotalPrice;
import com.sanedge.example_crud.model.category.CategoryYearPrice;
import com.sanedge.example_crud.model.category.CategoryYearTotalPrice;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.CategoryRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CategoryService {

        private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);

        private final CategoryRepository categoryRepository;
        private final RedisService redisService;
        private final TracingMetrics tracingMetrics;

        public Future<ApiResponsePagination<List<Category>>> getCategories(FindAllCategory req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.getCategories");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setSearch(keyword);
                req.setPage(page);
                req.setPageSize(pageSize);

                return fetchPaginatedCategories(req, "categories:all", page, pageSize, keyword,
                                categoryRepository::getCategories, Function.identity(), ctx, "get_categories",
                                "Categories fetched successfully");
        }

        public Future<ApiResponsePagination<List<Category>>> getCategoriesActive(FindAllCategory req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.getCategoriesActive");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setSearch(keyword);
                req.setPage(page);
                req.setPageSize(pageSize);

                return fetchPaginatedCategories(req, "categories:active", page, pageSize, keyword,
                                categoryRepository::getCategoriesActive, Function.identity(), ctx,
                                "get_categories_active",
                                "Active categories fetched successfully");
        }

        public Future<ApiResponsePagination<List<Category>>> getTrashedCategories(FindAllCategory req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.getTrashedCategories");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setSearch(keyword);
                req.setPage(page);
                req.setPageSize(pageSize);

                return fetchPaginatedCategories(req, "categories:trashed", page, pageSize, keyword,
                                categoryRepository::getCategoriesTrashed, Function.identity(), ctx,
                                "get_trashed_categories",
                                "Trashed categories fetched successfully");
        }

        public Future<ApiResponse<Category>> getCategoryById(Long categoryId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.getCategoryById",
                                Attributes.builder().put("category.id", categoryId).build());
                Span span = Span.fromContext(ctx.getContext());

                String cacheKey = "category:detail:" + categoryId;

                return redisService.get(cacheKey)
                                .compose(cached -> {
                                        if (cached != null && !cached.isEmpty()) {
                                                span.setAttribute("cache.hit", true);
                                                try {
                                                        Category category = Category.fromJson(new JsonObject(cached));
                                                        tracingMetrics.completeSpanSuccess(ctx, "get_by_id",
                                                                        "Category fetched from cache");
                                                        return Future.succeededFuture(
                                                                        ApiResponse.success(
                                                                                        "Category fetched successfully (from cache)",
                                                                                        category));
                                                } catch (Exception e) {
                                                        logger.warn("Failed to parse cached category data for category {}: {}",
                                                                        categoryId,
                                                                        e.getMessage());
                                                        return fetchCategoryFromDatabase(categoryId, ctx);
                                                }
                                        }
                                        span.setAttribute("cache.hit", false);
                                        return fetchCategoryFromDatabase(categoryId, ctx);
                                })
                                .recover(err -> {
                                        logger.error("Failed to fetch category by id: {}", categoryId, err);
                                        tracingMetrics.completeSpanError(ctx, "get_by_id", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Category>error("Failed to fetch category: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Category>> createCategory(CreateCategoryRequest req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.createCategory",
                                Attributes.builder().put("category.name", req.getName()).build());

                req.setSlugCategory(generateSlug(req.getName()));

                return categoryRepository.getCategoryByName(req.getName())
                                .compose(existing -> {
                                        if (existing != null) {
                                                return Future.failedFuture(
                                                                new CustomException("Category name already exists"));
                                        }
                                        return categoryRepository.createCategory(req);
                                })
                                .map(cat -> {
                                        invalidateCache(cat.getCategoryId());
                                        tracingMetrics.completeSpanSuccess(ctx, "create_category", "Created");
                                        return ApiResponse.success("Category created successfully", cat);
                                })
                                .recover(err -> {
                                        logger.error("Failed to create category", err);
                                        tracingMetrics.completeSpanError(ctx, "create_category", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Category>error("Failed to create category: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Category>> updateCategory(UpdateCategoryRequest req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.updateCategory",
                                Attributes.builder().put("category.id", req.getCategoryId()).build());

                req.setSlugCategory(generateSlug(req.getName()));

                return categoryRepository.getCategoryById(req.getCategoryId().longValue())
                                .compose(existing -> {
                                        if (existing == null) {
                                                return Future.failedFuture(new CustomException("Category not found"));
                                        }
                                        return categoryRepository.getCategoryByName(req.getName())
                                                        .compose(checkName -> {
                                                                if (checkName != null && !checkName.getName()
                                                                                .equals(existing.getName())) {
                                                                        return Future.failedFuture(
                                                                                        new CustomException(
                                                                                                        "Category name already used by another category"));
                                                                }
                                                                return categoryRepository.updateCategory(req);
                                                        });
                                })
                                .map(cat -> {
                                        invalidateCache(req.getCategoryId().longValue());
                                        tracingMetrics.completeSpanSuccess(ctx, "update_category", "Updated");
                                        return ApiResponse.success("Category updated successfully", cat);
                                })
                                .recover(err -> {
                                        logger.error("Failed to update category: {}", req.getCategoryId(), err);
                                        tracingMetrics.completeSpanError(ctx, "update_category", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Category>error("Failed to update category: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Category>> trashCategory(Long categoryId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.trashCategory",
                                Attributes.builder().put("category.id", categoryId).build());

                return categoryRepository.trashCategory(categoryId)
                                .compose(cat -> {
                                        if (cat == null)
                                                return Future.failedFuture(new CustomException(
                                                                "Category not found or already trashed"));
                                        invalidateCache(categoryId);
                                        tracingMetrics.completeSpanSuccess(ctx, "trash_category", "Trashed");
                                        return Future.succeededFuture(
                                                        ApiResponse.success("Category moved to trash", cat));
                                })
                                .recover(err -> {
                                        logger.error("Failed to trash category: {}", categoryId, err);
                                        tracingMetrics.completeSpanError(ctx, "trash_category", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Category>error("Failed to trash category: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Category>> restoreCategory(Long categoryId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.restoreCategory",
                                Attributes.builder().put("category.id", categoryId).build());

                return categoryRepository.findByTrashed(categoryId)
                                .compose(cat -> {
                                        if (cat == null) {
                                                return Future.failedFuture(new CustomException(
                                                                "Category not found or not in trash"));
                                        }
                                        return categoryRepository.restoreCategory(categoryId);
                                })
                                .compose(cat -> {
                                        invalidateCache(categoryId);
                                        tracingMetrics.completeSpanSuccess(ctx, "restore_category", "Restored");
                                        return Future.succeededFuture(
                                                        ApiResponse.success("Category restored successfully", cat));
                                })
                                .recover(err -> {
                                        logger.error("Failed to restore category: {}", categoryId, err);
                                        tracingMetrics.completeSpanError(ctx, "restore_category", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Category>error("Failed to restore category: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Void>> deleteCategoryPermanently(Long categoryId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan(
                                "CategoryService.deleteCategoryPermanently",
                                Attributes.builder().put("category.id", categoryId).build());

                logger.info("Permanently deleting category: {}", categoryId);

                return categoryRepository.findByTrashed(categoryId)
                                .compose(cat -> {
                                        if (cat == null) {
                                                return Future.failedFuture(new CustomException(
                                                                "Category not found or not in trash"));
                                        }
                                        return categoryRepository.deleteCategoryPermanently(categoryId);
                                })
                                .map(v -> {
                                        logger.info("Category deleted successfully: {}", categoryId);
                                        invalidateCache(categoryId);
                                        tracingMetrics.completeSpanSuccess(ctx, "delete_permanent",
                                                        "Category deleted permanently");
                                        return ApiResponse.<Void>success("Category deleted permanently", null);
                                })
                                .recover(err -> {
                                        logger.error("Failed to deletePermanent category: {}", categoryId, err);
                                        tracingMetrics.completeSpanError(ctx, "delete_permanent", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Void>error("Failed to delete category: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Integer>> restoreAllCategories() {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CategoryService.restoreAllCategories");
                return categoryRepository.restoreAllCategories()
                                .compose(count -> {
                                        if (count == 0) {
                                                return Future.failedFuture(new CustomException("No trashed categories found"));
                                        }
                                        tracingMetrics.completeSpanSuccess(ctx, "restore_all", "Success");
                                        logger.info("Restored {} categories", count);
                                        return Future.succeededFuture(ApiResponse.success("All categories restored", count));
                                })
                                .recover(err -> {
                                        logger.error("Failed to restore all categories", err);
                                        tracingMetrics.completeSpanError(ctx, "restore_all", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                         ApiResponse.<Integer>error(
                                                                         "Failed to restore all: " + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Integer>> deleteAllPermanentCategories() {
                TracingMetrics.TracingContext ctx = tracingMetrics
                                .startSpan("CategoryService.deleteAllPermanentCategories");
                return categoryRepository.deleteAllPermanentCategories()
                                .compose(count -> {
                                        if (count == 0) {
                                                return Future.failedFuture(new CustomException("No trashed categories found"));
                                        }
                                        tracingMetrics.completeSpanSuccess(ctx, "delete_all", "Success");
                                        logger.info("Permanently deleted {} categories", count);
                                        return Future.succeededFuture(ApiResponse.success("All categories deleted permanently", count));
                                })
                                .recover(err -> {
                                        logger.error("Failed to delete all categories permanently", err);
                                        tracingMetrics.completeSpanError(ctx, "delete_all", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                         ApiResponse.<Integer>error(
                                                                         "Failed to delete all: " + err.getMessage()));
                                });
        }

        public Future<ApiResponse<List<CategoryMonthTotalPrice>>> getMonthlyTotalPrice(MonthTotalPrice req) {
                String cacheKey = String.format("category:report:monthly_total:%d:%d", req.getYear(), req.getMonth());
                return fetchStats(cacheKey, categoryRepository.getMonthlyTotalPrice(req), Function.identity(),
                                CategoryMonthTotalPrice.class, "report_monthly_total",
                                "Monthly total price fetched successfully");
        }

        public Future<ApiResponse<List<CategoryYearTotalPrice>>> getYearlyTotalPrice(int year) {
                String cacheKey = String.format("category:report:yearly_total:%d", year);
                return fetchStats(cacheKey, categoryRepository.getYearlyTotalPrice(year), Function.identity(),
                                CategoryYearTotalPrice.class, "report_yearly_total",
                                "Yearly total price fetched successfully");
        }

        public Future<ApiResponse<List<CategoryMonthTotalPrice>>> getMonthlyTotalPriceByMerchant(
                        MonthTotalPriceMerchant req) {
                String cacheKey = String.format("category:report:monthly_total_merchant:%d:%d:%d", req.getMerchantId(),
                                req.getYear(), req.getMonth());
                return fetchStats(cacheKey, categoryRepository.getMonthlyTotalPriceByMerchant(req), Function.identity(),
                                CategoryMonthTotalPrice.class, "report_monthly_total_merchant",
                                "Monthly total price by merchant fetched successfully");
        }

        public Future<ApiResponse<List<CategoryYearTotalPrice>>> getYearlyTotalPriceByMerchant(
                        YearTotalPriceMerchant req) {
                String cacheKey = String.format("category:report:yearly_total_merchant:%d:%d", req.getMerchantId(),
                                req.getYear());
                return fetchStats(cacheKey, categoryRepository.getYearlyTotalPriceByMerchant(req), Function.identity(),
                                CategoryYearTotalPrice.class, "report_yearly_total_merchant",
                                "Yearly total price by merchant fetched successfully");
        }

        public Future<ApiResponse<List<CategoryMonthTotalPrice>>> getMonthlyTotalPriceById(
                        MonthTotalPriceCategory req) {
                String cacheKey = String.format("category:report:monthly_total_id:%d:%d:%d", req.getCategoryId(),
                                req.getYear(), req.getMonth());
                return fetchStats(cacheKey, categoryRepository.getMonthlyTotalPriceById(req), Function.identity(),
                                CategoryMonthTotalPrice.class, "report_monthly_total_id",
                                "Monthly total price by ID fetched successfully");
        }

        public Future<ApiResponse<List<CategoryYearTotalPrice>>> getYearlyTotalPriceById(YearTotalPriceCategory req) {
                String cacheKey = String.format("category:report:yearly_total_id:%d:%d", req.getCategoryId(),
                                req.getYear());
                return fetchStats(cacheKey, categoryRepository.getYearlyTotalPriceById(req), Function.identity(),
                                CategoryYearTotalPrice.class, "report_yearly_total_id",
                                "Yearly total price by ID fetched successfully");
        }

        public Future<ApiResponse<List<CategoryMonthPrice>>> getMonthlyCategory(int year) {
                String cacheKey = String.format("category:report:monthly_category:%d", year);
                return fetchStats(cacheKey, categoryRepository.getMonthlyCategory(year), Function.identity(),
                                CategoryMonthPrice.class, "report_monthly_category",
                                "Monthly category fetched successfully");
        }

        public Future<ApiResponse<List<CategoryYearPrice>>> getYearlyCategory(int year) {
                String cacheKey = String.format("category:report:yearly_category:%d", year);
                return fetchStats(cacheKey, categoryRepository.getYearlyCategory(year), Function.identity(),
                                CategoryYearPrice.class, "report_yearly_category",
                                "Yearly category fetched successfully");
        }

        public Future<ApiResponse<List<CategoryMonthPrice>>> getMonthlyCategoryByMerchant(MonthPriceMerchant req) {
                String cacheKey = String.format("category:report:monthly_category_merchant:%d:%d", req.getMerchantId(),
                                req.getYear());
                return fetchStats(cacheKey, categoryRepository.getMonthlyCategoryByMerchant(req), Function.identity(),
                                CategoryMonthPrice.class, "report_monthly_category_merchant",
                                "Monthly category by merchant fetched successfully");
        }

        public Future<ApiResponse<List<CategoryYearPrice>>> getYearlyCategoryByMerchant(YearPriceMerchant req) {
                String cacheKey = String.format("category:report:yearly_category_merchant:%d:%d", req.getMerchantId(),
                                req.getYear());
                return fetchStats(cacheKey, categoryRepository.getYearlyCategoryByMerchant(req), Function.identity(),
                                CategoryYearPrice.class, "report_yearly_category_merchant",
                                "Yearly category by merchant fetched successfully");
        }

        public Future<ApiResponse<List<CategoryMonthPrice>>> getMonthlyCategoryById(YearPriceId req) {
                String cacheKey = String.format("category:report:monthly_category_id:%d:%d", req.getCategoryId(),
                                req.getYear());
                return fetchStats(cacheKey, categoryRepository.getMonthlyCategoryById(req), Function.identity(),
                                CategoryMonthPrice.class, "report_monthly_category_id",
                                "Monthly category by ID fetched successfully");
        }

        public Future<ApiResponse<List<CategoryYearPrice>>> getYearlyCategoryById(YearPriceId req) {
                String cacheKey = String.format("category:report:yearly_category_id:%d:%d", req.getCategoryId(),
                                req.getYear());
                return fetchStats(cacheKey, categoryRepository.getYearlyCategoryById(req), Function.identity(),
                                CategoryYearPrice.class, "report_yearly_category_id",
                                "Yearly category by ID fetched successfully");
        }

        private <T, R> Future<ApiResponse<List<R>>> fetchStats(String cacheKey, Future<List<T>> dbFuture,
                        Function<T, R> mapper, Class<R> responseType, String spanName, String successMessage) {

                TracingMetrics.TracingContext tracingContext = tracingMetrics
                                .startSpan("CategoryStatsService." + spanName);

                return redisService.getJsonList(cacheKey, responseType)
                                .compose(cached -> {
                                        if (cached != null && !cached.isEmpty()) {
                                                tracingMetrics.completeSpanSuccess(tracingContext, spanName,
                                                                "Data from cache");
                                                return Future.succeededFuture(cached);
                                        }
                                        return dbFuture.map(dbResults -> {
                                                List<R> responseList = dbResults.stream().map(mapper)
                                                                .collect(Collectors.toList());
                                                redisService.setJsonList(cacheKey, responseList, Duration.ofHours(6))
                                                                .onFailure(err -> tracingMetrics.completeSpanError(
                                                                                tracingContext, spanName,
                                                                                "Data fetched but cache failed"))
                                                                .onSuccess(v -> tracingMetrics.completeSpanSuccess(
                                                                                tracingContext, spanName,
                                                                                "Data fetched from DB and cached"));
                                                return responseList;
                                        });
                                })
                                .map(results -> ApiResponse.success(successMessage, results))
                                .recover(err -> Future.succeededFuture(
                                                ApiResponse.error("Failed to fetch stats: " + err.getMessage())));
        }

        private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedCategories(T req, String cachePrefix,
                        int page, int pageSize, String keyword, Function<T, Future<PagedResult<Category>>> dbFetcher,
                        Function<Category, R> responseMapper, TracingMetrics.TracingContext tracingContext,
                        String spanName,
                        String successMessage) {

                Span span = Span.fromContext(tracingContext.getContext());
                String cacheKey = String.format("%s:page:%d:size:%d:search:%s", cachePrefix, page, pageSize, keyword);

                return redisService.get(cacheKey)
                                .compose(cached -> {
                                        if (cached != null && !cached.isEmpty()) {
                                                span.setAttribute("cache.hit", true);
                                                try {
                                                        JsonObject json = new JsonObject(cached);
                                                        int totalRecords = json.getInteger("totalRecords");
                                                        int totalPages = (int) Math
                                                                        .ceil((double) totalRecords / pageSize);

                                                        List<R> data = json.getJsonArray("data").stream()
                                                                        .map(obj -> responseMapper.apply(Category
                                                                                        .fromJson((JsonObject) obj)))
                                                                        .toList();

                                                        tracingMetrics.completeSpanSuccess(tracingContext, spanName,
                                                                        "Categories fetched from cache");
                                                        return Future.succeededFuture(new ApiResponsePagination<>(
                                                                        "success", successMessage, data,
                                                                        new PaginationMeta(page + 1, pageSize,
                                                                                        totalPages, totalRecords)));
                                                } catch (Exception e) {
                                                        logger.warn("Failed to parse cached paginated categories: {}",
                                                                        e.getMessage());
                                                }
                                        }

                                        span.setAttribute("cache.hit", false);
                                        return dbFetcher.apply(req)
                                                        .map(result -> {
                                                                JsonObject jsonToCache = new JsonObject()
                                                                                .put("totalRecords", result
                                                                                                .getTotalRecords())
                                                                                .put("data", new JsonArray(
                                                                                                result.getData().stream()
                                                                                                                .map(Category::toJson)
                                                                                                                .toList()));

                                                                redisService.set(cacheKey, jsonToCache.encode(),
                                                                                Duration.ofMinutes(5))
                                                                                .onFailure(err -> logger.warn(
                                                                                                "Failed to cache {}: {}",
                                                                                                cachePrefix,
                                                                                                err.getMessage()));

                                                                span.setAttribute("categories.count",
                                                                                result.getData().size());
                                                                span.setAttribute("categories.total_records",
                                                                                result.getTotalRecords());
                                                                tracingMetrics.completeSpanSuccess(tracingContext,
                                                                                spanName, successMessage);

                                                                return mapPagination(result, page, pageSize,
                                                                                responseMapper, successMessage);
                                                        });
                                })
                                .recover(throwable -> {
                                        logger.error("Failed to fetch paginated categories for {}", cachePrefix,
                                                        throwable);
                                        tracingMetrics.completeSpanError(tracingContext, spanName,
                                                        throwable.getMessage());
                                        return Future.succeededFuture(
                                                        ApiResponsePagination
                                                                        .<List<R>>error("Failed to fetch categories: "
                                                                                        + throwable.getMessage()));
                                });
        }

        private Future<ApiResponse<Category>> fetchCategoryFromDatabase(Long categoryId,
                        TracingMetrics.TracingContext tracingContext) {
                Span span = Span.fromContext(tracingContext.getContext());

                return categoryRepository.getCategoryById(categoryId)
                                .compose(category -> {
                                        if (category == null)
                                                return Future.failedFuture(new CustomException("Category not found"));

                                        span.setAttribute("category.id", category.getCategoryId());

                                        redisService.setJson("category:detail:" + categoryId, category.toJson(),
                                                        Duration.ofMinutes(30))
                                                        .onFailure(err -> logger.warn("Failed to cache category {}: {}",
                                                                        categoryId,
                                                                        err.getMessage()));

                                        return Future.succeededFuture(
                                                        ApiResponse.success("Category fetched successfully", category));
                                });
        }

        private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<Category> result, int page, int pageSize,
                        Function<Category, R> mapper, String message) {
                int totalRecords = result.getTotalRecords();
                int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
                List<R> data = result.getData().stream().map(mapper).toList();

                return new ApiResponsePagination<>("success", message, data,
                                new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
        }

        private void invalidateCache(Long categoryId) {
                if (categoryId != null) {
                        redisService.delete("category:detail:" + categoryId)
                                        .onSuccess(deleted -> {
                                                if (deleted > 0)
                                                        logger.debug("Cache category:{} invalidated successfully",
                                                                        categoryId);
                                        })
                                        .onFailure(err -> logger.warn("Failed to invalidate cache for category {}: {}",
                                                        categoryId,
                                                        err.getMessage()));
                }
                redisService.delete("categories:list:")
                                .onFailure(err -> logger.warn("Failed to invalidate list cache: {}", err.getMessage()));
        }

        private String generateSlug(String name) {
                if (name == null)
                        return "";
                return name.toLowerCase().trim().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-");
        }
}