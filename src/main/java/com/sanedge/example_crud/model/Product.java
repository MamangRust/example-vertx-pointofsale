package com.sanedge.example_crud.model;

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
public class Product {
    private Long productId;
    private Long merchantId;
    private Long categoryId;
    private String name;
    private String description;
    private Integer price;
    private Integer countInStock;
    private String brand;
    private Integer weight;
    private String slugProduct;
    private String imageProduct;
    private String barcode;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("productId", productId)
                .put("merchantId", merchantId)
                .put("categoryId", categoryId)
                .put("name", name)
                .put("description", description)
                .put("price", price)
                .put("countInStock", countInStock)
                .put("brand", brand)
                .put("weight", weight)
                .put("slugProduct", slugProduct)
                .put("imageProduct", imageProduct)
                .put("barcode", barcode);

        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
        if (deletedAt != null) json.put("deletedAt", deletedAt.toString());

        return json;
    }

    public static Product fromJson(JsonObject json) {
        if (json == null) return null;

        Product product = new Product();
        product.setProductId(json.getLong("productId"));
        product.setMerchantId(json.getLong("merchantId"));
        product.setCategoryId(json.getLong("categoryId"));
        product.setName(json.getString("name"));
        product.setDescription(json.getString("description"));
        product.setPrice(json.getInteger("price"));
        product.setCountInStock(json.getInteger("countInStock"));
        product.setBrand(json.getString("brand"));
        product.setWeight(json.getInteger("weight"));
        product.setSlugProduct(json.getString("slugProduct"));
        product.setImageProduct(json.getString("imageProduct"));
        product.setBarcode(json.getString("barcode"));

        product.setCreatedAt(parseTimestamp(json, "createdAt"));
        product.setUpdatedAt(parseTimestamp(json, "updatedAt"));
        product.setDeletedAt(parseTimestamp(json, "deletedAt"));

        return product;
    }

    public static Product fromRow(Row row) {
        if (row == null) return null;

        return Product.builder()
                .productId(row.getLong("product_id"))
                .merchantId(row.getLong("merchant_id"))
                .categoryId(row.getLong("category_id"))
                .name(row.getString("name"))
                .description(row.getString("description"))
                .price(row.getInteger("price"))
                .countInStock(row.getInteger("count_in_stock"))
                .brand(row.getString("brand"))
                .weight(row.getInteger("weight"))
                .slugProduct(row.getString("slug_product"))
                .imageProduct(row.getString("image_product"))
                .barcode(row.getString("barcode"))
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