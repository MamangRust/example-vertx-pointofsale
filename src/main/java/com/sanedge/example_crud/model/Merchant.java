package com.sanedge.example_crud.model;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import com.sanedge.example_crud.enums.Status;

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
public class Merchant {
    private Long merchantId;
    private Long userId;
    private String name;
    private String description;
    private String address;
    private String contactEmail;
    private String contactPhone;
    private Status status;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("merchantId", merchantId)
                .put("userId", userId)
                .put("name", name)
                .put("description", description)
                .put("address", address)
                .put("contactEmail", contactEmail)
                .put("contactPhone", contactPhone)
                .put("status", status != null ? status.name() : null);

        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
        if (deletedAt != null) json.put("deletedAt", deletedAt.toString());

        return json;
    }

    public static Merchant fromJson(JsonObject json) {
        if (json == null) return null;

        Merchant merchant = new Merchant();
        merchant.setMerchantId(json.getLong("merchantId"));
        merchant.setUserId(json.getLong("userId"));
        merchant.setName(json.getString("name"));
        merchant.setDescription(json.getString("description"));
        merchant.setAddress(json.getString("address"));
        merchant.setContactEmail(json.getString("contactEmail"));
        merchant.setContactPhone(json.getString("contactPhone"));
        
        String statusStr = json.getString("status");
        if (statusStr != null) {
            try { merchant.setStatus(Status.valueOf(statusStr.toUpperCase())); } 
            catch (IllegalArgumentException e) { merchant.setStatus(Status.PENDING); }
        }

        merchant.setCreatedAt(parseTimestamp(json, "createdAt"));
        merchant.setUpdatedAt(parseTimestamp(json, "updatedAt"));
        merchant.setDeletedAt(parseTimestamp(json, "deletedAt"));

        return merchant;
    }

    public static Merchant fromRow(Row row) {
        if (row == null) return null;

        Status status = null;
        String statusStr = row.getString("status");
        if (statusStr != null) {
            try { status = Status.valueOf(statusStr.toUpperCase()); } 
            catch (IllegalArgumentException e) { status = Status.PENDING; }
        }

        return Merchant.builder()
                .merchantId(row.getLong("merchant_id"))
                .userId(row.getLong("user_id"))
                .name(row.getString("name"))
                .description(row.getString("description"))
                .address(row.getString("address"))
                .contactEmail(row.getString("contact_email"))
                .contactPhone(row.getString("contact_phone"))
                .status(status)
                .createdAt(row.get(LocalDateTime.class, "created_at") != null ? Timestamp.valueOf(row.get(LocalDateTime.class, "created_at")) : null)
                .updatedAt(row.get(LocalDateTime.class, "updated_at") != null ? Timestamp.valueOf(row.get(LocalDateTime.class, "updated_at")) : null)
                .deletedAt(row.get(LocalDateTime.class, "deleted_at") != null ? Timestamp.valueOf(row.get(LocalDateTime.class, "deleted_at")) : null)
                .build();
    }

    private static Timestamp parseTimestamp(JsonObject json, String field) {
        Object value = json.getValue(field);
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts;
        if (value instanceof String str && !str.isBlank()) {
            try { return Timestamp.from(Instant.parse(str)); } catch (DateTimeParseException e) { return null; }
        }
        if (value instanceof Number num) return new Timestamp(num.longValue());
        return null;
    }
}