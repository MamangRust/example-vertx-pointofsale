package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.cashier.CreateCashierRequest;
import com.sanedge.example_crud.domain.requests.cashier.FindAllCashierMerchant;
import com.sanedge.example_crud.domain.requests.cashier.FindAllCashiers;
import com.sanedge.example_crud.domain.requests.cashier.MonthCashierIdRequest;
import com.sanedge.example_crud.domain.requests.cashier.MonthCashierMerchantRequest;
import com.sanedge.example_crud.domain.requests.cashier.MonthTotalSales;
import com.sanedge.example_crud.domain.requests.cashier.MonthTotalSalesCashier;
import com.sanedge.example_crud.domain.requests.cashier.MonthTotalSalesMerchant;
import com.sanedge.example_crud.domain.requests.cashier.UpdateCashierRequest;
import com.sanedge.example_crud.domain.requests.cashier.YearCashierIdRequest;
import com.sanedge.example_crud.domain.requests.cashier.YearCashierMerchantRequest;
import com.sanedge.example_crud.domain.requests.cashier.YearTotalSalesCashier;
import com.sanedge.example_crud.domain.requests.cashier.YearTotalSalesMerchant;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.model.User;
import com.sanedge.example_crud.model.cashier.Cashier;
import com.sanedge.example_crud.model.cashier.CashierMonthSales;
import com.sanedge.example_crud.model.cashier.CashierMonthTotalSales;
import com.sanedge.example_crud.model.cashier.CashierYearSales;
import com.sanedge.example_crud.model.cashier.CashierYearTotalSales;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.CashierRepository;
import com.sanedge.example_crud.repository.MerchantRepository;
import com.sanedge.example_crud.repository.UserRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CashierService {
        private static final Logger logger = LoggerFactory.getLogger(CashierService.class);

        private final CashierRepository cashierRepository;
        private final MerchantRepository merchantRepository;
        private final UserRepository userRepository;
        private final RedisService redisService;
        private final TracingMetrics tracingMetrics;

        public Future<ApiResponsePagination<List<Cashier>>> getCashiers(FindAllCashiers req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.getCashiers");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setSearch(keyword);
                req.setPage(page);
                req.setPageSize(pageSize);

                return fetchPaginatedCashiers(req, "cashiers:all", page, pageSize, keyword,
                                cashierRepository::getCashiers,
                                Function.identity(), ctx, "get_all", "Cashiers fetched successfully");
        }

        public Future<ApiResponsePagination<List<Cashier>>> getCashiersActive(FindAllCashiers req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.getCashiersActive");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setSearch(keyword);
                req.setPage(page);
                req.setPageSize(pageSize);

                return fetchPaginatedCashiers(req, "cashiers:active", page, pageSize, keyword,
                                cashierRepository::getCashiersActive, Function.identity(), ctx, "get_active",
                                "Active cashiers fetched successfully");
        }

        public Future<ApiResponsePagination<List<Cashier>>> getCashiersTrashed(FindAllCashiers req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.getCashiersTrashed");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setSearch(keyword);
                req.setPage(page);
                req.setPageSize(pageSize);

                return fetchPaginatedCashiers(req, "cashiers:trashed", page, pageSize, keyword,
                                cashierRepository::getCashiersTrashed, Function.identity(), ctx, "get_trashed",
                                "Trashed cashiers fetched successfully");
        }

        public Future<ApiResponsePagination<List<Cashier>>> getCashiersByMerchant(FindAllCashierMerchant req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.getCashiersByMerchant");
                int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
                String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
                req.setSearch(keyword);
                req.setPage(page);
                req.setPageSize(pageSize);

                String cachePrefix = String.format("cashiers:merchant:%d", req.getMerchantId());
                return fetchPaginatedCashiers(req, cachePrefix, page, pageSize, keyword,
                                cashierRepository::getCashiersByMerchant, Function.identity(), ctx, "get_by_merchant",
                                "Cashiers by merchant fetched successfully");
        }

        public Future<ApiResponse<Cashier>> getCashierById(Long cashierId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.getCashierById",
                                Attributes.builder().put("cashier.id", cashierId).build());
                Span span = Span.fromContext(ctx.getContext());

                String cacheKey = "cashier:id:" + cashierId;

                return redisService.get(cacheKey)
                                .compose(cached -> {
                                        if (cached != null && !cached.isEmpty()) {
                                                span.setAttribute("cashier.cache_hit", true);
                                                try {
                                                        Cashier cashier = Cashier.fromJson(new JsonObject(cached));
                                                        tracingMetrics.completeSpanSuccess(ctx, "get_by_id",
                                                                        "Cashier fetched from cache");
                                                        return Future.succeededFuture(
                                                                        ApiResponse.success(
                                                                                        "Cashier fetched successfully (from cache)",
                                                                                        cashier));
                                                } catch (Exception e) {
                                                        logger.warn("Failed to parse cached cashier data for cashier {}: {}",
                                                                        cashierId,
                                                                        e.getMessage());
                                                        return fetchCashierFromDatabase(cashierId, ctx);
                                                }
                                        }
                                        span.setAttribute("cashier.cache_hit", false);
                                        return fetchCashierFromDatabase(cashierId, ctx);
                                })
                                .recover(err -> {
                                        logger.error("Failed to fetch cashier by id: {}", cashierId, err);
                                        tracingMetrics.completeSpanError(ctx, "get_by_id", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Cashier>error("Failed to fetch cashier: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Cashier>> createCashier(CreateCashierRequest req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.createCashier",
                                Attributes.builder().put("cashier.merchantId", req.getMerchantId()).build());

                return merchantRepository.getMerchantById(req.getMerchantId().longValue())
                                .compose(merchant -> {
                                        if (merchant == null) {
                                                return Future.<User>failedFuture(
                                                                new CustomException("Merchant not found"));
                                        }
                                        return userRepository.getUserById(req.getUserId().intValue());
                                })
                                .compose(user -> {
                                        if (user == null) {
                                                return Future.<Cashier>failedFuture(
                                                                new CustomException("User not found"));
                                        }
                                        return cashierRepository.createCashier(req);
                                })
                                .map(cashier -> {
                                        tracingMetrics.completeSpanSuccess(ctx, "create",
                                                        "Cashier created successfully");
                                        return ApiResponse.success("Cashier created successfully", cashier);
                                })
                                .recover(err -> {
                                        logger.error("Failed to create cashier", err);
                                        tracingMetrics.completeSpanError(ctx, "create", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Cashier>error("Failed to create cashier: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Cashier>> updateCashier(UpdateCashierRequest req) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.updateCashier",
                                Attributes.builder().put("cashier.id", req.getCashierId()).build());

                return cashierRepository.updateCashier(req)
                                .compose(cashier -> {
                                        if (cashier == null) {
                                                return Future.failedFuture(new CustomException(
                                                                "Cashier not found or update failed"));
                                        }
                                        invalidateCache("cashier:id:" + cashier.getCashierId());
                                        tracingMetrics.completeSpanSuccess(ctx, "update",
                                                        "Cashier updated successfully");
                                        return Future.succeededFuture(
                                                        ApiResponse.success("Cashier updated successfully", cashier));
                                })
                                .recover(err -> {
                                        logger.error("Failed to update cashier: {}", req.getCashierId(), err);
                                        tracingMetrics.completeSpanError(ctx, "update", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Cashier>error("Failed to update cashier: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Cashier>> trashCashier(Long cashierId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.trashCashier",
                                Attributes.builder().put("cashier.id", cashierId).build());

                return cashierRepository.trashCashier(cashierId)
                                .compose(cashier -> {
                                        if (cashier == null) {
                                                return Future.failedFuture(new CustomException(
                                                                "Cashier not found or already trashed"));
                                        }
                                        invalidateCache("cashier:id:" + cashierId);
                                        tracingMetrics.completeSpanSuccess(ctx, "trash",
                                                        "Cashier trashed successfully");
                                        return Future.succeededFuture(
                                                        ApiResponse.success("Cashier trashed successfully", cashier));
                                })
                                .recover(err -> {
                                        logger.error("Failed to trash cashier: {}", cashierId, err);
                                        tracingMetrics.completeSpanError(ctx, "trash", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Cashier>error("Failed to trash cashier: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Cashier>> restoreCashier(Long cashierId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.restoreCashier",
                                Attributes.builder().put("cashier.id", cashierId).build());

                return cashierRepository.findByTrashed(cashierId)
                                .compose(cashier -> {
                                        if (cashier == null) {
                                                return Future.failedFuture(new CustomException(
                                                                "Cashier not found or not trashed"));
                                        }
                                        return cashierRepository.restoreCashier(cashierId);
                                })
                                .compose(cashier -> {
                                        invalidateCache("cashier:id:" + cashierId);
                                        tracingMetrics.completeSpanSuccess(ctx, "restore",
                                                        "Cashier restored successfully");
                                        return Future.succeededFuture(
                                                        ApiResponse.success("Cashier restored successfully", cashier));
                                })
                                .recover(err -> {
                                        logger.error("Failed to restore cashier: {}", cashierId, err);
                                        tracingMetrics.completeSpanError(ctx, "restore", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Cashier>error("Failed to restore cashier: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Boolean>> deleteCashierPermanently(Long cashierId) {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.deleteCashierPermanently",
                                Attributes.builder().put("cashier.id", cashierId).build());

                return cashierRepository.findByTrashed(cashierId)
                                .compose(cashier -> {
                                        if (cashier == null) {
                                                return Future.failedFuture(new CustomException(
                                                                "Cashier not found or not trashed"));
                                        }
                                        return cashierRepository.deleteCashierPermanently(cashierId);
                                })
                                .map(v -> {
                                        invalidateCache("cashier:id:" + cashierId);
                                        tracingMetrics.completeSpanSuccess(ctx, "delete_permanent",
                                                        "Cashier deleted permanently");
                                        return ApiResponse.success("Cashier deleted permanently", true);
                                })
                                .recover(err -> {
                                        logger.error("Failed to permanently delete cashier: {}", cashierId, err);
                                        tracingMetrics.completeSpanError(ctx, "delete_permanent", err.getMessage());
                                        if (err instanceof CustomException) {
                                                return Future.failedFuture(err);
                                        }
                                        return Future.succeededFuture(
                                                        ApiResponse.<Boolean>error("Failed to delete cashier: "
                                                                        + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Boolean>> restoreAllCashiers() {
                TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("CashierService.restoreAllCashiers");
                return cashierRepository.restoreAllCashiers()
                                .compose(count -> {
                                         if (count == 0) {
                                                 return Future.failedFuture(new CustomException("No trashed cashiers found"));
                                         }
                                         tracingMetrics.completeSpanSuccess(ctx, "restore_all", "All cashiers restored");
                                         return Future.succeededFuture(ApiResponse.success("All cashiers restored successfully", true));
                                })
                                .recover(err -> {
                                         logger.error("Failed to restore all cashiers", err);
                                         tracingMetrics.completeSpanError(ctx, "restore_all", err.getMessage());
                                         if (err instanceof CustomException) {
                                                 return Future.failedFuture(err);
                                         }
                                         return Future.succeededFuture(
                                                         ApiResponse.<Boolean>error("Failed to restore all cashiers: "
                                                                         + err.getMessage()));
                                });
        }

        public Future<ApiResponse<Boolean>> deleteAllCashiersPermanent() {
                TracingMetrics.TracingContext ctx = tracingMetrics
                                .startSpan("CashierService.deleteAllCashiersPermanent");
                return cashierRepository.deleteAllPermanentCashiers()
                                .compose(count -> {
                                         if (count == 0) {
                                                 return Future.failedFuture(new CustomException("No trashed cashiers found"));
                                         }
                                         tracingMetrics.completeSpanSuccess(ctx, "delete_all_permanent",
                                                         "All cashiers deleted");
                                         return Future.succeededFuture(ApiResponse.success("All cashiers deleted permanently", true));
                                })
                                .recover(err -> {
                                         logger.error("Failed to permanently delete all cashiers", err);
                                         tracingMetrics.completeSpanError(ctx, "delete_all_permanent", err.getMessage());
                                         if (err instanceof CustomException) {
                                                 return Future.failedFuture(err);
                                         }
                                         return Future.succeededFuture(
                                                         ApiResponse.<Boolean>error("Failed to delete all cashiers: "
                                                                         + err.getMessage()));
                                });
        }

        public Future<ApiResponse<List<CashierMonthTotalSales>>> getMonthlyTotalSalesCashier(MonthTotalSales req) {
                String cacheKey = String.format("cashier:report:monthly_total:%d:%d", req.getYear(), req.getMonth());
                return fetchStats(cacheKey, cashierRepository.getMonthlyTotalSalesCashier(req), Function.identity(),
                                CashierMonthTotalSales.class, "report_monthly_total",
                                "Monthly total sales fetched successfully");
        }

        public Future<ApiResponse<List<CashierYearTotalSales>>> getYearlyTotalSalesCashier(int year) {
                String cacheKey = String.format("cashier:report:yearly_total:%d", year);
                return fetchStats(cacheKey, cashierRepository.getYearlyTotalSalesCashier(year), Function.identity(),
                                CashierYearTotalSales.class, "report_yearly_total",
                                "Yearly total sales fetched successfully");
        }

        public Future<ApiResponse<List<CashierMonthTotalSales>>> getMonthlyTotalSalesById(MonthTotalSalesCashier req) {
                String cacheKey = String.format("cashier:report:monthly_total_id:%d:%d:%d", req.getCashierId(),
                                req.getYear(),
                                req.getMonth());
                return fetchStats(cacheKey, cashierRepository.getMonthlyTotalSalesById(req), Function.identity(),
                                CashierMonthTotalSales.class, "report_monthly_total_id",
                                "Monthly total sales by ID fetched successfully");
        }

        public Future<ApiResponse<List<CashierYearTotalSales>>> getYearlyTotalSalesById(YearTotalSalesCashier req) {
                String cacheKey = String.format("cashier:report:yearly_total:%d:cashier:%d", req.getYear(),
                                req.getCashierId());
                return fetchStats(cacheKey, cashierRepository.getYearlyTotalSalesById(req), Function.identity(),
                                CashierYearTotalSales.class, "report_yearly_total_by_id",
                                "Yearly total sales by ID fetched successfully");
        }

        public Future<ApiResponse<List<CashierMonthTotalSales>>> getMonthlyTotalSalesByMerchant(
                        MonthTotalSalesMerchant req) {
                String cacheKey = String.format("cashier:report:monthly_total_merchant:%d:%d:%d", req.getMerchantId(),
                                req.getYear(), req.getMonth());
                return fetchStats(cacheKey, cashierRepository.getMonthlyTotalSalesByMerchant(req), Function.identity(),
                                CashierMonthTotalSales.class, "report_monthly_total_merchant",
                                "Monthly total sales by merchant fetched successfully");
        }

        public Future<ApiResponse<List<CashierYearTotalSales>>> getYearlyTotalSalesByMerchant(
                        YearTotalSalesMerchant req) {
                String cacheKey = String.format("cashier:report:yearly_total_merchant:%d:%d", req.getMerchantId(),
                                req.getYear());
                return fetchStats(cacheKey, cashierRepository.getYearlyTotalSalesByMerchant(req), Function.identity(),
                                CashierYearTotalSales.class, "report_yearly_total_merchant",
                                "Yearly total sales by merchant fetched successfully");
        }

        public Future<ApiResponse<List<CashierMonthSales>>> getMonthlyCashier(int year) {
                String cacheKey = String.format("cashier:report:monthly_cashier:%d", year);
                return fetchStats(cacheKey, cashierRepository.getMonthlyCashier(year), Function.identity(),
                                CashierMonthSales.class, "report_monthly_cashier",
                                "Monthly cashier fetched successfully");
        }

        public Future<ApiResponse<List<CashierYearSales>>> getYearlyCashier(int year) {
                String cacheKey = String.format("cashier:report:yearly_cashier:%d", year);
                return fetchStats(cacheKey, cashierRepository.getYearlyCashier(year), Function.identity(),
                                CashierYearSales.class, "report_yearly_cashier", "Yearly cashier fetched successfully");
        }

        public Future<ApiResponse<List<CashierMonthSales>>> getMonthlyCashierByCashierId(MonthCashierIdRequest req) {
                String cacheKey = String.format("cashier:report:monthly_cashier:%d:cashier:%d", req.getYear(),
                                req.getCashierId());
                return fetchStats(cacheKey, cashierRepository.getMonthlyCashierByCashierId(req), Function.identity(),
                                CashierMonthSales.class, "report_monthly_by_cashier",
                                "Monthly cashier by ID fetched successfully");
        }

        public Future<ApiResponse<List<CashierYearSales>>> getYearlyCashierByCashierId(YearCashierIdRequest req) {
                String cacheKey = String.format("cashier:report:yearly_cashier:%d:cashier:%d", req.getYear(),
                                req.getCashierId());
                return fetchStats(cacheKey, cashierRepository.getYearlyCashierByCashierId(req), Function.identity(),
                                CashierYearSales.class, "report_yearly_by_cashier",
                                "Yearly cashier by ID fetched successfully");
        }

        public Future<ApiResponse<List<CashierMonthSales>>> getMonthlyCashierByMerchant(
                        MonthCashierMerchantRequest req) {
                String cacheKey = String.format("cashier:report:monthly_cashier:%d:merchant:%d", req.getYear(),
                                req.getMerchantId());
                return fetchStats(cacheKey, cashierRepository.getMonthlyCashierByMerchant(req), Function.identity(),
                                CashierMonthSales.class, "report_monthly_by_merchant",
                                "Monthly cashier by merchant fetched successfully");
        }

        public Future<ApiResponse<List<CashierYearSales>>> getYearlyCashierByMerchant(YearCashierMerchantRequest req) {
                String cacheKey = String.format("cashier:report:yearly_cashier:%d:merchant:%d", req.getYear(),
                                req.getMerchantId());
                return fetchStats(cacheKey, cashierRepository.getYearlyCashierByMerchant(req), Function.identity(),
                                CashierYearSales.class, "report_yearly_by_merchant",
                                "Yearly cashier by merchant fetched successfully");
        }

        private <T, R> Future<ApiResponse<List<R>>> fetchStats(String cacheKey, Future<List<T>> dbFuture,
                        Function<T, R> mapper, Class<R> responseType, String spanName, String successMessage) {

                TracingMetrics.TracingContext tracingContext = tracingMetrics
                                .startSpan("CashierStatsService." + spanName);

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

        private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedCashiers(T req, String cachePrefix,
                        int page,
                        int pageSize, String keyword, Function<T, Future<PagedResult<Cashier>>> dbFetcher,
                        Function<Cashier, R> responseMapper, TracingMetrics.TracingContext tracingContext,
                        String spanName,
                        String successMessage) {

                Span span = Span.fromContext(tracingContext.getContext());
                String cacheKey = String.format("%s:page:%d:size:%d:search:%s", cachePrefix, page, pageSize, keyword);

                return redisService.get(cacheKey)
                                .compose(cached -> {
                                        if (cached != null && !cached.isEmpty()) {
                                                span.setAttribute("cashiers.cache_hit", true);
                                                try {
                                                        JsonObject json = new JsonObject(cached);
                                                        int totalRecords = json.getInteger("totalRecords");
                                                        int totalPages = (int) Math
                                                                        .ceil((double) totalRecords / pageSize);

                                                        List<R> data = json.getJsonArray("data").stream()
                                                                        .map(obj -> responseMapper.apply(Cashier
                                                                                        .fromJson((JsonObject) obj)))
                                                                        .toList();

                                                        tracingMetrics.completeSpanSuccess(tracingContext, spanName,
                                                                        "Cashiers fetched from cache");
                                                        return Future.succeededFuture(new ApiResponsePagination<>(
                                                                        "success", successMessage, data,
                                                                        new PaginationMeta(page + 1, pageSize,
                                                                                        totalPages, totalRecords)));
                                                } catch (Exception e) {
                                                        logger.warn("Failed to parse cached paginated cashiers: {}",
                                                                        e.getMessage());
                                                }
                                        }

                                        span.setAttribute("cashiers.cache_hit", false);
                                        return dbFetcher.apply(req)
                                                        .map(result -> {
                                                                JsonObject jsonToCache = new JsonObject()
                                                                                .put("totalRecords", result
                                                                                                .getTotalRecords())
                                                                                .put("data", new JsonArray(
                                                                                                result.getData().stream()
                                                                                                                .map(Cashier::toJson)
                                                                                                                .toList()));

                                                                redisService.set(cacheKey, jsonToCache.encode(),
                                                                                Duration.ofMinutes(5))
                                                                                .onFailure(err -> logger.warn(
                                                                                                "Failed to cache {}: {}",
                                                                                                cachePrefix,
                                                                                                err.getMessage()));

                                                                span.setAttribute("cashiers.count",
                                                                                result.getData().size());
                                                                span.setAttribute("cashiers.total_records",
                                                                                result.getTotalRecords());
                                                                tracingMetrics.completeSpanSuccess(tracingContext,
                                                                                spanName, successMessage);

                                                                return mapPagination(result, page, pageSize,
                                                                                responseMapper, successMessage);
                                                        });
                                })
                                .recover(throwable -> {
                                        logger.error("Failed to fetch paginated cashiers for {}", cachePrefix,
                                                        throwable);
                                        tracingMetrics.completeSpanError(tracingContext, spanName,
                                                        throwable.getMessage());
                                        return Future.succeededFuture(
                                                        ApiResponsePagination
                                                                        .<List<R>>error("Failed to fetch cashiers: "
                                                                                        + throwable.getMessage()));
                                });
        }

        private Future<ApiResponse<Cashier>> fetchCashierFromDatabase(Long cashierId,
                        TracingMetrics.TracingContext tracingContext) {
                Span span = Span.fromContext(tracingContext.getContext());

                return cashierRepository.getCashierById(cashierId)
                                .compose(cashier -> {
                                        if (cashier == null)
                                                return Future.failedFuture(new CustomException("Cashier not found"));

                                        span.setAttribute("cashier.id", cashier.getCashierId());

                                        redisService.setJson("cashier:id:" + cashierId, cashier.toJson(),
                                                        Duration.ofMinutes(30))
                                                        .onFailure(err -> logger.warn("Failed to cache cashier {}: {}",
                                                                        cashierId,
                                                                        err.getMessage()));

                                        return Future.succeededFuture(
                                                        ApiResponse.success("Cashier fetched successfully", cashier));
                                });
        }

        private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<Cashier> result, int page, int pageSize,
                        Function<Cashier, R> mapper, String message) {
                int totalRecords = result.getTotalRecords();
                int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
                List<R> data = result.getData().stream().map(mapper).toList();

                return new ApiResponsePagination<>("success", message, data,
                                new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
        }

        private void invalidateCache(String cacheKey) {
                redisService.delete(cacheKey)
                                .onSuccess(deleted -> {
                                        if (deleted > 0)
                                                logger.debug("Cache {} invalidated successfully", cacheKey);
                                })
                                .onFailure(err -> logger.warn("Failed to invalidate cache for {}: {}", cacheKey,
                                                err.getMessage()));
        }
}