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
public class OrderItem {
    private Long orderItemId;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private Integer price;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("orderItemId", orderItemId)
                .put("orderId", orderId)
                .put("productId", productId)
                .put("quantity", quantity)
                .put("price", price);

        if (createdAt != null)
            json.put("createdAt", createdAt.toString());
        if (updatedAt != null)
            json.put("updatedAt", updatedAt.toString());
        if (deletedAt != null)
            json.put("deletedAt", deletedAt.toString());

        return json;
    }

    public static OrderItem fromJson(JsonObject json) {
        if (json == null)
            return null;

        OrderItem item = new OrderItem();
        item.setOrderItemId(json.getLong("orderItemId"));
        item.setOrderId(json.getLong("orderId"));
        item.setProductId(json.getLong("productId"));
        item.setQuantity(json.getInteger("quantity"));
        item.setPrice(json.getInteger("price"));

        item.setCreatedAt(parseTimestamp(json, "createdAt"));
        item.setUpdatedAt(parseTimestamp(json, "updatedAt"));
        item.setDeletedAt(parseTimestamp(json, "deletedAt"));

        return item;
    }

    public static OrderItem fromRow(Row row) {
        if (row == null)
            return null;

        return OrderItem.builder()
                .orderItemId(row.getLong("order_item_id"))
                .orderId(row.getLong("order_id"))
                .productId(row.getLong("product_id"))
                .quantity(row.getInteger("quantity"))
                .price(row.getInteger("price"))
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