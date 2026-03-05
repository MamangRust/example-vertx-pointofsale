package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sanedge.example_crud.domain.response.api.PagedResult;
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

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CashierService {

    private final CashierRepository cashierRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Future<ApiResponse<PagedResult<Cashier>>> getCashiers(FindAllCashiers req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getCashiers");
        String cacheKey = String.format("cashiers:all:%d:%d:%s", req.getPage(), req.getPageSize(), req.getSearch());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getCashiers(req),
                        new TypeReference<PagedResult<Cashier>>() {
                        }, tracingCtx, "get_all"))
                .recover(err -> handleError(tracingCtx, "get_all", err));
    }

    public Future<ApiResponse<Cashier>> getCashierById(Long cashierId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getCashierById");
        String cacheKey = "cashier:id:" + cashierId;

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getCashierById(cashierId).map(cashier -> {
                            if (cashier == null)
                                throw new CustomException("Cashier not found");
                            return cashier;
                        }),
                        new TypeReference<Cashier>() {
                        }, tracingCtx, "get_by_id"))
                .recover(err -> handleError(tracingCtx, "get_by_id", err));
    }

    public Future<ApiResponse<PagedResult<Cashier>>> getCashiersActive(FindAllCashiers req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getCashiersActive");
        String cacheKey = String.format("cashiers:active:%d:%d:%s", req.getPage(), req.getPageSize(), req.getSearch());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getCashiersActive(req),
                        new TypeReference<PagedResult<Cashier>>() {
                        }, tracingCtx, "get_active"))
                .recover(err -> handleError(tracingCtx, "get_active", err));
    }

    public Future<ApiResponse<PagedResult<Cashier>>> getCashiersTrashed(FindAllCashiers req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getCashiersTrashed");
        String cacheKey = String.format("cashiers:trashed:%d:%d:%s", req.getPage(), req.getPageSize(), req.getSearch());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getCashiersTrashed(req),
                        new TypeReference<PagedResult<Cashier>>() {
                        }, tracingCtx, "get_trashed"))
                .recover(err -> handleError(tracingCtx, "get_trashed", err));
    }

    public Future<ApiResponse<PagedResult<Cashier>>> getCashiersByMerchant(FindAllCashierMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getCashiersByMerchant");
        String cacheKey = String.format("cashiers:merchant:%d:%d:%d:%s", req.getMerchantId(), req.getPage(), req.getPageSize(), req.getSearch());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getCashiersByMerchant(req),
                        new TypeReference<PagedResult<Cashier>>() {
                        }, tracingCtx, "get_by_merchant"))
                .recover(err -> handleError(tracingCtx, "get_by_merchant", err));
    }

    public Future<ApiResponse<Cashier>> createCashier(CreateCashierRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.createCashier");

        return merchantRepository.getMerchantById(req.getMerchantId().longValue())
                .compose(merchant -> {
                    if (merchant == null) {
                        return Future.<User>failedFuture(new CustomException("Merchant not found"));
                    }
                    return userRepository.getUserById(req.getUserId().intValue());
                })
                .compose(user -> {
                    if (user == null) {
                        return Future.<Cashier>failedFuture(new CustomException("User not found"));
                    }
                    return cashierRepository.createCashier(req);
                })
                .map(cashier -> {
                    return ApiResponse.success("Cashier created successfully", cashier);
                })
                .recover(err -> handleError(tracingCtx, "create", err));
    }

    public Future<ApiResponse<Cashier>> updateCashier(UpdateCashierRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.updateCashier");

        return cashierRepository.updateCashier(req)
                .map(cashier -> {
                    if (cashier == null) {
                        throw new CustomException("Failed to update cashier or cashier not found");
                    }
                    invalidateCache(cashier.getCashierId());
                    tracingMetrics.completeSpanSuccess(tracingCtx, "update", "Cashier updated");
                    return ApiResponse.success("Cashier updated successfully", cashier);
                })
                .recover(err -> handleError(tracingCtx, "update", err));
    }

    public Future<ApiResponse<Cashier>> trashCashier(Long cashierId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.trashCashier");

        return cashierRepository.trashCashier(cashierId)
                .map(cashier -> {
                    if (cashier == null) {
                        throw new CustomException("Cashier not found or already trashed");
                    }
                    invalidateCache(cashier.getCashierId());
                    tracingMetrics.completeSpanSuccess(tracingCtx, "trash", "Cashier trashed");
                    return ApiResponse.success("Cashier trashed successfully", cashier);
                })
                .recover(err -> handleError(tracingCtx, "trash", err));
    }

    public Future<ApiResponse<Cashier>> restoreCashier(Long cashierId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.restoreCashier");

        return cashierRepository.restoreCashier(cashierId)
                .map(cashier -> {
                    if (cashier == null) {
                        throw new CustomException("Cashier not found or not trashed");
                    }
                    invalidateCache(cashier.getCashierId());
                    tracingMetrics.completeSpanSuccess(tracingCtx, "restore", "Cashier restored");
                    return ApiResponse.success("Cashier restored successfully", cashier);
                })
                .recover(err -> handleError(tracingCtx, "restore", err));
    }

    public Future<ApiResponse<Boolean>> deleteCashierPermanently(Long cashierId) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.deleteCashierPermanently");

        return cashierRepository.deleteCashierPermanently(cashierId)
                .map(v -> {
                    invalidateCache(cashierId);
                    tracingMetrics.completeSpanSuccess(tracingCtx, "delete_permanent", "Cashier deleted permanently");
                    return ApiResponse.success("Cashier deleted permanently", true);
                })
                .recover(err -> handleError(tracingCtx, "delete_permanent", err));
    }

    public Future<ApiResponse<Boolean>> restoreAllCashiers() {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.restoreAllCashiers");

        return cashierRepository.restoreAllCashiers()
                .map(v -> {
                    tracingMetrics.completeSpanSuccess(tracingCtx, "restore_all", "All cashiers restored");
                    return ApiResponse.success("All cashiers restored successfully", true);
                })
                .recover(err -> handleError(tracingCtx, "restore_all", err));
    }

    public Future<ApiResponse<Boolean>> deleteAllCashiersPermanent() {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.deleteAllCashiersPermanent");

        return cashierRepository.deleteAllPermanentCashiers()
                .map(v -> {
                    tracingMetrics.completeSpanSuccess(tracingCtx, "delete_all_permanent", "All cashiers deleted");
                    return ApiResponse.success("All cashiers deleted permanently", true);
                })
                .recover(err -> handleError(tracingCtx, "delete_all_permanent", err));
    }

    public Future<ApiResponse<List<CashierMonthTotalSales>>> getMonthlyTotalSalesCashier(MonthTotalSales req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.getMonthlyTotalSalesCashier");
        String cacheKey = String.format("cashier:report:monthly_total:%d:%d", req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getMonthlyTotalSalesCashier(req),
                        new TypeReference<List<CashierMonthTotalSales>>() {
                        }, tracingCtx, "report_monthly_total"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierYearTotalSales>>> getYearlyTotalSalesCashier(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.getYearlyTotalSalesCashier");
        String cacheKey = String.format("cashier:report:yearly_total:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getYearlyTotalSalesCashier(year),
                        new TypeReference<List<CashierYearTotalSales>>() {
                        }, tracingCtx, "report_yearly_total"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierMonthTotalSales>>> getMonthlyTotalSalesById(MonthTotalSalesCashier req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getMonthlyTotalSalesById");
        String cacheKey = String.format("cashier:report:monthly_total_id:%d:%d:%d", req.getCashierId(), req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getMonthlyTotalSalesById(req),
                        new TypeReference<List<CashierMonthTotalSales>>() {
                        }, tracingCtx, "report_monthly_total_id"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierYearTotalSales>>> getYearlyTotalSalesById(YearTotalSalesCashier req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getYearlyTotalSalesById");
        String cacheKey = String.format("cashier:report:yearly_total:%d:cashier:%d", req.getYear(), req.getCashierId());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getYearlyTotalSalesById(req),
                        new TypeReference<List<CashierYearTotalSales>>() {
                        }, tracingCtx, "report_yearly_total_by_id"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierMonthTotalSales>>> getMonthlyTotalSalesByMerchant(
            MonthTotalSalesMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.getMonthlyTotalSalesByMerchant");
        String cacheKey = String.format("cashier:report:monthly_total_merchant:%d:%d:%d", req.getMerchantId(), req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getMonthlyTotalSalesByMerchant(req),
                        new TypeReference<List<CashierMonthTotalSales>>() {
                        }, tracingCtx, "report_monthly_total_merchant"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierYearTotalSales>>> getYearlyTotalSalesByMerchant(YearTotalSalesMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.getYearlyTotalSalesByMerchant");
        String cacheKey = String.format("cashier:report:yearly_total_merchant:%d:%d", req.getMerchantId(), req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getYearlyTotalSalesByMerchant(req),
                        new TypeReference<List<CashierYearTotalSales>>() {
                        }, tracingCtx, "report_yearly_total_merchant"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierMonthSales>>> getMonthlyCashier(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getMonthlyCashier");
        String cacheKey = String.format("cashier:report:monthly_cashier:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getMonthlyCashier(year),
                        new TypeReference<List<CashierMonthSales>>() {
                        }, tracingCtx, "report_monthly_cashier"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierYearSales>>> getYearlyCashier(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("CashierService.getYearlyCashier");
        String cacheKey = String.format("cashier:report:yearly_cashier:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getYearlyCashier(year),
                        new TypeReference<List<CashierYearSales>>() {
                        }, tracingCtx, "report_yearly_cashier"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierMonthSales>>> getMonthlyCashierByCashierId(MonthCashierIdRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.getMonthlyCashierByCashierId");
        String cacheKey = String.format("cashier:report:monthly_cashier:%d:cashier:%d", req.getYear(), req.getCashierId());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getMonthlyCashierByCashierId(req),
                        new TypeReference<List<CashierMonthSales>>() {
                        }, tracingCtx, "report_monthly_by_cashier"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierYearSales>>> getYearlyCashierByCashierId(YearCashierIdRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.getYearlyCashierByCashierId");
        String cacheKey = String.format("cashier:report:yearly_cashier:%d:cashier:%d", req.getYear(), req.getCashierId());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getYearlyCashierByCashierId(req),
                        new TypeReference<List<CashierYearSales>>() {
                        }, tracingCtx, "report_yearly_by_cashier"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierMonthSales>>> getMonthlyCashierByMerchant(MonthCashierMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.getMonthlyCashierByMerchant");
        String cacheKey = String.format("cashier:report:monthly_cashier:%d:merchant:%d", req.getYear(), req.getMerchantId());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getMonthlyCashierByMerchant(req),
                        new TypeReference<List<CashierMonthSales>>() {
                        }, tracingCtx, "report_monthly_by_merchant"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<CashierYearSales>>> getYearlyCashierByMerchant(YearCashierMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("CashierService.getYearlyCashierByMerchant");
        String cacheKey = String.format("cashier:report:yearly_cashier:%d:merchant:%d", req.getYear(), req.getMerchantId());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> cashierRepository.getYearlyCashierByMerchant(req),
                        new TypeReference<List<CashierYearSales>>() {
                        }, tracingCtx, "report_yearly_by_merchant"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    private void invalidateCache(Long cashierId) {
        redisService.delete("cashier:id:" + cashierId);
        log.debug("Invalidated cache for cashier ID: {}", cashierId);
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

    private <T> Future<ApiResponse<T>> handleError(TracingMetrics.TracingContext ctx, String methodName,
            Throwable err) {
        log.error("Cashier service error in {}: {}", methodName, err.getMessage());
        tracingMetrics.completeSpanError(ctx, methodName, err.getMessage());
        return Future.succeededFuture(ApiResponse.<T>error(err.getMessage()));
    }
}