package com.sanedge.example_crud.service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.vertx.core.json.Json;

public class TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransactionService(
            TransactionRepository transactionRepository,
            MerchantRepository merchantRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RedisService redisService,
            TracingMetrics tracingMetrics) {
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.redisService = redisService;
        this.tracingMetrics = tracingMetrics;
    }

    public Future<ApiResponsePagination<List<TransactionResponse>>> getAll(FindAllTransactionRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.getAll");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching transactions | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("transactions:page:%d:search:%s", page, keyword);
        ObjectMapper mapper = new ObjectMapper();

        return redisService.get(cacheKey)
                .<ApiResponsePagination<List<TransactionResponse>>>compose(cachedResult -> {
                    if (cachedResult != null && !cachedResult.isEmpty()) {
                        logger.info("Transaction cache hit for key: {}", cacheKey);
                        span.setAttribute("cache.hit", true);
                        try {
                            PagedResult<Transaction> result = mapper.readValue(
                                    cachedResult,
                                    new TypeReference<PagedResult<Transaction>>() {
                                    });

                            ApiResponsePagination<List<TransactionResponse>> response = mapToPagedResponse(result, req);
                            return Future.succeededFuture(response);
                        } catch (Exception e) {
                            logger.warn("Failed to parse transaction cache: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("cache.hit", false);
                    return transactionRepository.getTransactions(req)
                            .map(result -> {
                                redisService.set(cacheKey, Json.encode(result), Duration.ofMinutes(10))
                                        .onFailure(err -> logger.warn("Failed to set transaction cache: {}",
                                                err.getMessage()));

                                return mapToPagedResponse(result, req);
                            });
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total_records", response.pagination().totalRecords());
                    tracingMetrics.completeSpanSuccess(tracingContext, "get_all", "Success");
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch transactions", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_all", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination
                                    .<List<TransactionResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<TransactionResponse>>> getActive(FindAllTransactionRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.getActive");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching active transactions | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("transactions:active:page:%d:search:%s", page, keyword);
        ObjectMapper mapper = new ObjectMapper();

        return redisService.get(cacheKey)
                .<ApiResponsePagination<List<TransactionResponse>>>compose(cachedResult -> {
                    if (cachedResult != null && !cachedResult.isEmpty()) {
                        logger.info("Active transaction cache hit for key: {}", cacheKey);
                        span.setAttribute("cache.hit", true);
                        try {
                            PagedResult<Transaction> result = mapper.readValue(
                                    cachedResult,
                                    new TypeReference<PagedResult<Transaction>>() {
                                    });

                            ApiResponsePagination<List<TransactionResponse>> response = mapToPagedResponse(result, req);
                            return Future.succeededFuture(response);
                        } catch (Exception e) {
                            logger.warn("Failed to parse active transaction cache: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("cache.hit", false);
                    return transactionRepository.getTransactionsActive(req)
                            .map(result -> {
                                redisService.set(cacheKey, Json.encode(result), Duration.ofMinutes(10))
                                        .onFailure(err -> logger.warn("Failed to set active transaction cache: {}",
                                                err.getMessage()));

                                return mapToPagedResponse(result, req);
                            });
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total_records", response.pagination().totalRecords());
                    tracingMetrics.completeSpanSuccess(tracingContext, "get_active", "Success");
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch active transactions", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_active", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination
                                    .<List<TransactionResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<TransactionResponseDeleteAt>>> getTrashed(FindAllTransactionRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.getTrashed");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        logger.info("Fetching trashed transactions | search={}, page={}, pageSize={}", keyword, page, pageSize);

        String cacheKey = String.format("transactions:trashed:page:%d:search:%s", page, keyword);
        ObjectMapper mapper = new ObjectMapper();

        return redisService.get(cacheKey)
                .<ApiResponsePagination<List<TransactionResponseDeleteAt>>>compose(cachedResult -> {
                    if (cachedResult != null && !cachedResult.isEmpty()) {
                        logger.info("Trashed transaction cache hit for key: {}", cacheKey);
                        span.setAttribute("cache.hit", true);
                        try {
                            PagedResult<Transaction> result = mapper.readValue(
                                    cachedResult,
                                    new TypeReference<PagedResult<Transaction>>() {
                                    });

                            ApiResponsePagination<List<TransactionResponseDeleteAt>> response = mapToPagedResponseDeleteAt(
                                    result, req);
                            return Future.succeededFuture(response);
                        } catch (Exception e) {
                            logger.warn("Failed to parse trashed transaction cache: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("cache.hit", false);
                    return transactionRepository.getTransactionsTrashed(req)
                            .map(result -> {
                                redisService.set(cacheKey, Json.encode(result), Duration.ofMinutes(10))
                                        .onFailure(err -> logger.warn("Failed to set trashed transaction cache: {}",
                                                err.getMessage()));

                                return mapToPagedResponseDeleteAt(result, req);
                            });
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total_records", response.pagination().totalRecords());
                    tracingMetrics.completeSpanSuccess(tracingContext, "get_trashed", "Success");
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch trashed transactions", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_trashed", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination.<List<TransactionResponseDeleteAt>>error(
                                    "Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponsePagination<List<TransactionResponse>>> getByMerchant(
            FindAllTransactionByMerchantRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.getByMerchant");
        Span span = Span.fromContext(tracingContext.getContext());

        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(keyword);

        span.setAttribute("merchant.id", req.getMerchantId());

        logger.info("Fetching transactions by merchant | merchantId={}, search={}, page={}, pageSize={}",
                req.getMerchantId(), keyword, page, pageSize);

        String cacheKey = String.format("transactions:merchant:%d:page:%d:search:%s", req.getMerchantId(), page,
                keyword);
        ObjectMapper mapper = new ObjectMapper();

        return redisService.get(cacheKey)
                .<ApiResponsePagination<List<TransactionResponse>>>compose(cachedResult -> {
                    if (cachedResult != null && !cachedResult.isEmpty()) {
                        logger.info("Merchant transaction cache hit for key: {}", cacheKey);
                        span.setAttribute("cache.hit", true);
                        try {
                            PagedResult<Transaction> result = mapper.readValue(
                                    cachedResult,
                                    new TypeReference<PagedResult<Transaction>>() {
                                    });

                            ApiResponsePagination<List<TransactionResponse>> response = mapToPagedResponse(result,
                                    req.getPage(), req.getPageSize());
                            return Future.succeededFuture(response);
                        } catch (Exception e) {
                            logger.warn("Failed to parse merchant transaction cache: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("cache.hit", false);
                    return transactionRepository.getTransactionByMerchant(req)
                            .map(result -> {
                                redisService.set(cacheKey, Json.encode(result), Duration.ofMinutes(10))
                                        .onFailure(err -> logger.warn("Failed to set merchant transaction cache: {}",
                                                err.getMessage()));

                                return mapToPagedResponse(result, req.getPage(), req.getPageSize());
                            });
                })
                .map(response -> {
                    span.setAttribute("records.count", response.data().size());
                    span.setAttribute("records.total_records", response.pagination().totalRecords());
                    tracingMetrics.completeSpanSuccess(tracingContext, "get_by_merchant", "Success");
                    return response;
                })
                .recover(err -> {
                    logger.error("Failed to fetch transactions by merchant", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_by_merchant", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponsePagination
                                    .<List<TransactionResponse>>error("Failed to fetch data: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponse>> getById(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
                "TransactionService.getById",
                io.opentelemetry.api.common.Attributes.builder().put("id", id).build());
        Span span = Span.fromContext(tracingContext.getContext());

        logger.info("Fetching transaction by id: {}", id);
        String cacheKey = "transaction:" + id;

        return redisService.get(cacheKey)
                .<ApiResponse<TransactionResponse>>compose(cached -> {
                    if (cached != null && !cached.isEmpty()) {
                        span.setAttribute("cache.hit", true);
                        try {
                            Transaction data = Json.decodeValue(cached, Transaction.class);
                            return Future.succeededFuture(
                                    ApiResponse.success("Data from cache", TransactionResponse.from(data)));
                        } catch (Exception e) {
                            logger.warn("Cache parse error: {}", e.getMessage());
                        }
                    }

                    span.setAttribute("cache.hit", false);
                    return transactionRepository.getTransactionById(id)
                            .<ApiResponse<TransactionResponse>>map(data -> {
                                if (data == null) {
                                    throw new NotFoundException("Transaction not found");
                                }
                                redisService.set(cacheKey, Json.encode(data), Duration.ofMinutes(60))
                                        .onFailure(err -> logger.warn("Cache set failed: {}", err.getMessage()));
                                return ApiResponse.success("Data fetched successfully", TransactionResponse.from(data));
                            });
                })
                .recover(err -> {
                    logger.error("Failed to fetch by id", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_by_id", err.getMessage());
                    return Future.succeededFuture(ApiResponse.<TransactionResponse>error(err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponse>> getByOrderId(Long orderId) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.getByOrderId");

        return transactionRepository.getTransactionByOrderId(orderId)
                .map(data -> {
                    if (data == null) {
                        throw new NotFoundException("Transaction not found for order: " + orderId);
                    }
                    tracingMetrics.completeSpanSuccess(tracingContext, "get_by_order", "Success");
                    return ApiResponse.success("Data fetched successfully", TransactionResponse.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to fetch by order id", err);
                    tracingMetrics.completeSpanError(tracingContext, "get_by_order", err.getMessage());
                    return Future.succeededFuture(ApiResponse.<TransactionResponse>error(err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponse>> createTransaction(CreateTransactionRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
                "TransactionService.createTransaction",
                Attributes.builder()
                        .put("order.id", req.getOrderID())
                        .put("merchant.id", req.getMerchantID())
                        .build());
        Span span = Span.fromContext(tracingContext.getContext());

        logger.info("Creating transaction for order: {}", req.getOrderID());

        return merchantRepository.getMerchantById(req.getMerchantID().longValue())
                .<Order>compose(merchant -> {
                    if (merchant == null) {
                        return Future.failedFuture("Merchant not found");
                    }
                    return orderRepository.getOrderById(req.getOrderID().longValue());
                })
                .<List<OrderItem>>compose(order -> {
                    if (order == null) {
                        return Future.failedFuture("Order not found");
                    }
                    return orderItemRepository.getOrderItemsByOrder(req.getOrderID().longValue());
                })
                .<Transaction>compose(orderItems -> {
                    if (orderItems == null || orderItems.isEmpty()) {
                        return Future.failedFuture("Order items not found");
                    }

                    for (OrderItem item : orderItems) {
                        if (item.getQuantity() <= 0) {
                            return Future.failedFuture(new CustomException("Invalid order item quantity"));
                        }
                    }

                    int totalAmount = 0;
                    for (OrderItem item : orderItems) {
                        totalAmount += item.getPrice() * item.getQuantity();
                    }

                    int ppn = totalAmount * 11 / 100;
                    int totalAmountWithTax = totalAmount + ppn;

                    if (req.getAmount() < totalAmountWithTax) {
                        return Future.failedFuture(
                                new CustomException("Insufficient balance. Required: " + totalAmountWithTax));
                    }

                    req.setAmount(totalAmountWithTax);
                    req.setPaymentStatus("success");

                    return transactionRepository.createTransaction(req);
                })
                .map(transaction -> {
                    span.setAttribute("transaction.id", transaction.getTransactionId());
                    tracingMetrics.completeSpanSuccess(tracingContext, "create", "Transaction created successfully");
                    return ApiResponse.success("Transaction created successfully",
                            TransactionResponse.from(transaction));
                })
                .recover(err -> {
                    logger.error("Failed to create transaction", err);
                    tracingMetrics.completeSpanError(tracingContext, "create", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse
                                    .<TransactionResponse>error("Failed to create transaction: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponse>> updateTransaction(UpdateTransactionRequest req) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan(
                "TransactionService.updateTransaction",
                Attributes.builder()
                        .put("transaction.id", req.getTransactionID())
                        .put("order.id", req.getOrderID())
                        .put("merchant.id", req.getMerchantID())
                        .build());
        Span span = Span.fromContext(tracingContext.getContext());

        logger.info("Updating transaction: {}", req.getTransactionID());

        return transactionRepository.getTransactionById(req.getTransactionID().longValue())
                .<Merchant>compose(existingTx -> {
                    if (existingTx == null) {
                        return Future.failedFuture("Transaction not found");
                    }

                    String status = existingTx.getStatus() != null ? existingTx.getStatus().toString()
                            : existingTx.getStatus().toString();

                    if ("success".equals(status) || "refunded".equals(status)) {
                        return Future.failedFuture("Payment status cannot be modified");
                    }

                    return merchantRepository.getMerchantById(req.getMerchantID().longValue());
                })
                .<Order>compose(merchant -> {
                    if (merchant == null) {
                        return Future.failedFuture("Merchant not found");
                    }
                    return orderRepository.getOrderById(req.getOrderID().longValue());
                })
                .<List<OrderItem>>compose(order -> {
                    if (order == null) {
                        return Future.failedFuture("Order not found");
                    }
                    return orderItemRepository.getOrderItemsByOrder(req.getOrderID().longValue());
                })
                .<Transaction>compose(orderItems -> {
                    if (orderItems == null || orderItems.isEmpty()) {
                        return Future.failedFuture("Order items not found");
                    }

                    for (OrderItem item : orderItems) {
                        if (item.getQuantity() <= 0) {
                            return Future.failedFuture("Invalid order item quantity");
                        }
                    }

                    int totalAmount = 0;
                    for (OrderItem item : orderItems) {
                        totalAmount += item.getPrice() * item.getQuantity();
                    }

                    int ppn = totalAmount * 11 / 100;
                    int totalAmountWithTax = totalAmount + ppn;

                    if (req.getAmount() < totalAmountWithTax) {
                        return Future.failedFuture("Insufficient balance. Required: " + totalAmountWithTax);
                    }

                    req.setAmount(totalAmountWithTax);
                    req.setPaymentStatus("success");

                    return transactionRepository.updateTransaction(req);
                })
                .map(updatedTx -> {
                    span.setAttribute("transaction.id", updatedTx.getTransactionId());
                    tracingMetrics.completeSpanSuccess(tracingContext, "update", "Transaction updated successfully");

                    return ApiResponse.success("Transaction updated successfully", TransactionResponse.from(updatedTx));
                })
                .recover(err -> {
                    logger.error("Failed to update transaction", err);
                    tracingMetrics.completeSpanError(tracingContext, "update", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse
                                    .<TransactionResponse>error("Failed to update transaction: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponseDeleteAt>> trash(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.trash");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return transactionRepository.trashTransaction(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.<Transaction>failedFuture(new NotFoundException("Transaction not found"));
                    }
                    return redisService.delete("transaction:" + id).map(data);
                })
                .map(data -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "trash", "Success");
                    return ApiResponse.success("Transaction trashed", TransactionResponseDeleteAt.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to trash transaction", err);
                    tracingMetrics.completeSpanError(tracingContext, "trash", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<TransactionResponseDeleteAt>error("Failed to trash: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<TransactionResponseDeleteAt>> restore(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.restore");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return transactionRepository.restoreTransaction(id)
                .compose(data -> {
                    if (data == null) {
                        return Future.<Transaction>failedFuture(new NotFoundException("Transaction not found"));
                    }
                    return redisService.delete("transaction:" + id).map(data);
                })
                .map(data -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "restore", "Success");
                    return ApiResponse.success("Transaction restored", TransactionResponseDeleteAt.from(data));
                })
                .recover(err -> {
                    logger.error("Failed to restore transaction", err);
                    tracingMetrics.completeSpanError(tracingContext, "restore", err.getMessage());
                    return Future.succeededFuture(
                            ApiResponse.<TransactionResponseDeleteAt>error("Failed to restore: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Boolean>> deletePermanent(Long id) {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.deletePermanent");
        Span span = Span.fromContext(tracingContext.getContext());
        span.setAttribute("id", id);

        return transactionRepository.deleteTransactionPermanently(id)
                .compose(v -> redisService.delete("transaction:" + id).map(v))
                .map(v -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "delete_permanent", "Success");
                    return ApiResponse.success("Transaction deleted permanently", true);
                })
                .recover(err -> {
                    logger.error("Failed to delete transaction", err);
                    tracingMetrics.completeSpanError(tracingContext, "delete_permanent", err.getMessage());
                    return Future.succeededFuture(ApiResponse.<Boolean>error("Failed to delete: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> restoreAll() {
        TracingMetrics.TracingContext tracingContext = tracingMetrics.startSpan("TransactionService.restoreAll");
        return transactionRepository.restoreAllTransactions()
                .map(count -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "restore_all", "Success");
                    return ApiResponse.success("All transactions restored", count);
                })
                .recover(err -> {
                    logger.error("Failed to restore all transactions", err);
                    tracingMetrics.completeSpanError(tracingContext, "restore_all", err.getMessage());
                    return Future
                            .succeededFuture(ApiResponse.<Integer>error("Failed to restore all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<Integer>> deleteAllPermanent() {
        TracingMetrics.TracingContext tracingContext = tracingMetrics
                .startSpan("TransactionService.deleteAllPermanent");
        return transactionRepository.deleteAllPermanentTransactions()
                .map(count -> {
                    tracingMetrics.completeSpanSuccess(tracingContext, "delete_all", "Success");
                    return ApiResponse.success("All transactions deleted permanently", count);
                })
                .recover(err -> {
                    logger.error("Failed to delete all transactions", err);
                    tracingMetrics.completeSpanError(tracingContext, "delete_all", err.getMessage());
                    return Future
                            .succeededFuture(ApiResponse.<Integer>error("Failed to delete all: " + err.getMessage()));
                });
    }

    public Future<ApiResponse<List<TransactionMonthlyAmountSuccess>>> getMonthlyAmountSuccess(
            MonthAmountTransactionRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("TransactionService.getMonthlySuccess");
        String cacheKey = String.format("transaction:report:monthly_success:%d:%d", req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getMonthlyAmountTransactionSuccess(req),
                        new TypeReference<List<TransactionMonthlyAmountSuccess>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionYearlyAmountSuccess>>> getYearlyAmountSuccess(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getYearlyAmountSuccess");
        String cacheKey = String.format("transaction:report:yearly_success:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getYearlyAmountTransactionSuccess(year),
                        new TypeReference<List<TransactionYearlyAmountSuccess>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionMonthlyAmountFailed>>> getMonthlyAmountFailed(
            MonthAmountTransactionRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getMonthlyAmountFailed");
        String cacheKey = String.format("transaction:report:monthly_failed:%d:%d", req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getMonthlyAmountTransactionFailed(req),
                        new TypeReference<List<TransactionMonthlyAmountFailed>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionYearlyAmountFailed>>> getYearlyAmountFailed(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics.startSpan("TransactionService.getYearlyAmountFailed");
        String cacheKey = String.format("transaction:report:yearly_failed:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getYearlyAmountTransactionFailed(year),
                        new TypeReference<List<TransactionYearlyAmountFailed>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionMonthlyMethod>>> getMonthlyMethodsSuccess(
            MonthMethodTransactionRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getMonthlyMethodsSuccess");
        String cacheKey = String.format("transaction:report:monthly_methods_success:%d:%d", req.getYear(),
                req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getMonthlyTransactionMethodsSuccess(req),
                        new TypeReference<List<TransactionMonthlyMethod>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionMonthlyMethod>>> getMonthlyMethodsFailed(
            MonthMethodTransactionRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getMonthlyMethodsFailed");
        String cacheKey = String.format("transaction:report:monthly_methods_failed:%d:%d", req.getYear(),
                req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getMonthlyTransactionMethodsFailed(req),
                        new TypeReference<List<TransactionMonthlyMethod>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionYearMethod>>> getYearlyMethodsSuccess(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getYearlyMethodsSuccess");
        String cacheKey = String.format("transaction:report:yearly_methods_success:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getYearlyTransactionMethodsSuccess(year),
                        new TypeReference<List<TransactionYearMethod>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionYearMethod>>> getYearlyMethodsFailed(int year) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getYearlyMethodsFailed");
        String cacheKey = String.format("transaction:report:yearly_methods_failed:%d", year);

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getYearlyTransactionMethodsFailed(year),
                        new TypeReference<List<TransactionYearMethod>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionMonthlyAmountSuccess>>> getMonthlyAmountSuccessByMerchant(
            MonthAmountTransactionMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getMonthlyAmountSuccessByMerchant");
        String cacheKey = String.format("transaction:report:m_monthly_success:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getMonthlyAmountTransactionSuccessByMerchant(req),
                        new TypeReference<List<TransactionMonthlyAmountSuccess>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionYearlyAmountSuccess>>> getYearlyAmountSuccessByMerchant(
            YearAmountTransactionMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getYearlyAmountSuccessByMerchant");
        String cacheKey = String.format("transaction:report:m_yearly_success:%d:%d", req.getMerchantId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getYearlyAmountTransactionSuccessByMerchant(req),
                        new TypeReference<List<TransactionYearlyAmountSuccess>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionMonthlyAmountFailed>>> getMonthlyAmountFailedByMerchant(
            MonthAmountTransactionMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getMonthlyAmountFailedByMerchant");
        String cacheKey = String.format("transaction:report:m_monthly_failed:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getMonthlyAmountTransactionFailedByMerchant(req),
                        new TypeReference<List<TransactionMonthlyAmountFailed>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionYearlyAmountFailed>>> getYearlyAmountFailedByMerchant(
            YearAmountTransactionMerchant req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getYearlyAmountFailedByMerchant");
        String cacheKey = String.format("transaction:report:m_yearly_failed:%d:%d", req.getMerchantId(), req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getYearlyAmountTransactionFailedByMerchant(req),
                        new TypeReference<List<TransactionYearlyAmountFailed>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionMonthlyMethod>>> getMonthlyMethodsByMerchantSuccess(
            MonthMethodTransactionMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getMonthlyMethodsByMerchantSuccess");
        String cacheKey = String.format("transaction:report:m_monthly_methods_success:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getMonthlyTransactionMethodsByMerchantSuccess(req),
                        new TypeReference<List<TransactionMonthlyMethod>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionMonthlyMethod>>> getMonthlyMethodsByMerchantFailed(
            MonthMethodTransactionMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getMonthlyMethodsByMerchantFailed");
        String cacheKey = String.format("transaction:report:m_monthly_methods_failed:%d:%d:%d", req.getMerchantId(),
                req.getYear(), req.getMonth());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getMonthlyTransactionMethodsByMerchantFailed(req),
                        new TypeReference<List<TransactionMonthlyMethod>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionYearMethod>>> getYearlyMethodsByMerchantSuccess(
            YearMethodTransactionMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getYearlyMethodsByMerchantSuccess");
        String cacheKey = String.format("transaction:report:m_yearly_methods_success:%d:%d", req.getMerchantId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getYearlyTransactionMethodsByMerchantSuccess(req),
                        new TypeReference<List<TransactionYearMethod>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    public Future<ApiResponse<List<TransactionYearMethod>>> getYearlyMethodsByMerchantFailed(
            YearMethodTransactionMerchantRequest req) {
        TracingMetrics.TracingContext tracingCtx = tracingMetrics
                .startSpan("TransactionService.getYearlyMethodsByMerchantFailed");
        String cacheKey = String.format("transaction:report:m_yearly_methods_failed:%d:%d", req.getMerchantId(),
                req.getYear());

        return redisService.get(cacheKey)
                .compose(cached -> handleCacheOrRepo(cached, cacheKey,
                        () -> transactionRepository.getYearlyTransactionMethodsByMerchantFailed(req),
                        new TypeReference<List<TransactionYearMethod>>() {
                        }, tracingCtx, "report"))
                .recover(err -> handleReportError(tracingCtx, err));
    }

    private <T> Future<ApiResponse<T>> handleCacheOrRepo(String cached, String cacheKey,
            java.util.concurrent.Callable<Future<T>> repoCall, TypeReference<T> typeRef,
            TracingMetrics.TracingContext tracingCtx, String operation) {
        if (cached != null) {
            try {
                T data = objectMapper.readValue(cached, typeRef);
                return Future.succeededFuture(ApiResponse.success("Success", data));
            } catch (Exception e) {
                logger.warn("Cache parse error", e);
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
        logger.error("Report generation failed", err);
        tracingMetrics.completeSpanError(ctx, "report", err.getMessage());
        return Future.succeededFuture(ApiResponse.<T>error("Failed to generate report: " + err.getMessage()));
    }

    private ApiResponsePagination<List<TransactionResponse>> mapToPagedResponse(PagedResult<Transaction> result,
            FindAllTransactionRequest req) {
        return mapToPagedResponse(result, req.getPage(), req.getPageSize());
    }

    private ApiResponsePagination<List<TransactionResponse>> mapToPagedResponse(PagedResult<Transaction> result,
            int page, int pageSize) {
        List<TransactionResponse> data = result.getData().stream()
                .map(TransactionResponse::from)
                .collect(Collectors.toList());
        return new ApiResponsePagination<>(
                "success", "Data fetched", data,
                new PaginationMeta(page, pageSize,
                        (int) Math.ceil((double) result.getTotalRecords() / pageSize),
                        result.getTotalRecords()));
    }

    private ApiResponsePagination<List<TransactionResponseDeleteAt>> mapToPagedResponseDeleteAt(
            PagedResult<Transaction> result, FindAllTransactionRequest req) {
        List<TransactionResponseDeleteAt> data = result.getData().stream()
                .map(TransactionResponseDeleteAt::from)
                .collect(Collectors.toList());
        return new ApiResponsePagination<>(
                "success", "Data fetched", data,
                new PaginationMeta(req.getPage(), req.getPageSize(),
                        (int) Math.ceil((double) result.getTotalRecords() / req.getPageSize()),
                        result.getTotalRecords()));
    }
}