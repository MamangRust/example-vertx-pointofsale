package com.sanedge.example_crud.repository;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.example_crud.domain.requests.order.FindAllOrderRequest;
import com.sanedge.example_crud.domain.requests.order_item.CreateOrderItemRecordRequest;
import com.sanedge.example_crud.domain.requests.order_item.UpdateOrderItemRecordRequest;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.model.order.OrderItem;

import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderItemRepository {
    private final Pool client;

    public Future<PagedResult<OrderItem>> getOrderItems(FindAllOrderRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at,
                            COUNT(*) OVER () AS total_count
                        FROM order_items
                        WHERE
                            deleted_at IS NULL
                            AND (
                                $1::TEXT IS NULL
                                OR order_id::TEXT ILIKE '%' || $1 || '%'
                                OR product_id::TEXT ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedOrderItems);
    }

    public Future<PagedResult<OrderItem>> getOrderItemsActive(FindAllOrderRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at,
                            deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM order_items
                        WHERE
                            deleted_at IS NULL
                            AND (
                                $1::TEXT IS NULL
                                OR order_id::TEXT ILIKE '%' || $1 || '%'
                                OR product_id::TEXT ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedOrderItems);
    }

    public Future<PagedResult<OrderItem>> getOrderItemsTrashed(FindAllOrderRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at,
                            deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM order_items
                        WHERE
                            deleted_at IS NOT NULL
                            AND (
                                $1::TEXT IS NULL
                                OR order_id::TEXT ILIKE '%' || $1 || '%'
                                OR product_id::TEXT ILIKE '%' || $1 || '%'
                            )
                        ORDER BY deleted_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedOrderItems);
    }

    public Future<Integer> calculateTotalPrice(Long orderId) {
        return client
                .preparedQuery("""
                        SELECT COALESCE(SUM(quantity * price), 0)::int AS total_price
                        FROM order_items
                        WHERE
                            order_id = $1
                            AND deleted_at IS NULL;
                        """)
                .execute(Tuple.of(orderId))
                .map(rows -> {
                    if (rows.iterator().hasNext()) {
                        return rows.iterator().next().getInteger("total_price");
                    }
                    return 0;
                });
    }

    public Future<List<OrderItem>> getOrderItemsByOrder(Long orderId) {
        return client
                .preparedQuery("""
                        SELECT
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at
                        FROM order_items
                        WHERE
                            order_id = $1
                            AND deleted_at IS NULL;
                        """)
                .execute(Tuple.of(orderId))
                .map(rows -> {
                    List<OrderItem> items = new ArrayList<>();
                    rows.forEach(row -> items.add(OrderItem.fromRow(row)));
                    return items;
                });
    }

    public Future<OrderItem> createOrderItem(CreateOrderItemRecordRequest req) {
        return client
                .preparedQuery("""
                        INSERT INTO
                            order_items (
                                order_id,
                                product_id,
                                quantity,
                                price
                            )
                        VALUES ($1, $2, $3, $4)
                        RETURNING
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at;
                        """)
                .execute(Tuple.of(req.getOrderId(), req.getProductId(), req.getQuantity(), req.getPrice()))
                .map(rows -> OrderItem.fromRow(rows.iterator().next()));
    }

    public Future<OrderItem> updateOrderItem(UpdateOrderItemRecordRequest req) {
        return client
                .preparedQuery("""
                        UPDATE order_items
                        SET
                            quantity = $2,
                            price = $3,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE
                            order_item_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at;
                        """)
                .execute(Tuple.of(req.getOrderItemId(), req.getQuantity(), req.getPrice()))
                .map(rows -> rows.iterator().hasNext() ? OrderItem.fromRow(rows.iterator().next()) : null);
    }

    public Future<OrderItem> trashOrderItem(Long orderItemId) {
        return client
                .preparedQuery("""
                        UPDATE order_items
                        SET
                            deleted_at = current_timestamp
                        WHERE
                            order_item_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at,
                            deleted_at;
                        """)
                .execute(Tuple.of(orderItemId))
                .map(rows -> rows.iterator().hasNext() ? OrderItem.fromRow(rows.iterator().next()) : null);
    }

    public Future<OrderItem> restoreOrderItem(Long orderItemId) {
        return client
                .preparedQuery("""
                        UPDATE order_items
                        SET
                            deleted_at = NULL
                        WHERE
                            order_item_id = $1
                            AND deleted_at IS NOT NULL
                        RETURNING
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at,
                            deleted_at;
                        """)
                .execute(Tuple.of(orderItemId))
                .map(rows -> rows.iterator().hasNext() ? OrderItem.fromRow(rows.iterator().next()) : null);
    }

    public Future<Void> deleteOrderItemPermanently(Long orderItemId) {
        return client
                .preparedQuery("DELETE FROM order_items WHERE order_item_id = $1 AND deleted_at IS NOT NULL")
                .execute(Tuple.of(orderItemId))
                .mapEmpty();
    }

    public Future<OrderItem> findByTrashed(Long orderItemId) {
        return client
                .preparedQuery("""
                        SELECT
                            order_item_id,
                            order_id,
                            product_id,
                            quantity,
                            price,
                            created_at,
                            updated_at,
                            deleted_at
                        FROM order_items
                        WHERE
                            order_item_id = $1
                            AND deleted_at IS NOT NULL;
                        """)
                .execute(Tuple.of(orderItemId))
                .map(rows -> rows.iterator().hasNext() ? OrderItem.fromRow(rows.iterator().next()) : null);
    }

    public Future<Integer> restoreAllOrdersItem() {
        return client
                .preparedQuery("UPDATE order_items SET deleted_at = NULL WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }

    public Future<Integer> deleteAllPermanentOrdersItem() {
        return client
                .preparedQuery("DELETE FROM order_items WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank())
            return null;
        return search;
    }

    private PagedResult<OrderItem> mapPagedOrderItems(RowSet<Row> rows) {
        List<OrderItem> list = new ArrayList<>();
        int total = 0;
        for (Row row : rows) {
            list.add(OrderItem.fromRow(row));
            if (total == 0)
                total = row.getInteger("total_count");
        }
        return new PagedResult<>(list, total);
    }
}