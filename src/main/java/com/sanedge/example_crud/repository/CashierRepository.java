package com.sanedge.example_crud.repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.model.cashier.Cashier;
import com.sanedge.example_crud.model.cashier.CashierMonthSales;
import com.sanedge.example_crud.model.cashier.CashierMonthTotalSales;
import com.sanedge.example_crud.model.cashier.CashierYearSales;
import com.sanedge.example_crud.model.cashier.CashierYearTotalSales;

import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CashierRepository {
    private final Pool client;

    public Future<PagedResult<Cashier>> getCashiers(FindAllCashiers req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at,
                            COUNT(*) OVER () AS total_count
                        FROM cashiers
                        WHERE
                            deleted_at IS NULL
                            AND (
                                $1::TEXT IS NULL
                                OR name ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedCashiers);
    }

    public Future<PagedResult<Cashier>> getCashiersActive(FindAllCashiers req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at,
                            deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM cashiers
                        WHERE
                            deleted_at IS NULL
                            AND (
                                $1::TEXT IS NULL
                                OR name ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedCashiers);
    }

    public Future<PagedResult<Cashier>> getCashiersTrashed(FindAllCashiers req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at,
                            deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM cashiers
                        WHERE
                            deleted_at IS NOT NULL
                            AND (
                                $1::TEXT IS NULL
                                OR name ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedCashiers);
    }

    public Future<PagedResult<Cashier>> getCashiersByMerchant(FindAllCashierMerchant req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at,
                            deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM cashiers
                        WHERE
                            merchant_id = $1
                            AND deleted_at IS NULL
                            AND (
                                $2::TEXT IS NULL
                                OR name ILIKE '%' || $2 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $3
                        OFFSET $4;
                        """)
                .execute(Tuple.of(req.getMerchantId(), normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedCashiers);
    }

    public Future<Cashier> getCashierById(Long cashierId) {
        return client
                .preparedQuery("""
                        SELECT
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at
                        FROM cashiers
                        WHERE
                            cashier_id = $1
                            AND deleted_at IS NULL;
                        """)
                .execute(Tuple.of(cashierId))
                .map(rows -> rows.iterator().hasNext() ? Cashier.fromRow(rows.iterator().next()) : null);
    }

    public Future<Cashier> createCashier(CreateCashierRequest req) {
        return client
                .preparedQuery("""
                        INSERT INTO
                            cashiers (merchant_id, user_id, name)
                        VALUES ($1, $2, $3)
                        RETURNING
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at;
                        """)
                .execute(Tuple.of(req.getMerchantId(), req.getUserId(), req.getName()))
                .map(rows -> Cashier.fromRow(rows.iterator().next()));
    }

    public Future<Cashier> updateCashier(UpdateCashierRequest req) {
        return client
                .preparedQuery("""
                        UPDATE cashiers
                        SET
                            name = $2,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE
                            cashier_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at;
                        """)
                .execute(Tuple.of(req.getCashierId(), req.getName()))
                .map(rows -> rows.iterator().hasNext() ? Cashier.fromRow(rows.iterator().next()) : null);
    }

    public Future<Cashier> trashCashier(Long cashierId) {
        return client
                .preparedQuery("""
                        UPDATE cashiers
                        SET
                            deleted_at = current_timestamp
                        WHERE
                            cashier_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at,
                            deleted_at;
                        """)
                .execute(Tuple.of(cashierId))
                .map(rows -> rows.iterator().hasNext() ? Cashier.fromRow(rows.iterator().next()) : null);
    }

    public Future<Cashier> restoreCashier(Long cashierId) {
        return client
                .preparedQuery("""
                        UPDATE cashiers
                        SET
                            deleted_at = NULL
                        WHERE
                            cashier_id = $1
                            AND deleted_at IS NOT NULL
                        RETURNING
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at,
                            deleted_at;
                        """)
                .execute(Tuple.of(cashierId))
                .map(rows -> rows.iterator().hasNext() ? Cashier.fromRow(rows.iterator().next()) : null);
    }

    public Future<Void> deleteCashierPermanently(Long cashierId) {
        return client
                .preparedQuery("DELETE FROM cashiers WHERE cashier_id = $1 AND deleted_at IS NOT NULL")
                .execute(Tuple.of(cashierId))
                .mapEmpty();
    }

    public Future<Cashier> findByTrashed(Long cashierId) {
        return client
                .preparedQuery("""
                        SELECT
                            cashier_id,
                            merchant_id,
                            user_id,
                            name,
                            created_at,
                            updated_at,
                            deleted_at
                        FROM cashiers
                        WHERE
                            cashier_id = $1
                            AND deleted_at IS NOT NULL;
                        """)
                .execute(Tuple.of(cashierId))
                .map(rows -> rows.iterator().hasNext() ? Cashier.fromRow(rows.iterator().next()) : null);
    }

    public Future<Integer> restoreAllCashiers() {
        return client
                .preparedQuery("UPDATE cashiers SET deleted_at = NULL WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }

    public Future<Integer> deleteAllPermanentCashiers() {
        return client
                .preparedQuery("DELETE FROM cashiers WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }


   public Future<List<CashierMonthTotalSales>> getMonthlyTotalSalesCashier(MonthTotalSales req) {
        int year = req.getYear();
        int month = req.getMonth();
        
        return client
                .preparedQuery("""
                        WITH
                            monthly_totals AS (
                                SELECT EXTRACT(YEAR FROM o.created_at)::TEXT AS year, EXTRACT(MONTH FROM o.created_at)::integer AS month, COALESCE(SUM(o.total_price), 0)::INTEGER AS total_sales
                                FROM orders o
                                    JOIN cashiers c ON o.cashier_id = c.cashier_id
                                WHERE
                                    o.deleted_at IS NULL
                                    AND c.deleted_at IS NULL
                                    AND (
                                        (o.created_at >= $1 AND o.created_at <= $2)
                                        OR (o.created_at >= $3 AND o.created_at <= $4)
                                    )
                                GROUP BY EXTRACT(YEAR FROM o.created_at), EXTRACT(MONTH FROM o.created_at)
                            ),
                            all_months AS (
                                SELECT EXTRACT(YEAR FROM $1)::TEXT AS year, EXTRACT(MONTH FROM $1)::integer AS month, TO_CHAR($1, 'FMMonth') AS month_name
                                UNION
                                SELECT EXTRACT(YEAR FROM $3)::TEXT AS year, EXTRACT(MONTH FROM $3)::integer AS month, TO_CHAR($3, 'FMMonth') AS month_name
                            )
                        SELECT COALESCE(am.year, EXTRACT(YEAR FROM $1)::TEXT) AS year, COALESCE(am.month_name, TO_CHAR($1, 'FMMonth')) AS month, COALESCE(mt.total_sales, 0) AS total_sales
                        FROM all_months am LEFT JOIN monthly_totals mt ON am.year = mt.year AND am.month = mt.month
                        ORDER BY am.year::INT DESC, am.month DESC;
                        """)
                .execute(getMonthlyTuple(year, month))
                .map(this::mapCashierMonthTotalSales);
    }

    public Future<List<CashierYearTotalSales>> getYearlyTotalSalesCashier(Integer year) {
        return client
                .preparedQuery("""
                        WITH yearly_data AS (
                            SELECT EXTRACT(YEAR FROM o.created_at)::integer AS year, COALESCE(SUM(o.total_price), 0)::INTEGER AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL
                              AND (EXTRACT(YEAR FROM o.created_at) = $1::integer OR EXTRACT(YEAR FROM o.created_at) = $1::integer - 1)
                            GROUP BY EXTRACT(YEAR FROM o.created_at)
                        ), all_years AS ( SELECT $1 AS year UNION SELECT $1 - 1 AS year )
                        SELECT a.year::text AS year, COALESCE(yd.total_sales, 0) AS total_sales
                        FROM all_years a LEFT JOIN yearly_data yd ON a.year = yd.year
                        ORDER BY a.year DESC;
                        """)
                .execute(Tuple.of(year))
                .map(this::mapCashierYearTotalSales);
    }

    public Future<List<CashierMonthTotalSales>> getMonthlyTotalSalesByMerchant(MonthTotalSalesMerchant req) {
        Tuple args = getMonthlyTuple(req.getYear(), req.getMonth()).addLong(req.getMerchantId().longValue());
        return client
                .preparedQuery("""
                        WITH monthly_totals AS (
                            SELECT EXTRACT(YEAR FROM o.created_at)::TEXT AS year, EXTRACT(MONTH FROM o.created_at)::integer AS month, COALESCE(SUM(o.total_price), 0)::INTEGER AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL
                              AND ( (o.created_at >= $1 AND o.created_at <= $2) OR (o.created_at >= $3 AND o.created_at <= $4) )
                              AND o.merchant_id = $5
                            GROUP BY EXTRACT(YEAR FROM o.created_at), EXTRACT(MONTH FROM o.created_at)
                        ), all_months AS (
                            SELECT EXTRACT(YEAR FROM $1)::TEXT AS year, EXTRACT(MONTH FROM $1)::integer AS month, TO_CHAR($1, 'FMMonth') AS month_name
                            UNION SELECT EXTRACT(YEAR FROM $3)::TEXT AS year, EXTRACT(MONTH FROM $3)::integer AS month, TO_CHAR($3, 'FMMonth') AS month_name
                        )
                        SELECT COALESCE(am.year, EXTRACT(YEAR FROM $1)::TEXT) AS year, COALESCE(am.month_name, TO_CHAR($1, 'FMMonth')) AS month, COALESCE(mt.total_sales, 0) AS total_sales
                        FROM all_months am LEFT JOIN monthly_totals mt ON am.year = mt.year AND am.month = mt.month
                        ORDER BY am.year::INT DESC, am.month DESC;
                        """)
                .execute(args)
                .map(this::mapCashierMonthTotalSales);
    }

    public Future<List<CashierYearTotalSales>> getYearlyTotalSalesByMerchant(YearTotalSalesMerchant req) {
        return client
                .preparedQuery("""
                        WITH yearly_data AS (
                            SELECT EXTRACT(YEAR FROM o.created_at)::integer AS year, COALESCE(SUM(o.total_price), 0)::INTEGER AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL
                              AND (EXTRACT(YEAR FROM o.created_at) = $1::integer OR EXTRACT(YEAR FROM o.created_at) = $1::integer - 1)
                              AND o.merchant_id = $2
                            GROUP BY EXTRACT(YEAR FROM o.created_at)
                        ), all_years AS ( SELECT $1 AS year UNION SELECT $1 - 1 AS year )
                        SELECT a.year::text AS year, COALESCE(yd.total_sales, 0) AS total_sales
                        FROM all_years a LEFT JOIN yearly_data yd ON a.year = yd.year ORDER BY a.year DESC;
                        """)
                .execute(Tuple.of(req.getYear(), req.getMerchantId().longValue()))
                .map(this::mapCashierYearTotalSales);
    }

    public Future<List<CashierMonthTotalSales>> getMonthlyTotalSalesById(MonthTotalSalesCashier req) {
        Tuple args = getMonthlyTuple(req.getYear(), req.getMonth()).addLong(req.getCashierId().longValue());
        return client
                .preparedQuery("""
                        WITH monthly_totals AS (
                            SELECT EXTRACT(YEAR FROM o.created_at)::TEXT AS year, EXTRACT(MONTH FROM o.created_at)::integer AS month, COALESCE(SUM(o.total_price), 0)::INTEGER AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL
                              AND ( (o.created_at >= $1 AND o.created_at <= $2) OR (o.created_at >= $3 AND o.created_at <= $4) )
                              AND c.cashier_id = $5
                            GROUP BY EXTRACT(YEAR FROM o.created_at), EXTRACT(MONTH FROM o.created_at)
                        ), all_months AS (
                            SELECT EXTRACT(YEAR FROM $1)::TEXT AS year, EXTRACT(MONTH FROM $1)::integer AS month, TO_CHAR($1, 'FMMonth') AS month_name
                            UNION SELECT EXTRACT(YEAR FROM $3)::TEXT AS year, EXTRACT(MONTH FROM $3)::integer AS month, TO_CHAR($3, 'FMMonth') AS month_name
                        )
                        SELECT COALESCE(am.year, EXTRACT(YEAR FROM $1)::TEXT) AS year, COALESCE(am.month_name, TO_CHAR($1, 'FMMonth')) AS month, COALESCE(mt.total_sales, 0) AS total_sales
                        FROM all_months am LEFT JOIN monthly_totals mt ON am.year = mt.year AND am.month = mt.month
                        ORDER BY am.year::INT DESC, am.month DESC;
                        """)
                .execute(args)
                .map(this::mapCashierMonthTotalSales);
    }

    public Future<List<CashierYearTotalSales>> getYearlyTotalSalesById(YearTotalSalesCashier req) {
        return client
                .preparedQuery("""
                        WITH yearly_data AS (
                            SELECT EXTRACT(YEAR FROM o.created_at)::integer AS year, COALESCE(SUM(o.total_price), 0)::INTEGER AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL
                              AND (EXTRACT(YEAR FROM o.created_at) = $1::integer OR EXTRACT(YEAR FROM o.created_at) = $1::integer - 1)
                              AND c.cashier_id = $2
                            GROUP BY EXTRACT(YEAR FROM o.created_at)
                        ), all_years AS ( SELECT $1 AS year UNION SELECT $1 - 1 AS year )
                        SELECT a.year::text AS year, COALESCE(yd.total_sales, 0) AS total_sales
                        FROM all_years a LEFT JOIN yearly_data yd ON a.year = yd.year ORDER BY a.year DESC;
                        """)
                .execute(Tuple.of(req.getYear(), req.getCashierId().longValue()))
                .map(this::mapCashierYearTotalSales);
    }

    public Future<List<CashierMonthSales>> getMonthlyCashier(Integer year) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(year, 1, 1, 0, 0));
        return client
                .preparedQuery("""
                        WITH date_range AS (
                            SELECT date_trunc('month', $1::timestamp) AS start_date, date_trunc('month', $1::timestamp) + interval '1 year' - interval '1 day' AS end_date
                        ), cashier_activity AS (
                            SELECT c.cashier_id, c.name AS cashier_name, date_trunc('month', o.created_at) AS activity_month,
                                   COUNT(o.order_id) AS order_count, SUM(o.total_price)::NUMERIC AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL
                              AND o.created_at BETWEEN (SELECT start_date FROM date_range) AND (SELECT end_date FROM date_range)
                            GROUP BY c.cashier_id, c.name, activity_month
                        )
                        SELECT ca.cashier_id, ca.cashier_name, TO_CHAR(ca.activity_month, 'Mon') AS month, ca.order_count, ca.total_sales
                        FROM cashier_activity ca ORDER BY ca.activity_month, ca.cashier_id;
                        """)
                .execute(Tuple.of(refTs))
                .map(this::mapCashierMonthSales);
    }

    public Future<List<CashierYearSales>> getYearlyCashier(Integer year) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(year, 1, 1, 0, 0));
        return client
                .preparedQuery("""
                        WITH last_five_years AS (
                            SELECT c.cashier_id, c.name AS cashier_name, EXTRACT(YEAR FROM o.created_at)::text AS year,
                                   COUNT(o.order_id) AS order_count, SUM(o.total_price) AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL
                              AND EXTRACT(YEAR FROM o.created_at) BETWEEN (EXTRACT(YEAR FROM $1::timestamp) - 4) AND EXTRACT(YEAR FROM $1::timestamp)
                            GROUP BY c.cashier_id, c.name, EXTRACT(YEAR FROM o.created_at)
                        )
                        SELECT year, cashier_id, cashier_name, order_count, total_sales
                        FROM last_five_years ORDER BY year, cashier_id;
                        """)
                .execute(Tuple.of(refTs))
                .map(this::mapCashierYearSales);
    }

    public Future<List<CashierMonthSales>> getMonthlyCashierByCashierId(MonthCashierIdRequest req) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(req.getYear(), 1, 1, 0, 0));
        return client
                .preparedQuery("""
                        WITH date_range AS (
                            SELECT date_trunc('month', $1::timestamp) AS start_date, date_trunc('month', $1::timestamp) + interval '1 year' - interval '1 day' AS end_date
                        ), cashier_activity AS (
                            SELECT c.cashier_id, c.name AS cashier_name, date_trunc('month', o.created_at) AS activity_month,
                                   COUNT(o.order_id) AS order_count, SUM(o.total_price) AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL AND c.cashier_id = $2
                              AND o.created_at BETWEEN (SELECT start_date FROM date_range) AND (SELECT end_date FROM date_range)
                            GROUP BY c.cashier_id, c.name, activity_month
                        )
                        SELECT ca.cashier_id, ca.cashier_name, TO_CHAR(ca.activity_month, 'Mon') AS month, ca.order_count, ca.total_sales
                        FROM cashier_activity ca ORDER BY ca.activity_month, ca.cashier_id;
                        """)
                .execute(Tuple.of(refTs, req.getCashierId().longValue()))
                .map(this::mapCashierMonthSales);
    }

    public Future<List<CashierYearSales>> getYearlyCashierByCashierId(YearCashierIdRequest req) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(req.getYear(), 1, 1, 0, 0));
        return client
                .preparedQuery("""
                        WITH last_five_years AS (
                            SELECT c.cashier_id, c.name AS cashier_name, EXTRACT(YEAR FROM o.created_at)::text AS year,
                                   COUNT(o.order_id) AS order_count, SUM(o.total_price) AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL AND c.cashier_id = $2
                              AND EXTRACT(YEAR FROM o.created_at) BETWEEN (EXTRACT(YEAR FROM $1::timestamp) - 4) AND EXTRACT(YEAR FROM $1::timestamp)
                            GROUP BY c.cashier_id, c.name, EXTRACT(YEAR FROM o.created_at)
                        )
                        SELECT year, cashier_id, cashier_name, order_count, total_sales
                        FROM last_five_years ORDER BY year, cashier_id;
                        """)
                .execute(Tuple.of(refTs, req.getCashierId().longValue()))
                .map(this::mapCashierYearSales);
    }

    public Future<List<CashierMonthSales>> getMonthlyCashierByMerchant(MonthCashierMerchantRequest req) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(req.getYear(), 1, 1, 0, 0));
        return client
                .preparedQuery("""
                         WITH date_range AS (
                            SELECT date_trunc('month', $1::timestamp) AS start_date, date_trunc('month', $1::timestamp) + interval '1 year' - interval '1 day' AS end_date
                        ), cashier_activity AS (
                            SELECT c.cashier_id, c.name AS cashier_name, date_trunc('month', o.created_at) AS activity_month,
                                   COUNT(o.order_id) AS order_count, SUM(o.total_price) AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL AND c.merchant_id = $2
                              AND o.created_at BETWEEN (SELECT start_date FROM date_range) AND (SELECT end_date FROM date_range)
                            GROUP BY c.cashier_id, c.name, activity_month
                        )
                        SELECT ca.cashier_id, ca.cashier_name, TO_CHAR(ca.activity_month, 'Mon') AS month, ca.order_count, ca.total_sales
                        FROM cashier_activity ca ORDER BY ca.activity_month, ca.cashier_id;
                        """)
                .execute(Tuple.of(refTs, req.getMerchantId().longValue()))
                .map(this::mapCashierMonthSales);
    }

    public Future<List<CashierYearSales>> getYearlyCashierByMerchant(YearCashierMerchantRequest req) {
        Timestamp refTs = Timestamp.valueOf(LocalDateTime.of(req.getYear(), 1, 1, 0, 0));
        return client
                .preparedQuery("""
                        WITH last_five_years AS (
                            SELECT c.cashier_id, c.name AS cashier_name, EXTRACT(YEAR FROM o.created_at)::text AS year,
                                   COUNT(o.order_id) AS order_count, SUM(o.total_price) AS total_sales
                            FROM orders o JOIN cashiers c ON o.cashier_id = c.cashier_id
                            WHERE o.deleted_at IS NULL AND c.deleted_at IS NULL AND c.merchant_id = $2
                              AND EXTRACT(YEAR FROM o.created_at) BETWEEN (EXTRACT(YEAR FROM $1::timestamp) - 4) AND EXTRACT(YEAR FROM $1::timestamp)
                            GROUP BY c.cashier_id, c.name, EXTRACT(YEAR FROM o.created_at)
                        )
                        SELECT year, cashier_id, cashier_name, order_count, total_sales
                        FROM last_five_years ORDER BY year, cashier_id;
                        """)
                .execute(Tuple.of(refTs, req.getMerchantId().longValue()))
                .map(this::mapCashierYearSales);
    }


    private String normalizeSearch(String search) {
        if (search == null || search.isBlank())
            return null;
        return search;
    }

     private Tuple getMonthlyTuple(int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        LocalDate startPrevDate = startDate.minusYears(1);
        LocalDate endPrevDate = startPrevDate.withDayOfMonth(startPrevDate.lengthOfMonth());
        return Tuple.of(startDate, endDate, startPrevDate, endPrevDate);
    }

    private PagedResult<Cashier> mapPagedCashiers(RowSet<Row> rows) {
        List<Cashier> list = new ArrayList<>();
        int total = 0;
        for (Row row : rows) {
            list.add(Cashier.fromRow(row));
            if (total == 0)
                total = row.getInteger("total_count");
        }
        return new PagedResult<>(list, total);
    }

    private List<CashierMonthTotalSales> mapCashierMonthTotalSales(RowSet<Row> rows) {
        List<CashierMonthTotalSales> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new CashierMonthTotalSales(
                    r.getString("year"),
                    r.getString("month"),
                    r.getLong("total_sales")));
        }
        return list;
    }

    private List<CashierYearTotalSales> mapCashierYearTotalSales(RowSet<Row> rows) {
        List<CashierYearTotalSales> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new CashierYearTotalSales(
                    r.getString("year"),
                    r.getLong("total_sales")));
        }
        return list;
    }

    private List<CashierMonthSales> mapCashierMonthSales(RowSet<Row> rows) {
        List<CashierMonthSales> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new CashierMonthSales(
                    r.getString("month"),
                    r.getInteger("cashier_id"),
                    r.getString("cashier_name"),
                    r.getInteger("order_count"),
                    r.getLong("total_sales")));
        }
        return list;
    }

    private List<CashierYearSales> mapCashierYearSales(RowSet<Row> rows) {
        List<CashierYearSales> list = new ArrayList<>();
        for (Row r : rows) {
            list.add(new CashierYearSales(
                    r.getString("year"),
                    r.getInteger("cashier_id"),
                    r.getString("cashier_name"),
                    r.getInteger("order_count"),
                    r.getLong("total_sales")));
        }
        return list;
    }
}