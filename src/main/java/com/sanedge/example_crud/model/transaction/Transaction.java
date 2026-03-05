package com.sanedge.example_crud.model.transaction;


import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import com.sanedge.example_crud.enums.PaymentStatus;

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
public class Transaction {
    private Long transactionId;
    private Long orderId;
    private Long merchantId;
    private String paymentMethod;
    private Integer amount;
    private Integer changeAmount;
    private PaymentStatus status;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("transactionId", transactionId)
                .put("orderId", orderId)
                .put("merchantId", merchantId)
                .put("paymentMethod", paymentMethod)
                .put("amount", amount)
                .put("changeAmount", changeAmount)
                .put("status", status != null ? status.name() : null);

        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
        if (deletedAt != null) json.put("deletedAt", deletedAt.toString());

        return json;
    }

    public static Transaction fromJson(JsonObject json) {
        if (json == null) return null;

        Transaction t = new Transaction();
        t.setTransactionId(json.getLong("transactionId"));
        t.setOrderId(json.getLong("orderId"));
        t.setMerchantId(json.getLong("merchantId"));
        t.setPaymentMethod(json.getString("paymentMethod"));
        t.setAmount(json.getInteger("amount"));
        t.setChangeAmount(json.getInteger("changeAmount"));
        
        String statusStr = json.getString("status");
        if (statusStr != null) {
            try { t.setStatus(PaymentStatus.valueOf(statusStr.toUpperCase())); } 
            catch (IllegalArgumentException e) { t.setStatus(PaymentStatus.PENDING); }
        }

        t.setCreatedAt(parseTimestamp(json, "createdAt"));
        t.setUpdatedAt(parseTimestamp(json, "updatedAt"));
        t.setDeletedAt(parseTimestamp(json, "deletedAt"));

        return t;
    }

    public static Transaction fromRow(Row row) {
        if (row == null) return null;

        PaymentStatus status = null;
        String statusStr = row.getString("status");
        if (statusStr != null) {
            try { status = PaymentStatus.valueOf(statusStr.toUpperCase()); } 
            catch (IllegalArgumentException e) { status = PaymentStatus.PENDING; }
        }

        return Transaction.builder()
                .transactionId(row.getLong("transaction_id"))
                .orderId(row.getLong("order_id"))
                .merchantId(row.getLong("merchant_id"))
                .paymentMethod(row.getString("payment_method"))
                .amount(row.getInteger("amount"))
                .changeAmount(row.getInteger("change_amount"))
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