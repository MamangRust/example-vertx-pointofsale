package com.sanedge.example_crud.model.cashier;

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
public class Cashier {
    private Long cashierId;
    private Long merchantId;
    private Long userId;
    private String name;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("cashierId", cashierId)
                .put("merchantId", merchantId)
                .put("userId", userId)
                .put("name", name);

        if (createdAt != null)
            json.put("createdAt", createdAt.toString());
        if (updatedAt != null)
            json.put("updatedAt", updatedAt.toString());
        if (deletedAt != null)
            json.put("deletedAt", deletedAt.toString());

        return json;
    }

    public static Cashier fromJson(JsonObject json) {
        if (json == null)
            return null;

        Cashier cashier = new Cashier();
        cashier.setCashierId(json.getLong("cashierId"));
        cashier.setMerchantId(json.getLong("merchantId"));
        cashier.setUserId(json.getLong("userId"));
        cashier.setName(json.getString("name"));

        cashier.setCreatedAt(parseTimestamp(json, "createdAt"));
        cashier.setUpdatedAt(parseTimestamp(json, "updatedAt"));
        cashier.setDeletedAt(parseTimestamp(json, "deletedAt"));

        return cashier;
    }

    public static Cashier fromRow(Row row) {
        if (row == null)
            return null;

        return Cashier.builder()
                .cashierId(row.getLong("cashier_id"))
                .merchantId(row.getLong("merchant_id"))
                .userId(row.getLong("user_id"))
                .name(row.getString("name"))
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