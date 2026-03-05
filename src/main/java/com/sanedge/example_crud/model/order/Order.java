package com.sanedge.example_crud.model.order;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {
    private Long orderId;
    private Long merchantId;
    private Long cashierId;
    private Long totalPrice;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("orderId", orderId)
                .put("merchantId", merchantId)
                .put("cashierId", cashierId)
                .put("totalPrice", totalPrice);

        if (createdAt != null)
            json.put("createdAt", createdAt.toString());
        if (updatedAt != null)
            json.put("updatedAt", updatedAt.toString());
        if (deletedAt != null)
            json.put("deletedAt", deletedAt.toString());

        return json;
    }

    public static Order fromJson(JsonObject json) {
        if (json == null)
            return null;

        Order order = new Order();
        order.setOrderId(json.getLong("orderId"));
        order.setMerchantId(json.getLong("merchantId"));
        order.setCashierId(json.getLong("cashierId"));
        order.setTotalPrice(json.getLong("totalPrice"));

        order.setCreatedAt(parseTimestamp(json, "createdAt"));
        order.setUpdatedAt(parseTimestamp(json, "updatedAt"));
        order.setDeletedAt(parseTimestamp(json, "deletedAt"));

        return order;
    }

    public static Order fromRow(Row row) {
        if (row == null)
            return null;

        return Order.builder()
                .orderId(row.getLong("order_id"))
                .merchantId(row.getLong("merchant_id"))
                .cashierId(row.getLong("cashier_id"))
                .totalPrice(row.getLong("total_price"))
                .createdAt(row.get(LocalDateTime.class, "created_at") != null
                        ? Timestamp.valueOf(row.get(LocalDateTime.class, "created_at"))
                        : null)
                .updatedAt(row.get(LocalDateTime.class, "updated_at") != null
                        ? Timestamp.valueOf(row.get(LocalDateTime.class, "updated_at"))
                        : null)
                .deletedAt(row.get(LocalDateTime.class, "deleted_at") != null
                        ? Timestamp.valueOf(row.get(LocalDateTime.class, "deleted_at"))
                        : null)
                .build();
    }

    private static Timestamp parseTimestamp(JsonObject json, String field) {
        Object value = json.getValue(field);
        if (value == null)
            return null;
        if (value instanceof Timestamp ts)
            return ts;
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Timestamp.from(Instant.parse(str));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        if (value instanceof Number num)
            return new Timestamp(num.longValue());
        return null;
    }
}