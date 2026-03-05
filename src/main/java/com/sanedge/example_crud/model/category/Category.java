package com.sanedge.example_crud.model.category;

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
public class Category {
    private Long categoryId;
    private String name;
    private String description;
    private String slugCategory;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("categoryId", categoryId)
                .put("name", name)
                .put("description", description)
                .put("slugCategory", slugCategory);

        if (createdAt != null)
            json.put("createdAt", createdAt.toString());
        if (updatedAt != null)
            json.put("updatedAt", updatedAt.toString());
        if (deletedAt != null)
            json.put("deletedAt", deletedAt.toString());

        return json;
    }

    public static Category fromJson(JsonObject json) {
        if (json == null)
            return null;

        Category category = new Category();
        category.setCategoryId(json.getLong("categoryId"));
        category.setName(json.getString("name"));
        category.setDescription(json.getString("description"));
        category.setSlugCategory(json.getString("slugCategory"));

        category.setCreatedAt(parseTimestamp(json, "createdAt"));
        category.setUpdatedAt(parseTimestamp(json, "updatedAt"));
        category.setDeletedAt(parseTimestamp(json, "deletedAt"));

        return category;
    }

    public static Category fromRow(Row row) {
        if (row == null)
            return null;

        return Category.builder()
                .categoryId(row.getLong("category_id"))
                .name(row.getString("name"))
                .description(row.getString("description"))
                .slugCategory(row.getString("slug_category"))
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