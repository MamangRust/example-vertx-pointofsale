package com.sanedge.example_crud.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.enums.PaymentStatus;
import com.sanedge.example_crud.model.transaction.Transaction;
import com.sanedge.example_crud.model.transaction.TransactionMonthlyAmountFailed;
import com.sanedge.example_crud.model.transaction.TransactionMonthlyAmountSuccess;
import com.sanedge.example_crud.model.transaction.TransactionMonthlyMethod;
import com.sanedge.example_crud.model.transaction.TransactionYearMethod;
import com.sanedge.example_crud.model.transaction.TransactionYearlyAmountFailed;
import com.sanedge.example_crud.model.transaction.TransactionYearlyAmountSuccess;

import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionRepository {
    private final Pool client;

    public Future<PagedResult<Transaction>> getTransactions(FindAllTransactionRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();

        return client
                .preparedQuery(
                        """
                                SELECT
                                    transaction_id, order_id, merchant_id, payment_method, amount, payment_status,
                                    created_at, updated_at,
                                    COUNT(*) OVER () AS total_count
                                FROM transactions
                                WHERE deleted_at IS NULL
                                  AND ($1::TEXT IS NULL OR payment_method ILIKE '%' || $1 || '%' OR payment_status ILIKE '%' || $1 || '%')
                                ORDER BY created_at DESC
                                LIMIT $2 OFFSET $3;
                                """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedTransactions);
    }

    public Future<PagedResult<Transaction>> getTransactionsActive(FindAllTransactionRequest req) {
        return getTransactions(req);
    }

    public Future<PagedResult<Transaction>> getTransactionsTrashed(FindAllTransactionRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();

        return client
                .preparedQuery(
                        """
                                SELECT
                                    transaction_id, order_id, merchant_id, payment_method, amount, payment_status,
                                    created_at, updated_at, deleted_at,
                                    COUNT(*) OVER () AS total_count
                                FROM transactions
                                WHERE deleted_at IS NOT NULL
                                  AND ($1::TEXT IS NULL OR payment_method ILIKE '%' || $1 || '%' OR payment_status ILIKE '%' || $1 || '%')
                                ORDER BY created_at DESC
                                LIMIT $2 OFFSET $3;
                                """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedTransactions);
    }

    public Future<PagedResult<Transaction>> getTransactionByMerchant(FindAllTransactionByMerchantRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();

        return client
                .preparedQuery(
                        """
                                SELECT
                                    transaction_id, order_id, merchant_id, payment_method, amount, payment_status,
                                    created_at, updated_at,
                                    COUNT(*) OVER () AS total_count
                                FROM transactions
                                WHERE deleted_at IS NULL
                                  AND ($1::TEXT IS NULL OR payment_method ILIKE '%' || $1 || '%' OR payment_status ILIKE '%' || $1 || '%')
                                  AND ($2::INT IS NULL OR merchant_id = $2)
                                ORDER BY created_at DESC
                                LIMIT $3 OFFSET $4;
                                """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getMerchantId(), req.getPageSize(), offset))
                .map(this::mapPagedTransactions);
    }

    public Future<Transaction> getTransactionById(Long transactionId) {
        return client
                .preparedQuery(
                        """
                                SELECT transaction_id, order_id, merchant_id, payment_method, amount, payment_status, created_at, updated_at
                                FROM transactions WHERE transaction_id = $1 AND deleted_at IS NULL;
                                """)
                .execute(Tuple.of(transactionId))
                .map(rows -> rows.iterator().hasNext() ? Transaction.fromRow(rows.iterator().next()) : null);
    }

    public Future<Transaction> getTransactionByOrderId(Long orderId) {
        return client
                .preparedQuery(
                        """
                                SELECT transaction_id, order_id, merchant_id, payment_method, amount, payment_status, created_at, updated_at
                                FROM transactions WHERE order_id = $1 AND deleted_at IS NULL;
                                """)
                .execute(Tuple.of(orderId))
                .map(rows -> rows.iterator().hasNext() ? Transaction.fromRow(rows.iterator().next()) : null);
    }

    public Future<Transaction> createTransaction(CreateTransactionRequest req) {
        return client
                .preparedQuery("""
                        INSERT INTO transactions (order_id, merchant_id, payment_method, amount, payment_status)
                        VALUES ($1, $2, $3, $4, $5)
                        RETURNING *;
                        """)
                .execute(Tuple.of(req.getOrderID(), req.getMerchantID(), req.getPaymentMethod(), req.getAmount(),
                        req.getPaymentStatus() != null ? req.getPaymentStatus() : PaymentStatus.PENDING.name()))
                .map(rows -> Transaction.fromRow(rows.iterator().next()));
    }

    public Future<Transaction> updateTransaction(UpdateTransactionRequest req) {
        return client
                .preparedQuery(
                        """
                                UPDATE transactions
                                SET merchant_id = $2, payment_method = $3, amount = $4, payment_status = $5, order_id = $6, updated_at = CURRENT_TIMESTAMP
                                WHERE transaction_id = $1 AND deleted_at IS NULL
                                RETURNING *;
                                """)
                .execute(Tuple.of(req.getTransactionID(), req.getMerchantID(), req.getPaymentMethod(), req.getAmount(),
                        req.getPaymentStatus(), req.getOrderID()))
                .map(rows -> rows.iterator().hasNext() ? Transaction.fromRow(rows.iterator().next()) : null);
    }

    public Future<Transaction> trashTransaction(Long transactionId) {
        return client
                .preparedQuery(
                        "UPDATE transactions SET deleted_at = current_timestamp WHERE transaction_id = $1 AND deleted_at IS NULL RETURNING *")
                .execute(Tuple.of(transactionId))
                .map(rows -> rows.iterator().hasNext() ? Transaction.fromRow(rows.iterator().next()) : null);
    }

    public Future<Transaction> restoreTransaction(Long transactionId) {
        return client
                .preparedQuery(
                        "UPDATE transactions SET deleted_at = NULL WHERE transaction_id = $1 AND deleted_at IS NOT NULL RETURNING *")
                .execute(Tuple.of(transactionId))
                .map(rows -> rows.iterator().hasNext() ? Transaction.fromRow(rows.iterator().next()) : null);
    }

    public Future<Void> deleteTransactionPermanently(Long transactionId) {
        return client
                .preparedQuery("DELETE FROM transactions WHERE transaction_id = $1 AND deleted_at IS NOT NULL")
                .execute(Tuple.of(transactionId))
                .mapEmpty();
    }

    public Future<Integer> restoreAllTransactions() {
        return client
                .preparedQuery("UPDATE transactions SET deleted_at = NULL WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }

    public Future<Integer> deleteAllPermanentTransactions() {
        return client
                .preparedQuery("DELETE FROM transactions WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }

    private Tuple getMonthlyTuple(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        LocalDate startPrev = start.minusYears(1);
        LocalDate endPrev = startPrev.withDayOfMonth(startPrev.lengthOfMonth());
        return Tuple.of(start, end, startPrev, endPrev);
    }

    public Future<List<TransactionMonthlyAmountSuccess>> getMonthlyAmountTransactionSuccess(
            MonthAmountTransactionRequest req) {
        return client
                .preparedQuery(
                        """
                                WITH monthly_data AS (
                                    SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year,
                                           EXTRACT(MONTH FROM t.created_at)::integer AS month,
                                           COUNT(*) AS total_success,
                                           COALESCE(SUM(t.amount), 0)::integer AS total_amount
                                    FROM transactions t
                                    WHERE t.deleted_at IS NULL AND t.payment_status = 'success'
                                      AND ((t.created_at >= $1 AND t.created_at <= $2) OR (t.created_at >= $3 AND t.created_at <= $4))
                                    GROUP BY EXTRACT(YEAR FROM t.created_at), EXTRACT(MONTH FROM t.created_at)
                                ), formatted_data AS (
                                    SELECT year::text, TO_CHAR(TO_DATE(month::text, 'MM'), 'Mon') AS month, total_success, total_amount FROM monthly_data
                                    UNION ALL SELECT EXTRACT(YEAR FROM $1::timestamp)::text, TO_CHAR($1::timestamp, 'Mon'), 0, 0
                                    WHERE NOT EXISTS (SELECT 1 FROM monthly_data WHERE year = EXTRACT(YEAR FROM $1::timestamp)::integer AND month = EXTRACT(MONTH FROM $1::timestamp)::integer)
                                    UNION ALL SELECT EXTRACT(YEAR FROM $3::timestamp)::text, TO_CHAR($3::timestamp, 'Mon'), 0, 0
                                    WHERE NOT EXISTS (SELECT 1 FROM monthly_data WHERE year = EXTRACT(YEAR FROM $3::timestamp)::integer AND month = EXTRACT(MONTH FROM $3::timestamp)::integer)
                                ) SELECT * FROM formatted_data ORDER BY year DESC, TO_DATE(month, 'Mon') DESC;
                                """)
                .execute(getMonthlyTuple(req.getYear(), req.getMonth()))
                .map(this::mapMonthlySuccess);
    }

    public Future<List<TransactionYearlyAmountSuccess>> getYearlyAmountTransactionSuccess(
            Integer year) {
        return client
                .preparedQuery(
                        """
                                WITH yearly_data AS (
                                    SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year, COUNT(*) AS total_success, COALESCE(SUM(t.amount), 0)::integer AS total_amount
                                    FROM transactions t
                                    WHERE t.deleted_at IS NULL AND t.payment_status = 'success'
                                      AND (EXTRACT(YEAR FROM t.created_at) = $1::integer OR EXTRACT(YEAR FROM t.created_at) = $1::integer - 1)
                                    GROUP BY EXTRACT(YEAR FROM t.created_at)
                                ), formatted_data AS (
                                    SELECT year::text, total_success::integer, total_amount::integer FROM yearly_data
                                    UNION ALL SELECT $1::text, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = $1::integer)
                                    UNION ALL SELECT ($1::integer - 1)::text, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = $1::integer - 1)
                                ) SELECT * FROM formatted_data ORDER BY year DESC;
                                """)
                .execute(Tuple.of(year))
                .map(this::mapYearlySuccess);
    }

    public Future<List<TransactionMonthlyAmountFailed>> getMonthlyAmountTransactionFailed(
            MonthAmountTransactionRequest req) {
        return client
                .preparedQuery(
                        """
                                WITH monthly_data AS (
                                    SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year,
                                           EXTRACT(MONTH FROM t.created_at)::integer AS month,
                                           COUNT(*) AS total_failed, COALESCE(SUM(t.amount), 0)::integer AS total_amount
                                    FROM transactions t
                                    WHERE t.deleted_at IS NULL AND t.payment_status = 'failed'
                                      AND ((t.created_at >= $1 AND t.created_at <= $2) OR (t.created_at >= $3 AND t.created_at <= $4))
                                    GROUP BY EXTRACT(YEAR FROM t.created_at), EXTRACT(MONTH FROM t.created_at)
                                ), formatted_data AS (
                                    SELECT year::text, TO_CHAR(TO_DATE(month::text, 'MM'), 'Mon') AS month, total_failed, total_amount FROM monthly_data
                                    UNION ALL SELECT EXTRACT(YEAR FROM $1::timestamp)::text, TO_CHAR($1::timestamp, 'Mon'), 0, 0
                                    WHERE NOT EXISTS (SELECT 1 FROM monthly_data WHERE year = EXTRACT(YEAR FROM $1::timestamp)::integer AND month = EXTRACT(MONTH FROM $1::timestamp)::integer)
                                    UNION ALL SELECT EXTRACT(YEAR FROM $3::timestamp)::text, TO_CHAR($3::timestamp, 'Mon'), 0, 0
                                    WHERE NOT EXISTS (SELECT 1 FROM monthly_data WHERE year = EXTRACT(YEAR FROM $3::timestamp)::integer AND month = EXTRACT(MONTH FROM $3::timestamp)::integer)
                                ) SELECT * FROM formatted_data ORDER BY year DESC, TO_DATE(month, 'Mon') DESC;
                                """)
                .execute(getMonthlyTuple(req.getYear(), req.getMonth()))
                .map(this::mapMonthlyFailed);
    }

    public Future<List<TransactionYearlyAmountFailed>> getYearlyAmountTransactionFailed(
            Integer year) {
        return client
                .preparedQuery(
                        """
                                WITH yearly_data AS (
                                    SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year, COUNT(*) AS total_failed, COALESCE(SUM(t.amount), 0)::integer AS total_amount
                                    FROM transactions t
                                    WHERE t.deleted_at IS NULL AND t.payment_status = 'failed'
                                      AND (EXTRACT(YEAR FROM t.created_at) = $1::integer OR EXTRACT(YEAR FROM t.created_at) = $1::integer - 1)
                                    GROUP BY EXTRACT(YEAR FROM t.created_at)
                                ), formatted_data AS (
                                    SELECT year::text, total_failed::integer, total_amount::integer FROM yearly_data
                                    UNION ALL SELECT $1::text, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = $1::integer)
                                    UNION ALL SELECT ($1::integer - 1)::text, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = $1::integer - 1)
                                ) SELECT * FROM formatted_data ORDER BY year DESC;
                                """)
                .execute(Tuple.of(year))
                .map(this::mapYearlyFailed);
    }

    public Future<List<TransactionMonthlyMethod>> getMonthlyTransactionMethodsSuccess(
            MonthMethodTransactionRequest req) {
        return client
                .preparedQuery(
                        """
                                WITH date_ranges AS (SELECT $1::timestamp AS range1_start, $2::timestamp AS range1_end, $3::timestamp AS range2_start, $4::timestamp AS range2_end),
                                     payment_methods AS (SELECT DISTINCT payment_method FROM transactions WHERE deleted_at IS NULL),
                                     all_months AS (
                                         SELECT generate_series(date_trunc('month', LEAST((SELECT range1_start FROM date_ranges), (SELECT range2_start FROM date_ranges))),
                                                                date_trunc('month', GREATEST((SELECT range1_end FROM date_ranges), (SELECT range2_end FROM date_ranges))), interval '1 month')::date AS activity_month
                                     ),
                                     all_combinations AS (SELECT am.activity_month, pm.payment_method FROM all_months am CROSS JOIN payment_methods pm),
                                     monthly_transactions AS (
                                         SELECT date_trunc('month', t.created_at)::date AS activity_month, t.payment_method,
                                                COUNT(t.transaction_id) AS total_transactions, COALESCE(SUM(t.amount), 0)::NUMERIC AS total_amount
                                         FROM transactions t JOIN date_ranges dr ON (t.created_at BETWEEN dr.range1_start AND dr.range1_end OR t.created_at BETWEEN dr.range2_start AND dr.range2_end)
                                         WHERE t.deleted_at IS NULL AND t.payment_status = 'success'
                                         GROUP BY date_trunc('month', t.created_at), t.payment_method
                                     )
                                SELECT TO_CHAR(ac.activity_month, 'Mon') AS month, ac.payment_method,
                                       COALESCE(mt.total_transactions, 0) AS total_transactions, COALESCE(mt.total_amount, 0) AS total_amount
                                FROM all_combinations ac LEFT JOIN monthly_transactions mt ON ac.activity_month = mt.activity_month AND ac.payment_method = mt.payment_method
                                ORDER BY ac.activity_month, ac.payment_method;
                                """)
                .execute(getMonthlyTuple(req.getYear(), req.getMonth()))
                .map(this::mapMonthlyMethod);
    }

    public Future<List<TransactionMonthlyMethod>> getMonthlyTransactionMethodsFailed(
            MonthMethodTransactionRequest req) {
        return client
                .preparedQuery(
                        """
                                WITH date_ranges AS (SELECT $1::timestamp AS range1_start, $2::timestamp AS range1_end, $3::timestamp AS range2_start, $4::timestamp AS range2_end),
                                     payment_methods AS (SELECT DISTINCT payment_method FROM transactions WHERE deleted_at IS NULL),
                                     all_months AS (
                                         SELECT generate_series(date_trunc('month', LEAST((SELECT range1_start FROM date_ranges), (SELECT range2_start FROM date_ranges))),
                                                                date_trunc('month', GREATEST((SELECT range1_end FROM date_ranges), (SELECT range2_end FROM date_ranges))), interval '1 month')::date AS activity_month
                                     ),
                                     all_combinations AS (SELECT am.activity_month, pm.payment_method FROM all_months am CROSS JOIN payment_methods pm),
                                     monthly_transactions AS (
                                         SELECT date_trunc('month', t.created_at)::date AS activity_month, t.payment_method,
                                                COUNT(t.transaction_id) AS total_transactions, COALESCE(SUM(t.amount), 0)::NUMERIC AS total_amount
                                         FROM transactions t JOIN date_ranges dr ON (t.created_at BETWEEN dr.range1_start AND dr.range1_end OR t.created_at BETWEEN dr.range2_start AND dr.range2_end)
                                         WHERE t.deleted_at IS NULL AND t.payment_status = 'failed'
                                         GROUP BY date_trunc('month', t.created_at), t.payment_method
                                     )
                                SELECT TO_CHAR(ac.activity_month, 'Mon') AS month, ac.payment_method,
                                       COALESCE(mt.total_transactions, 0) AS total_transactions, COALESCE(mt.total_amount, 0) AS total_amount
                                FROM all_combinations ac LEFT JOIN monthly_transactions mt ON ac.activity_month = mt.activity_month AND ac.payment_method = mt.payment_method
                                ORDER BY ac.activity_month, ac.payment_method;
                                """)
                .execute(getMonthlyTuple(req.getYear(), req.getMonth()))
                .map(this::mapMonthlyMethod);
    }

    public Future<List<TransactionYearMethod>> getYearlyTransactionMethodsSuccess(Integer year) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(year, 1, 1, 0, 0));
        return client
                .preparedQuery(
                        """
                                WITH year_range AS (SELECT EXTRACT(YEAR FROM $1::timestamp)::int - 1 AS start_year, EXTRACT(YEAR FROM $1::timestamp)::int AS end_year),
                                     payment_methods AS (SELECT DISTINCT payment_method FROM transactions WHERE deleted_at IS NULL),
                                     all_years AS (SELECT generate_series((SELECT start_year FROM year_range), (SELECT end_year FROM year_range))::int AS year),
                                     all_combinations AS (SELECT ay.year::text AS year, pm.payment_method FROM all_years ay CROSS JOIN payment_methods pm),
                                     yearly_transactions AS (
                                         SELECT EXTRACT(YEAR FROM t.created_at)::text AS year, t.payment_method,
                                                COUNT(t.transaction_id) AS total_transactions, COALESCE(SUM(t.amount), 0)::NUMERIC AS total_amount
                                         FROM transactions t
                                         WHERE t.deleted_at IS NULL AND t.payment_status = 'success'
                                           AND EXTRACT(YEAR FROM t.created_at) BETWEEN (SELECT start_year FROM year_range) AND (SELECT end_year FROM year_range)
                                         GROUP BY EXTRACT(YEAR FROM t.created_at), t.payment_method
                                     )
                                SELECT ac.year, ac.payment_method, COALESCE(yt.total_transactions, 0) AS total_transactions, COALESCE(yt.total_amount, 0) AS total_amount
                                FROM all_combinations ac LEFT JOIN yearly_transactions yt ON ac.year = yt.year AND ac.payment_method = yt.payment_method
                                ORDER BY ac.year, ac.payment_method;
                                """)
                .execute(Tuple.of(refTs))
                .map(this::mapYearlyMethod);
    }

    public Future<List<TransactionYearMethod>> getYearlyTransactionMethodsFailed(Integer year) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(year, 1, 1, 0, 0));
        return client
                .preparedQuery(
                        """
                                 WITH year_range AS (SELECT EXTRACT(YEAR FROM $1::timestamp)::int - 1 AS start_year, EXTRACT(YEAR FROM $1::timestamp)::int AS end_year),
                                     payment_methods AS (SELECT DISTINCT payment_method FROM transactions WHERE deleted_at IS NULL),
                                     all_years AS (SELECT generate_series((SELECT start_year FROM year_range), (SELECT end_year FROM year_range))::int AS year),
                                     all_combinations AS (SELECT ay.year::text AS year, pm.payment_method FROM all_years ay CROSS JOIN payment_methods pm),
                                     yearly_transactions AS (
                                         SELECT EXTRACT(YEAR FROM t.created_at)::text AS year, t.payment_method,
                                                COUNT(t.transaction_id) AS total_transactions, COALESCE(SUM(t.amount), 0)::NUMERIC AS total_amount
                                         FROM transactions t
                                         WHERE t.deleted_at IS NULL AND t.payment_status = 'failed'
                                           AND EXTRACT(YEAR FROM t.created_at) BETWEEN (SELECT start_year FROM year_range) AND (SELECT end_year FROM year_range)
                                         GROUP BY EXTRACT(YEAR FROM t.created_at), t.payment_method
                                     )
                                SELECT ac.year, ac.payment_method, COALESCE(yt.total_transactions, 0) AS total_transactions, COALESCE(yt.total_amount, 0) AS total_amount
                                FROM all_combinations ac LEFT JOIN yearly_transactions yt ON ac.year = yt.year AND ac.payment_method = yt.payment_method
                                ORDER BY ac.year, ac.payment_method;
                                """)
                .execute(Tuple.of(refTs))
                .map(this::mapYearlyMethod);
    }

    public Future<List<TransactionMonthlyAmountSuccess>> getMonthlyAmountTransactionSuccessByMerchant(
            MonthAmountTransactionMerchant req) {
        Tuple args = getMonthlyTuple(req.getYear(), req.getMonth()).addInteger(req.getMerchantId());
        return client
                .preparedQuery(
                        """
                                WITH monthly_data AS (
                                    SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year, EXTRACT(MONTH FROM t.created_at)::integer AS month,
                                           COUNT(*) AS total_success, COALESCE(SUM(t.amount), 0)::integer AS total_amount
                                    FROM transactions t
                                    WHERE t.deleted_at IS NULL AND t.payment_status = 'success' AND t.merchant_id = $5
                                      AND ((t.created_at >= $1 AND t.created_at <= $2) OR (t.created_at >= $3 AND t.created_at <= $4))
                                    GROUP BY EXTRACT(YEAR FROM t.created_at), EXTRACT(MONTH FROM t.created_at)
                                ), formatted_data AS (
                                    SELECT year::text, TO_CHAR(TO_DATE(month::text, 'MM'), 'Mon') AS month, total_success, total_amount FROM monthly_data
                                    UNION ALL SELECT EXTRACT(YEAR FROM $1::timestamp)::text, TO_CHAR($1::timestamp, 'Mon'), 0, 0
                                    WHERE NOT EXISTS (SELECT 1 FROM monthly_data WHERE year = EXTRACT(YEAR FROM $1::timestamp)::integer AND month = EXTRACT(MONTH FROM $1::timestamp)::integer)
                                    UNION ALL SELECT EXTRACT(YEAR FROM $3::timestamp)::text, TO_CHAR($3::timestamp, 'Mon'), 0, 0
                                    WHERE NOT EXISTS (SELECT 1 FROM monthly_data WHERE year = EXTRACT(YEAR FROM $3::timestamp)::integer AND month = EXTRACT(MONTH FROM $3::timestamp)::integer)
                                ) SELECT * FROM formatted_data ORDER BY year DESC, TO_DATE(month, 'Mon') DESC;
                                """)
                .execute(args)
                .map(this::mapMonthlySuccess);
    }

    public Future<List<TransactionYearlyAmountSuccess>> getYearlyAmountTransactionSuccessByMerchant(
            YearAmountTransactionMerchant req) {
        return client
                .preparedQuery(
                        """
                                WITH yearly_data AS (
                                    SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year, COUNT(*) AS total_success, COALESCE(SUM(t.amount), 0)::integer AS total_amount
                                    FROM transactions t
                                    WHERE t.deleted_at IS NULL AND t.payment_status = 'success' AND t.merchant_id = $2
                                      AND (EXTRACT(YEAR FROM t.created_at) = $1::integer OR EXTRACT(YEAR FROM t.created_at) = $1::integer - 1)
                                    GROUP BY EXTRACT(YEAR FROM t.created_at)
                                ), formatted_data AS (
                                    SELECT year::text, total_success::integer, total_amount::integer FROM yearly_data
                                    UNION ALL SELECT $1::text, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = $1::integer)
                                    UNION ALL SELECT ($1::integer - 1)::text, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = $1::integer - 1)
                                ) SELECT * FROM formatted_data ORDER BY year DESC;
                                """)
                .execute(Tuple.of(req.getYear(), req.getMerchantId()))
                .map(this::mapYearlySuccess);
    }

    public Future<List<TransactionMonthlyAmountFailed>> getMonthlyAmountTransactionFailedByMerchant(
            MonthAmountTransactionMerchant req) {
        Tuple args = getMonthlyTuple(req.getYear(), req.getMonth()).addInteger(req.getMerchantId());
        return client
                .preparedQuery(
                        """
                                WITH monthly_data AS (
                                    SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year, EXTRACT(MONTH FROM t.created_at)::integer AS month,
                                           COUNT(*) AS total_failed, COALESCE(SUM(t.amount), 0)::integer AS total_amount
                                    FROM transactions t
                                    WHERE t.deleted_at IS NULL AND t.payment_status = 'failed' AND t.merchant_id = $5
                                      AND ((t.created_at >= $1 AND t.created_at <= $2) OR (t.created_at >= $3 AND t.created_at <= $4))
                                    GROUP BY EXTRACT(YEAR FROM t.created_at), EXTRACT(MONTH FROM t.created_at)
                                ), formatted_data AS (
                                    SELECT year::text, TO_CHAR(TO_DATE(month::text, 'MM'), 'Mon') AS month, total_failed, total_amount FROM monthly_data
                                    UNION ALL SELECT EXTRACT(YEAR FROM $1::timestamp)::text, TO_CHAR($1::timestamp, 'Mon'), 0, 0
                                    WHERE NOT EXISTS (SELECT 1 FROM monthly_data WHERE year = EXTRACT(YEAR FROM $1::timestamp)::integer AND month = EXTRACT(MONTH FROM $1::timestamp)::integer)
                                    UNION ALL SELECT EXTRACT(YEAR FROM $3::timestamp)::text, TO_CHAR($3::timestamp, 'Mon'), 0, 0
                                    WHERE NOT EXISTS (SELECT 1 FROM monthly_data WHERE year = EXTRACT(YEAR FROM $3::timestamp)::integer AND month = EXTRACT(MONTH FROM $3::timestamp)::integer)
                                ) SELECT * FROM formatted_data ORDER BY year DESC, TO_DATE(month, 'Mon') DESC;
                                """)
                .execute(args)
                .map(this::mapMonthlyFailed);
    }

    public Future<List<TransactionYearlyAmountFailed>> getYearlyAmountTransactionFailedByMerchant(
            YearAmountTransactionMerchant req) {
        return client
                .preparedQuery(
                        """
                                WITH yearly_data AS (
                                    SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year, COUNT(*) AS total_failed, COALESCE(SUM(t.amount), 0)::integer AS total_amount
                                    FROM transactions t
                                    WHERE t.deleted_at IS NULL AND t.payment_status = 'failed' AND t.merchant_id = $2
                                      AND (EXTRACT(YEAR FROM t.created_at) = $1::integer OR EXTRACT(YEAR FROM t.created_at) = $1::integer - 1)
                                    GROUP BY EXTRACT(YEAR FROM t.created_at)
                                ), formatted_data AS (
                                    SELECT year::text, total_failed::integer, total_amount::integer FROM yearly_data
                                    UNION ALL SELECT $1::text, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = $1::integer)
                                    UNION ALL SELECT ($1::integer - 1)::text, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = $1::integer - 1)
                                ) SELECT * FROM formatted_data ORDER BY year DESC;
                                """)
                .execute(Tuple.of(req.getYear(), req.getMerchantId()))
                .map(this::mapYearlyFailed);
    }

    public Future<List<TransactionMonthlyMethod>> getMonthlyTransactionMethodsByMerchantSuccess(
            MonthMethodTransactionMerchantRequest req) {
        Tuple args = getMonthlyTuple(req.getYear(), req.getMonth()).addInteger(req.getMerchantId());
        return client
                .preparedQuery(
                        """
                                WITH date_ranges AS (SELECT $1::timestamp AS range1_start, $2::timestamp AS range1_end, $3::timestamp AS range2_start, $4::timestamp AS range2_end),
                                     payment_methods AS (SELECT DISTINCT payment_method FROM transactions WHERE deleted_at IS NULL),
                                     all_months AS (
                                         SELECT generate_series(date_trunc('month', LEAST((SELECT range1_start FROM date_ranges), (SELECT range2_start FROM date_ranges))),
                                                                date_trunc('month', GREATEST((SELECT range1_end FROM date_ranges), (SELECT range2_end FROM date_ranges))), interval '1 month')::date AS activity_month
                                     ),
                                     all_combinations AS (SELECT am.activity_month, pm.payment_method FROM all_months am CROSS JOIN payment_methods pm),
                                     monthly_transactions AS (
                                         SELECT date_trunc('month', t.created_at)::date AS activity_month, t.payment_method,
                                                COUNT(t.transaction_id) AS total_transactions, COALESCE(SUM(t.amount), 0)::NUMERIC AS total_amount
                                         FROM transactions t JOIN date_ranges dr ON (t.created_at BETWEEN dr.range1_start AND dr.range1_end OR t.created_at BETWEEN dr.range2_start AND dr.range2_end)
                                         WHERE t.deleted_at IS NULL AND t.payment_status = 'success' AND t.merchant_id = $5
                                         GROUP BY date_trunc('month', t.created_at), t.payment_method
                                     )
                                SELECT TO_CHAR(ac.activity_month, 'Mon') AS month, ac.payment_method,
                                       COALESCE(mt.total_transactions, 0) AS total_transactions, COALESCE(mt.total_amount, 0) AS total_amount
                                FROM all_combinations ac LEFT JOIN monthly_transactions mt ON ac.activity_month = mt.activity_month AND ac.payment_method = mt.payment_method
                                ORDER BY ac.activity_month, ac.payment_method;
                                """)
                .execute(args)
                .map(this::mapMonthlyMethod);
    }

    public Future<List<TransactionMonthlyMethod>> getMonthlyTransactionMethodsByMerchantFailed(
            MonthMethodTransactionMerchantRequest req) {
        Tuple args = getMonthlyTuple(req.getYear(), req.getMonth()).addInteger(req.getMerchantId());
        return client
                .preparedQuery(
                        """
                                WITH date_ranges AS (SELECT $1::timestamp AS range1_start, $2::timestamp AS range1_end, $3::timestamp AS range2_start, $4::timestamp AS range2_end),
                                     payment_methods AS (SELECT DISTINCT payment_method FROM transactions WHERE deleted_at IS NULL),
                                     all_months AS (
                                         SELECT generate_series(date_trunc('month', LEAST((SELECT range1_start FROM date_ranges), (SELECT range2_start FROM date_ranges))),
                                                                date_trunc('month', GREATEST((SELECT range1_end FROM date_ranges), (SELECT range2_end FROM date_ranges))), interval '1 month')::date AS activity_month
                                     ),
                                     all_combinations AS (SELECT am.activity_month, pm.payment_method FROM all_months am CROSS JOIN payment_methods pm),
                                     monthly_transactions AS (
                                         SELECT date_trunc('month', t.created_at)::date AS activity_month, t.payment_method,
                                                COUNT(t.transaction_id) AS total_transactions, COALESCE(SUM(t.amount), 0)::NUMERIC AS total_amount
                                         FROM transactions t JOIN date_ranges dr ON (t.created_at BETWEEN dr.range1_start AND dr.range1_end OR t.created_at BETWEEN dr.range2_start AND dr.range2_end)
                                         WHERE t.deleted_at IS NULL AND t.payment_status = 'failed' AND t.merchant_id = $5
                                         GROUP BY date_trunc('month', t.created_at), t.payment_method
                                     )
                                SELECT TO_CHAR(ac.activity_month, 'Mon') AS month, ac.payment_method,
                                       COALESCE(mt.total_transactions, 0) AS total_transactions, COALESCE(mt.total_amount, 0) AS total_amount
                                FROM all_combinations ac LEFT JOIN monthly_transactions mt ON ac.activity_month = mt.activity_month AND ac.payment_method = mt.payment_method
                                ORDER BY ac.activity_month, ac.payment_method;
                                """)
                .execute(args)
                .map(this::mapMonthlyMethod);
    }

    public Future<List<TransactionYearMethod>> getYearlyTransactionMethodsByMerchantSuccess(
            YearMethodTransactionMerchantRequest req) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(req.getYear(), 1, 1, 0, 0));
        return client
                .preparedQuery(
                        """
                                WITH year_series AS (SELECT generate_series(EXTRACT(YEAR FROM $1::timestamp)::integer - 2, EXTRACT(YEAR FROM $1::timestamp)::integer, 1) AS year),
                                     yearly_transactions AS (
                                         SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year, t.payment_method,
                                                COUNT(t.transaction_id) AS total_transactions, SUM(t.amount)::NUMERIC AS total_amount
                                         FROM transactions t
                                         WHERE t.deleted_at IS NULL AND t.payment_status = 'success' AND t.merchant_id = $2
                                           AND EXTRACT(YEAR FROM t.created_at) BETWEEN (EXTRACT(YEAR FROM $1::timestamp) - 1) AND EXTRACT(YEAR FROM $1::timestamp)
                                         GROUP BY year, t.payment_method
                                     ),
                                     payment_methods AS (SELECT DISTINCT payment_method FROM transactions WHERE deleted_at IS NULL)
                                SELECT ys.year::text AS year, pm.payment_method,
                                       COALESCE(yt.total_transactions, 0) AS total_transactions, COALESCE(yt.total_amount, 0) AS total_amount
                                FROM year_series ys CROSS JOIN payment_methods pm
                                LEFT JOIN yearly_transactions yt ON ys.year = yt.year AND pm.payment_method = yt.payment_method
                                ORDER BY ys.year, pm.payment_method;
                                """)
                .execute(Tuple.of(refTs, req.getMerchantId()))
                .map(this::mapYearlyMethod);
    }

    public Future<List<TransactionYearMethod>> getYearlyTransactionMethodsByMerchantFailed(
            YearMethodTransactionMerchantRequest req) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(req.getYear(), 1, 1, 0, 0));
        return client
                .preparedQuery(
                        """
                                WITH year_series AS (SELECT generate_series(EXTRACT(YEAR FROM $1::timestamp)::integer - 1, EXTRACT(YEAR FROM $1::timestamp)::integer, 1) AS year),
                                     yearly_transactions AS (
                                         SELECT EXTRACT(YEAR FROM t.created_at)::integer AS year, t.payment_method,
                                                COUNT(t.transaction_id) AS total_transactions, SUM(t.amount)::NUMERIC AS total_amount
                                         FROM transactions t
                                         WHERE t.deleted_at IS NULL AND t.payment_status = 'failed' AND t.merchant_id = $2
                                           AND EXTRACT(YEAR FROM t.created_at) BETWEEN (EXTRACT(YEAR FROM $1::timestamp) - 1) AND EXTRACT(YEAR FROM $1::timestamp)
                                         GROUP BY year, t.payment_method
                                     ),
                                     payment_methods AS (SELECT DISTINCT payment_method FROM transactions WHERE deleted_at IS NULL)
                                SELECT ys.year::text AS year, pm.payment_method,
                                       COALESCE(yt.total_transactions, 0) AS total_transactions, COALESCE(yt.total_amount, 0) AS total_amount
                                FROM year_series ys CROSS JOIN payment_methods pm
                                LEFT JOIN yearly_transactions yt ON ys.year = yt.year AND pm.payment_method = yt.payment_method
                                ORDER BY ys.year, pm.payment_method;
                                """)
                .execute(Tuple.of(refTs, req.getMerchantId()))
                .map(this::mapYearlyMethod);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank())
            return null;
        return search;
    }

    private PagedResult<Transaction> mapPagedTransactions(RowSet<Row> rows) {
        List<Transaction> list = new ArrayList<>();
        int total = 0;
        for (Row row : rows) {
            list.add(Transaction.fromRow(row));
            if (total == 0)
                total = row.getInteger("total_count");
        }
        return new PagedResult<>(list, total);
    }

    private Long getLongFromDecimal(Row row, String column) {
        Object val = row.getValue(column);
        if (val == null)
            return 0L;
        if (val instanceof BigDecimal)
            return ((BigDecimal) val).longValue();
        if (val instanceof Number)
            return ((Number) val).longValue();
        return 0L;
    }

    private List<TransactionMonthlyAmountSuccess> mapMonthlySuccess(RowSet<Row> rows) {
        List<TransactionMonthlyAmountSuccess> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new TransactionMonthlyAmountSuccess(
                    r.getString("year"), r.getString("month"), r.getInteger("total_success"),
                    r.getInteger("total_amount").longValue()));
        }
        return list;
    }

    private List<TransactionYearlyAmountSuccess> mapYearlySuccess(RowSet<Row> rows) {
        List<TransactionYearlyAmountSuccess> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new TransactionYearlyAmountSuccess(
                    r.getString("year"), r.getInteger("total_success"), r.getInteger("total_amount").longValue()));
        }
        return list;
    }

    private List<TransactionMonthlyAmountFailed> mapMonthlyFailed(RowSet<Row> rows) {
        List<TransactionMonthlyAmountFailed> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new TransactionMonthlyAmountFailed(
                    r.getString("year"), r.getString("month"), r.getInteger("total_failed"),
                    r.getInteger("total_amount").longValue()));
        }
        return list;
    }

    private List<TransactionYearlyAmountFailed> mapYearlyFailed(RowSet<Row> rows) {
        List<TransactionYearlyAmountFailed> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new TransactionYearlyAmountFailed(
                    r.getString("year"), r.getInteger("total_failed"), r.getInteger("total_amount").longValue()));
        }
        return list;
    }

    private List<TransactionMonthlyMethod> mapMonthlyMethod(RowSet<Row> rows) {
        List<TransactionMonthlyMethod> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new TransactionMonthlyMethod(
                    r.getString("month"), r.getString("payment_method"), r.getInteger("total_transactions"),
                    getLongFromDecimal(r, "total_amount")));
        }
        return list;
    }

    private List<TransactionYearMethod> mapYearlyMethod(RowSet<Row> rows) {
        List<TransactionYearMethod> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new TransactionYearMethod(
                    r.getString("year"), r.getString("payment_method"), r.getInteger("total_transactions"),
                    getLongFromDecimal(r, "total_amount")));
        }
        return list;
    }
}