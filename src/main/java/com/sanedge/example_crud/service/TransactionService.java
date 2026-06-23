package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.domain.requests.transactions.CreateTransactionRequest;
import com.sanedge.example_crud.domain.requests.transactions.FindAllTransactionByMerchantRequest;
import com.sanedge.example_crud.domain.requests.transactions.FindAllTransactionRequest;
import com.sanedge.example_crud.domain.requests.transactions.MonthAmountTransactionMerchant;
import com.sanedge.example_crud.domain.requests.transactions.MonthAmountTransactionRequest;
import com.sanedge.example_crud.domain.requests.transactions.MonthMethodTransactionMerchantRequest;
import com.sanedge.example_crud.domain.requests.transactions.MonthMethodTransactionRequest;
import com.sanedge.example_crud.domain.requests.transactions.UpdateTransactionRequest;
import com.sanedge.example_crud.domain.requests.transactions.YearAmountTransactionMerchant;
import com.sanedge.example_crud.domain.requests.transactions.YearMethodTransactionMerchantRequest;
import com.sanedge.example_crud.domain.response.api.ApiResponse;
import com.sanedge.example_crud.domain.response.api.ApiResponsePagination;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.domain.response.api.PaginationMeta;
import com.sanedge.example_crud.domain.response.transaction.TransactionResponse;
import com.sanedge.example_crud.domain.response.transaction.TransactionResponseDeleteAt;
import com.sanedge.example_crud.exception.CustomException;
import com.sanedge.example_crud.exception.NotFoundException;
import com.sanedge.example_crud.model.Merchant;
import com.sanedge.example_crud.model.order.Order;
import com.sanedge.example_crud.model.order.OrderItem;
import com.sanedge.example_crud.model.transaction.Transaction;
import com.sanedge.example_crud.model.transaction.TransactionMonthlyAmountFailed;
import com.sanedge.example_crud.model.transaction.TransactionMonthlyAmountSuccess;
import com.sanedge.example_crud.model.transaction.TransactionMonthlyMethod;
import com.sanedge.example_crud.model.transaction.TransactionYearMethod;
import com.sanedge.example_crud.model.transaction.TransactionYearlyAmountFailed;
import com.sanedge.example_crud.model.transaction.TransactionYearlyAmountSuccess;
import com.sanedge.example_crud.observability.TracingMetrics;
import com.sanedge.example_crud.repository.MerchantRepository;
import com.sanedge.example_crud.repository.OrderItemRepository;
import com.sanedge.example_crud.repository.OrderRepository;
import com.sanedge.example_crud.repository.TransactionRepository;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;

    public Future<ApiResponsePagination<List<TransactionResponse>>> getAll(FindAllTransactionRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.getAll");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedTransactions(req, "transactions:all", page, pageSize, keyword,
                transactionRepository::getTransactions, TransactionResponse::from, ctx, "get_all",
                "Transactions fetched successfully");
    }

    public Future<ApiResponsePagination<List<TransactionResponse>>> getActive(FindAllTransactionRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.getActive");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedTransactions(req, "transactions:active", page, pageSize, keyword,
                transactionRepository::getTransactionsActive, TransactionResponse::from, ctx, "get_active",
                "Active transactions fetched successfully");
    }

    public Future<ApiResponsePagination<List<TransactionResponseDeleteAt>>> getTrashed(FindAllTransactionRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.getTrashed");
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        return fetchPaginatedTransactions(req, "transactions:trashed", page, pageSize, keyword,
                transactionRepository::getTransactionsTrashed, TransactionResponseDeleteAt::from, ctx, "get_trashed",
                "Trashed transactions fetched successfully");
    }

    public Future<ApiResponsePagination<List<TransactionResponse>>> getByMerchant(
            FindAllTransactionByMerchantRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.getByMerchant",
                Attributes.builder().put("merchant.id", req.getMerchantId()).build());
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        String cachePrefix = String.format("transactions:merchant:%d", req.getMerchantId());
        return fetchPaginatedTransactions(req, cachePrefix, page, pageSize, keyword,
                transactionRepository::getTransactionByMerchant, TransactionResponse::from, ctx, "get_by_merchant",
                "Merchant transactions fetched successfully");
    }

    public Future<ApiResponse<TransactionResponse>> getById(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.getById",
                Attributes.builder().put("id", id).build());
        Span span = Span.fromContext(ctx.getContext());

        String cacheKey = "transaction:" + id;

        return redisService.get(cacheKey)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        span.setAttribute("cache.hit", true);
                        try {
                            Transaction data = Transaction.fromJson(new JsonObject(cached));
                            tracingMetrics.completeSpanSuccess(ctx, "get_by_id", "Transaction fetched from cache");
                            return Future.succeededFuture(
                                    ApiResponse.success("Data from cache", TransactionResponse.from(data)));
                        } catch (Exception e) {
                            logger.warn("Failed to parse cached transaction {}: {}", id, e.getMessage());
                            return fetchTransactionFromDatabase(id, ctx);
                        }
                    }
                    span.setAttribute("cache.hit", false);
                    return fetchTransactionFromDatabase(id, ctx);
                })
                .recover(err -> {
                    logger.error("Failed to fetch by id", err);
                    tracingMetrics.completeSpanError(ctx, "get_by_id", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<TransactionResponse>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponse>> getByOrderId(Long orderId) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.getByOrderId");

        return transactionRepository.getTransactionByOrderId(orderId)
                .map(data -> {
                    if (data == null) {
                        throw new NotFoundException("Transaction not found for order: " + orderId);
                    }
                    tracingMetrics.completeSpanSuccess(ctx, "get_by_order", "Success");
                    return ApiResponse.success("Data fetched successfully", TransactionResponse.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to fetch by order id", err);
                    tracingMetrics.completeSpanError(ctx, "get_by_order", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<TransactionResponse>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponse>> createTransaction(CreateTransactionRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.createTransaction",
                Attributes.builder()
                        .put("order.id", req.getOrderID())
                        .put("merchant.id", req.getMerchantID())
                        .build());
        Span span = Span.fromContext(ctx.getContext());

        return merchantRepository.getMerchantById(req.getMerchantID().longValue())
                .<Order>compose(merchant -> {
                    if (merchant == null)
                        return Future.failedFuture(new CustomException("Merchant not found"));
                    return orderRepository.getOrderById(req.getOrderID().longValue());
                })
                .<List<OrderItem>>compose(order -> {
                    if (order == null)
                        return Future.failedFuture(new CustomException("Order not found"));
                    return orderItemRepository.getOrderItemsByOrder(req.getOrderID().longValue());
                })
                .<Transaction>compose(orderItems -> {
                    if (orderItems == null || orderItems.isEmpty())
                        return Future.failedFuture(new CustomException("Order items not found"));

                    for (OrderItem item : orderItems) {
                        if (item.getQuantity() <= 0)
                            return Future.failedFuture(new CustomException("Invalid order item quantity"));
                    }

                    int totalAmount = orderItems.stream().mapToInt(item -> item.getPrice() * item.getQuantity()).sum();
                    int ppn = totalAmount * 11 / 100;
                    int totalAmountWithTax = totalAmount + ppn;

                    if (req.getAmount() < totalAmountWithTax)
                        return Future.failedFuture(
                                new CustomException("Insufficient balance. Required: " + totalAmountWithTax));

                    req.setAmount(totalAmountWithTax);
                    req.setPaymentStatus("success");

                    return transactionRepository.createTransaction(req);
                })
                .map(transaction -> {
                    span.setAttribute("transaction.id", transaction.getTransactionId());
                    tracingMetrics.completeSpanSuccess(ctx, "create", "Transaction created successfully");
                    return ApiResponse.success("Transaction created successfully",
                            TransactionResponse.from(transaction));
                })
                .recover(err -> {
                    logger.error("Failed to create transaction", err);
                    tracingMetrics.completeSpanError(ctx, "create", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse
                                    .<TransactionResponse>error("Failed to create transaction: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponse>> updateTransaction(UpdateTransactionRequest req) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.updateTransaction",
                Attributes.builder()
                        .put("transaction.id", req.getTransactionID())
                        .put("order.id", req.getOrderID())
                        .put("merchant.id", req.getMerchantID())
                        .build());
        Span span = Span.fromContext(ctx.getContext());

        return transactionRepository.getTransactionById(req.getTransactionID().longValue())
                .<Merchant>compose(existingTx -> {
                    if (existingTx == null)
                        return Future.failedFuture(new CustomException("Transaction not found"));

                    String status = existingTx.getStatus() != null ? existingTx.getStatus().toString() : "";
                    if ("success".equals(status) || "refunded".equals(status))
                        return Future.failedFuture(new CustomException("Payment status cannot be modified"));

                    return merchantRepository.getMerchantById(req.getMerchantID().longValue());
                })
                .<Order>compose(merchant -> {
                    if (merchant == null)
                        return Future.failedFuture(new CustomException("Merchant not found"));
                    return orderRepository.getOrderById(req.getOrderID().longValue());
                })
                .<List<OrderItem>>compose(order -> {
                    if (order == null)
                        return Future.failedFuture(new CustomException("Order not found"));
                    return orderItemRepository.getOrderItemsByOrder(req.getOrderID().longValue());
                })
                .<Transaction>compose(orderItems -> {
                    if (orderItems == null || orderItems.isEmpty())
                        return Future.failedFuture(new CustomException("Order items not found"));

                    for (OrderItem item : orderItems) {
                        if (item.getQuantity() <= 0)
                            return Future.failedFuture(new CustomException("Invalid order item quantity"));
                    }

                    int totalAmount = orderItems.stream().mapToInt(item -> item.getPrice() * item.getQuantity()).sum();
                    int ppn = totalAmount * 11 / 100;
                    int totalAmountWithTax = totalAmount + ppn;

                    if (req.getAmount() < totalAmountWithTax)
                        return Future.failedFuture(
                                new CustomException("Insufficient balance. Required: " + totalAmountWithTax));

                    req.setAmount(totalAmountWithTax);
                    req.setPaymentStatus("success");

                    return transactionRepository.updateTransaction(req);
                })
                .map(updatedTx -> {
                    span.setAttribute("transaction.id", updatedTx.getTransactionId());
                    invalidateCache(updatedTx.getTransactionId());
                    tracingMetrics.completeSpanSuccess(ctx, "update", "Transaction updated successfully");
                    return ApiResponse.success("Transaction updated successfully", TransactionResponse.from(updatedTx));
                })
                .recover(err -> {
                    logger.error("Failed to update transaction", err);
                    tracingMetrics.completeSpanError(ctx, "update", err.getMessage());
                    if (err instanceof CustomException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse
                                    .<TransactionResponse>error("Failed to update transaction: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponseDeleteAt>> trash(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.trash",
                Attributes.builder().put("id", id).build());

        return transactionRepository.trashTransaction(id)
                .compose(data -> {
                    if (data == null)
                        return Future.failedFuture(new NotFoundException("Transaction not found"));
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "trash", "Success");
                    return Future.succeededFuture(
                            ApiResponse.success("Transaction trashed", TransactionResponseDeleteAt.from(data)));
                })
                .recover(err -> {
                    logger.error("Failed to trash transaction", err);
                    tracingMetrics.completeSpanError(ctx, "trash", err.getMessage());
                    if (err instanceof NotFoundException)
                        return Future.failedFuture(err);
                    return Future.succeededFuture(
                            ApiResponse.<TransactionResponseDeleteAt>error("Failed to trash: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponseDeleteAt>> restore(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.restore",
                Attributes.builder().put("id", id).build());

        return transactionRepository.findByTrashed(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Transaction not found"));
                    }
                    return transactionRepository.restoreTransaction(id);
                })
                .compose(data -> {
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "restore", "Success");
                    return Future.succeededFuture(
                            ApiResponse.success("Transaction restored", TransactionResponseDeleteAt.from(data)));
                })
                .recover(err -> {
                    logger.error("Failed to restore transaction", err);
                    tracingMetrics.completeSpanError(ctx, "restore", err.getMessage());
                    if (err instanceof NotFoundException)
                        return Future.failedFuture(err);
                    return Future.succeededFuture(
                            ApiResponse.<TransactionResponseDeleteAt>error("Failed to restore: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> deletePermanent(Long id) {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.deletePermanent",
                Attributes.builder().put("id", id).build());

        return transactionRepository.findByTrashed(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.failedFuture(new NotFoundException("Transaction not found"));
                    }
                    return transactionRepository.deleteTransactionPermanently(id);
                })
                .map(v -> {
                    invalidateCache(id);
                    tracingMetrics.completeSpanSuccess(ctx, "delete_permanent", "Success");
                    return ApiResponse.success("Transaction deleted permanently", true);
                })
                .recover(err -> {
                    logger.error("Failed to delete transaction", err);
                    tracingMetrics.completeSpanError(ctx, "delete_permanent", err.getMessage());
                    if (err instanceof NotFoundException)
                        return Future.failedFuture(err);
                    return Future.succeededFuture(ApiResponse.<Boolean>error("Failed to delete: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> restoreAll() {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.restoreAll");
        return transactionRepository.restoreAllTransactions()
                .compose(count -> {
                    if (count == 0) {
                        return Future.failedFuture(new NotFoundException("No trashed transactions found"));
                    }
                    tracingMetrics.completeSpanSuccess(ctx, "restore_all", "Success");
                    return Future.succeededFuture(ApiResponse.success("All transactions restored", count));
                })
                .recover(err -> {
                    logger.error("Failed to restore all transactions", err);
                    tracingMetrics.completeSpanError(ctx, "restore_all", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<Integer>error("Failed to restore all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> deleteAllPermanent() {
        TracingMetrics.TracingContext ctx = tracingMetrics.startSpan("TransactionService.deleteAllPermanent");
        return transactionRepository.deleteAllPermanentTransactions()
                .compose(count -> {
                    if (count == 0) {
                        return Future.failedFuture(new NotFoundException("No trashed transactions found"));
                    }
                    tracingMetrics.completeSpanSuccess(ctx, "delete_all", "Success");
                    return Future.succeededFuture(ApiResponse.success("All transactions deleted permanently", count));
                })
                .recover(err -> {
                    logger.error("Failed to delete all transactions", err);
                    tracingMetrics.completeSpanError(ctx, "delete_all", err.getMessage());
                    if (err instanceof NotFoundException) {
                        return Future.failedFuture(err);
                    }
                    return Future.succeededFuture(
                            ApiResponse.<Integer>error("Failed to delete all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<List<TransactionMonthlyAmountSuccess>>> getMonthlyAmountSuccess(
            MonthAmountTransactionRequest req) {
        String cacheKey = String.format("transaction:report:monthly_success:%d:%d", req.getYear(), req.getMonth());
        return fetchStats(cacheKey, transactionRepository.getMonthlyAmountTransactionSuccess(req),
                Function.identity(), TransactionMonthlyAmountSuccess.class, "report_monthly_success",
                "Monthly success amount fetched successfully");
    }

    public Future<ApiResponse<List<TransactionYearlyAmountSuccess>>> getYearlyAmountSuccess(int year) {
        String cacheKey = String.format("transaction:report:yearly_success:%d", year);
        return fetchStats(cacheKey, transactionRepository.getYearlyAmountTransactionSuccess(year),
                Function.identity(), TransactionYearlyAmountSuccess.class, "report_yearly_success",
                "Yearly success amount fetched successfully");
    }

    public Future<ApiResponse<List<TransactionMonthlyAmountFailed>>> getMonthlyAmountFailed(
            MonthAmountTransactionRequest req) {
        String cacheKey = String.format("transaction:report:monthly_failed:%d:%d", req.getYear(), req.getMonth());
        return fetchStats(cacheKey, transactionRepository.getMonthlyAmountTransactionFailed(req),
                Function.identity(), TransactionMonthlyAmountFailed.class, "report_monthly_failed",
                "Monthly failed amount fetched successfully");
    }

    public Future<ApiResponse<List<TransactionYearlyAmountFailed>>> getYearlyAmountFailed(int year) {
        String cacheKey = String.format("transaction:report:yearly_failed:%d", year);
        return fetchStats(cacheKey, transactionRepository.getYearlyAmountTransactionFailed(year),
                Function.identity(), TransactionYearlyAmountFailed.class, "report_yearly_failed",
                "Yearly failed amount fetched successfully");
    }

    public Future<ApiResponse<List<TransactionMonthlyMethod>>> getMonthlyMethodsSuccess(
            MonthMethodTransactionRequest req) {
        String cacheKey = String.format("transaction:report:monthly_methods_success:%d:%d", req.getYear(),
                req.getMonth());
        return fetchStats(cacheKey, transactionRepository.getMonthlyTransactionMethodsSuccess(req),
                Function.identity(), TransactionMonthlyMethod.class, "report_monthly_methods_success",
                "Monthly success methods fetched successfully");
    }

    public Future<ApiResponse<List<TransactionMonthlyMethod>>> getMonthlyMethodsFailed(
            MonthMethodTransactionRequest req) {
        String cacheKey = String.format("transaction:report:monthly_methods_failed:%d:%d", req.getYear(),
                req.getMonth());
        return fetchStats(cacheKey, transactionRepository.getMonthlyTransactionMethodsFailed(req),
                Function.identity(), TransactionMonthlyMethod.class, "report_monthly_methods_failed",
                "Monthly failed methods fetched successfully");
    }

    public Future<ApiResponse<List<TransactionYearMethod>>> getYearlyMethodsSuccess(int year) {
        String cacheKey = String.format("transaction:report:yearly_methods_success:%d", year);
        return fetchStats(cacheKey, transactionRepository.getYearlyTransactionMethodsSuccess(year),
                Function.identity(), TransactionYearMethod.class, "report_yearly_methods_success",
                "Yearly success methods fetched successfully");
    }

    public Future<ApiResponse<List<TransactionYearMethod>>> getYearlyMethodsFailed(int year) {
        String cacheKey = String.format("transaction:report:yearly_methods_failed:%d", year);
        return fetchStats(cacheKey, transactionRepository.getYearlyTransactionMethodsFailed(year),
                Function.identity(), TransactionYearMethod.class, "report_yearly_methods_failed",
                "Yearly failed methods fetched successfully");
    }

    public Future<ApiResponse<List<TransactionMonthlyAmountSuccess>>> getMonthlyAmountSuccessByMerchant(
            MonthAmountTransactionMerchant req) {
        String cacheKey = String.format("transaction:report:m_monthly_success:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());
        return fetchStats(cacheKey, transactionRepository.getMonthlyAmountTransactionSuccessByMerchant(req),
                Function.identity(), TransactionMonthlyAmountSuccess.class, "report_m_monthly_success",
                "Merchant monthly success amount fetched successfully");
    }

    public Future<ApiResponse<List<TransactionYearlyAmountSuccess>>> getYearlyAmountSuccessByMerchant(
            YearAmountTransactionMerchant req) {
        String cacheKey = String.format("transaction:report:m_yearly_success:%d:%d", req.getMerchantId(),
                req.getYear());
        return fetchStats(cacheKey, transactionRepository.getYearlyAmountTransactionSuccessByMerchant(req),
                Function.identity(), TransactionYearlyAmountSuccess.class, "report_m_yearly_success",
                "Merchant yearly success amount fetched successfully");
    }

    public Future<ApiResponse<List<TransactionMonthlyAmountFailed>>> getMonthlyAmountFailedByMerchant(
            MonthAmountTransactionMerchant req) {
        String cacheKey = String.format("transaction:report:m_monthly_failed:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());
        return fetchStats(cacheKey, transactionRepository.getMonthlyAmountTransactionFailedByMerchant(req),
                Function.identity(), TransactionMonthlyAmountFailed.class, "report_m_monthly_failed",
                "Merchant monthly failed amount fetched successfully");
    }

    public Future<ApiResponse<List<TransactionYearlyAmountFailed>>> getYearlyAmountFailedByMerchant(
            YearAmountTransactionMerchant req) {
        String cacheKey = String.format("transaction:report:m_yearly_failed:%d:%d", req.getMerchantId(),
                req.getYear());
        return fetchStats(cacheKey, transactionRepository.getYearlyAmountTransactionFailedByMerchant(req),
                Function.identity(), TransactionYearlyAmountFailed.class, "report_m_yearly_failed",
                "Merchant yearly failed amount fetched successfully");
    }

    public Future<ApiResponse<List<TransactionMonthlyMethod>>> getMonthlyMethodsByMerchantSuccess(
            MonthMethodTransactionMerchantRequest req) {
        String cacheKey = String.format("transaction:report:m_monthly_methods_success:%d:%d:%d",
                req.getMerchantId(), req.getYear(), req.getMonth());
        return fetchStats(cacheKey, transactionRepository.getMonthlyTransactionMethodsByMerchantSuccess(req),
                Function.identity(), TransactionMonthlyMethod.class, "report_m_monthly_methods_success",
                "Merchant monthly success methods fetched successfully");
    }

    public Future<ApiResponse<List<TransactionMonthlyMethod>>> getMonthlyMethodsByMerchantFailed(
            MonthMethodTransactionMerchantRequest req) {
        String cacheKey = String.format("transaction:report:m_monthly_methods_failed:%d:%d:%d",
                req.getMerchantId(), req.getYear(), req.getMonth());
        return fetchStats(cacheKey, transactionRepository.getMonthlyTransactionMethodsByMerchantFailed(req),
                Function.identity(), TransactionMonthlyMethod.class, "report_m_monthly_methods_failed",
                "Merchant monthly failed methods fetched successfully");
    }

    public Future<ApiResponse<List<TransactionYearMethod>>> getYearlyMethodsByMerchantSuccess(
            YearMethodTransactionMerchantRequest req) {
        String cacheKey = String.format("transaction:report:m_yearly_methods_success:%d:%d", req.getMerchantId(),
                req.getYear());
        return fetchStats(cacheKey, transactionRepository.getYearlyTransactionMethodsByMerchantSuccess(req),
                Function.identity(), TransactionYearMethod.class, "report_m_yearly_methods_success",
                "Merchant yearly success methods fetched successfully");
    }

    public Future<ApiResponse<List<TransactionYearMethod>>> getYearlyMethodsByMerchantFailed(
            YearMethodTransactionMerchantRequest req) {
        String cacheKey = String.format("transaction:report:m_yearly_methods_failed:%d:%d", req.getMerchantId(),
                req.getYear());
        return fetchStats(cacheKey, transactionRepository.getYearlyTransactionMethodsByMerchantFailed(req),
                Function.identity(), TransactionYearMethod.class, "report_m_yearly_methods_failed",
                "Merchant yearly failed methods fetched successfully");
    }

    private <T, R> Future<ApiResponse<List<R>>> fetchStats(String cacheKey, Future<List<T>> dbFuture,
            Function<T, R> mapper, Class<R> responseType, String spanName, String successMessage) {

        TracingMetrics.TracingContext tracingContext = tracingMetrics
                .startSpan("TransactionStatsService." + spanName);

        return redisService.getJsonList(cacheKey, responseType)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        tracingMetrics.completeSpanSuccess(tracingContext, spanName, "Data from cache");
                        return Future.succeededFuture(cached);
                    }
                    return dbFuture.map(dbResults -> {
                        List<R> responseList = dbResults.stream().map(mapper).collect(Collectors.toList());
                        redisService.setJsonList(cacheKey, responseList, Duration.ofHours(6))
                                .onFailure(err -> tracingMetrics.completeSpanError(tracingContext, spanName,
                                        "Data fetched but cache failed"))
                                .onSuccess(v -> tracingMetrics.completeSpanSuccess(tracingContext, spanName,
                                        "Data fetched from DB and cached"));
                        return responseList;
                    });
                })
                .map(results -> ApiResponse.success(successMessage, results))
                .recover(
                        err -> Future.succeededFuture(ApiResponse.error("Failed to fetch stats: " + err.getMessage())));
    }

    private <T, R> Future<ApiResponsePagination<List<R>>> fetchPaginatedTransactions(T req, String cachePrefix,
            int page, int pageSize, String keyword,
            Function<T, Future<PagedResult<Transaction>>> dbFetcher,
            Function<Transaction, R> responseMapper, TracingMetrics.TracingContext tracingContext,
            String spanName, String successMessage) {

        Span span = Span.fromContext(tracingContext.getContext());
        String cacheKey = String.format("%s:page:%d:search:%s", cachePrefix, page, keyword);

        return redisService.get(cacheKey)
                .compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        span.setAttribute("cache.hit", true);
                        try {
                            JsonObject json = new JsonObject(cached);
                            int totalRecords = json.getInteger("totalRecords");
                            int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

                            List<R> data = json.getJsonArray("data").stream()
                                    .map(obj -> responseMapper.apply(Transaction.fromJson((JsonObject) obj)))
                                    .toList();

                            tracingMetrics.completeSpanSuccess(tracingContext, spanName,
                                    "Transactions fetched from cache");
                            return Future.succeededFuture(new ApiResponsePagination<>("success", successMessage, data,
                                    new PaginationMeta(page + 1, pageSize, totalPages, totalRecords)));
                        } catch (Exception e) {
                            logger.warn("Failed to parse cached paginated transactions: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("cache.hit", false);
                    return dbFetcher.apply(req)
                            .map(result -> {
                                JsonObject jsonToCache = new JsonObject()
                                        .put("totalRecords", result.getTotalRecords())
                                        .put("data", new JsonArray(
                                                result.getData().stream().map(Transaction::toJson).toList()));

                                redisService.set(cacheKey, jsonToCache.encode(), Duration.ofMinutes(5))
                                        .onFailure(err -> logger.warn("Failed to cache {}: {}", cachePrefix,
                                                err.getMessage()));

                                span.setAttribute("records.count", result.getData().size());
                                span.setAttribute("records.total_records", result.getTotalRecords());
                                tracingMetrics.completeSpanSuccess(tracingContext, spanName, successMessage);

                                return mapPagination(result, page, pageSize, responseMapper, successMessage);
                            });
                })
                .recover(throwable -> {
                    logger.error("Failed to fetch paginated transactions for {}", cachePrefix, throwable);
                    tracingMetrics.completeSpanError(tracingContext, spanName, throwable.getMessage());
                    return Future.succeededFuture(ApiResponsePagination
                            .<List<R>>error("Failed to fetch data: " + throwable.getMessage()));
                });
    }

    private Future<ApiResponse<TransactionResponse>> fetchTransactionFromDatabase(Long id,
            TracingMetrics.TracingContext tracingContext) {
        Span span = Span.fromContext(tracingContext.getContext());

        return transactionRepository.getTransactionById(id)
                .compose(data -> {
                    if (data == null)
                        return Future.failedFuture(new NotFoundException("Transaction not found"));

                    span.setAttribute("transaction.id", data.getTransactionId());

                    redisService.setJson("transaction:" + id, data.toJson(), Duration.ofMinutes(60))
                            .onFailure(err -> logger.warn("Failed to cache transaction {}: {}", id, err.getMessage()));

                    return Future
                            .succeededFuture(
                                    ApiResponse.success("Data fetched successfully", TransactionResponse.from(data)));
                });
    }

    private <R> ApiResponsePagination<List<R>> mapPagination(PagedResult<Transaction> result, int page, int pageSize,
            Function<Transaction, R> mapper, String message) {
        int totalRecords = result.getTotalRecords();
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        List<R> data = result.getData().stream().map(mapper).toList();

        return new ApiResponsePagination<>("success", message, data,
                new PaginationMeta(page + 1, pageSize, totalPages, totalRecords));
    }

    private void invalidateCache(Long id) {
        if (id != null) {
            redisService.delete("transaction:" + id)
                    .onSuccess(deleted -> {
                        if (deleted > 0)
                            logger.debug("Cache transaction:{} invalidated successfully", id);
                    })
                    .onFailure(err -> logger.warn("Failed to invalidate cache for transaction {}: {}", id,
                            err.getMessage()));
        }
    }
}