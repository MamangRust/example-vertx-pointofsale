package com.sanedge.example_crud.repository;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.example_crud.domain.requests.product.CreateProductRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductByCategoryRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductByMerchantRequest;
import com.sanedge.example_crud.domain.requests.product.FindAllProductRequest;
import com.sanedge.example_crud.domain.requests.product.UpdateProductRequest;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.model.Product;

import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductRepository {
    private final Pool client;

    public Future<PagedResult<Product>> getProducts(FindAllProductRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            p.product_id,
                            p.merchant_id,
                            p.category_id,
                            p.name,
                            p.description,
                            p.price,
                            p.count_in_stock,
                            p.brand,
                            p.weight,
                            p.slug_product,
                            p.image_product,
                            p.barcode,
                            p.created_at,
                            p.updated_at,
                            COUNT(*) OVER () AS total_count
                        FROM products as p
                        WHERE
                            deleted_at IS NULL
                            AND (
                                $1::TEXT IS NULL
                                OR p.name ILIKE '%' || $1 || '%'
                                OR p.description ILIKE '%' || $1 || '%'
                                OR p.brand ILIKE '%' || $1 || '%'
                                OR p.slug_product ILIKE '%' || $1 || '%'
                                OR p.barcode ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedProducts);
    }

    public Future<PagedResult<Product>> getProductsActive(FindAllProductRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            p.product_id,
                            p.merchant_id,
                            p.category_id,
                            p.name,
                            p.description,
                            p.price,
                            p.count_in_stock,
                            p.brand,
                            p.weight,
                            p.slug_product,
                            p.image_product,
                            p.barcode,
                            p.created_at,
                            p.updated_at,
                            p.deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM products as p
                        WHERE
                            deleted_at IS NULL
                            AND (
                                $1::TEXT IS NULL
                                OR p.name ILIKE '%' || $1 || '%'
                                OR p.description ILIKE '%' || $1 || '%'
                                OR p.brand ILIKE '%' || $1 || '%'
                                OR p.slug_product ILIKE '%' || $1 || '%'
                                OR p.barcode ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedProducts);
    }

    public Future<PagedResult<Product>> getProductsTrashed(FindAllProductRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            p.product_id,
                            p.merchant_id,
                            p.category_id,
                            p.name,
                            p.description,
                            p.price,
                            p.count_in_stock,
                            p.brand,
                            p.weight,
                            p.slug_product,
                            p.image_product,
                            p.barcode,
                            p.created_at,
                            p.updated_at,
                            p.deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM products as p
                        WHERE
                            deleted_at IS NOT NULL
                            AND (
                                $1::TEXT IS NULL
                                OR p.name ILIKE '%' || $1 || '%'
                                OR p.description ILIKE '%' || $1 || '%'
                                OR p.brand ILIKE '%' || $1 || '%'
                                OR p.slug_product ILIKE '%' || $1 || '%'
                                OR p.barcode ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedProducts);
    }

    public Future<PagedResult<Product>> getProductsByMerchant(FindAllProductByMerchantRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();

        Integer catIdVal = req.getCategoryId() != null && req.getCategoryId() > 0 ? req.getCategoryId() : null;
        Integer minPriceVal = req.getMinPrice() != null && req.getMinPrice() > 0 ? req.getMinPrice() : null;
        Integer maxPriceVal = req.getMaxPrice() != null && req.getMaxPrice() > 0 ? req.getMaxPrice() : null;

        return client
                .preparedQuery("""
                        WITH filtered_products AS (
                            SELECT
                                p.product_id,
                                p.merchant_id,
                                p.category_id,
                                p.name,
                                p.description,
                                p.price,
                                p.count_in_stock,
                                p.brand,
                                p.weight,
                                p.slug_product,
                                p.image_product,
                                p.barcode,
                                p.created_at,
                                p.updated_at,
                                c.name AS category_name
                            FROM products p
                                JOIN categories c ON p.category_id = c.category_id
                            WHERE
                                p.deleted_at IS NULL
                                AND p.merchant_id = $1
                                AND (
                                    p.name ILIKE '%' || COALESCE($2, '') || '%'
                                    OR p.description ILIKE '%' || COALESCE($2, '') || '%'
                                    OR $2 IS NULL
                                )
                                AND (
                                    c.category_id = NULLIF($3, 0)
                                    OR NULLIF($3, 0) IS NULL
                                )
                                AND (
                                    p.price >= COALESCE(NULLIF($4, 0), 0)
                                    AND p.price <= COALESCE(NULLIF($5, 0), 999999999)
                                )
                        )
                        SELECT (SELECT COUNT(*) FROM filtered_products) AS total_count, fp.*
                        FROM filtered_products fp
                        ORDER BY fp.created_at DESC
                        LIMIT $6
                        OFFSET $7;
                        """)
                .execute(Tuple.of(req.getMerchantId().longValue(), req.getSearch(), catIdVal, minPriceVal, maxPriceVal, req.getPageSize(), offset))
                .map(this::mapPagedProducts);
    }

    public Future<PagedResult<Product>> getProductsByCategoryName(FindAllProductByCategoryRequest req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        WITH filtered_products AS (
                            SELECT
                                p.product_id,
                                p.merchant_id,
                                p.category_id,
                                p.slug_product,
                                p.weight,
                                p.name,
                                p.description,
                                p.price,
                                p.count_in_stock,
                                p.brand,
                                p.image_product,
                                p.barcode,
                                p.created_at,
                                p.updated_at,
                                p.deleted_at,
                                c.name AS category_name
                            FROM products p
                                JOIN categories c ON p.category_id = c.category_id
                            WHERE
                                p.deleted_at IS NULL
                                AND c.name = $1
                                AND (
                                    $2 IS NULL
                                    OR p.name ILIKE '%' || $2 || '%'
                                    OR p.description ILIKE '%' || $2 || '%'
                                )
                                AND (
                                    ($3 IS NULL OR p.price >= $3)
                                    AND ($4 IS NULL OR p.price <= $4)
                                )
                        )
                        SELECT (SELECT COUNT(*) FROM filtered_products) AS total_count, fp.*
                        FROM filtered_products fp
                        ORDER BY fp.created_at DESC
                        LIMIT $5
                        OFFSET $6;
                        """)
                .execute(Tuple.of(req.getCategoryName(), req.getSearch(), req.getMinPrice(), req.getMaxPrice(), req.getPageSize(), offset))
                .map(this::mapPagedProducts);
    }

    public Future<Product> getProductById(Long productId) {
        return client
                .preparedQuery("""
                        SELECT
                            product_id,
                            merchant_id,
                            category_id,
                            name,
                            description,
                            price,
                            count_in_stock,
                            brand,
                            weight,
                            slug_product,
                            image_product,
                            barcode,
                            created_at,
                            updated_at
                        FROM products
                        WHERE
                            product_id = $1
                            AND deleted_at IS NULL;
                        """)
                .execute(Tuple.of(productId))
                .map(rows -> rows.iterator().hasNext() ? Product.fromRow(rows.iterator().next()) : null);
    }

    public Future<Product> createProduct(CreateProductRequest req) {
        return client
                .preparedQuery("""
                        INSERT INTO
                            products (
                                merchant_id,
                                category_id,
                                name,
                                description,
                                price,
                                count_in_stock,
                                brand,
                                weight,
                                slug_product,
                                image_product
                            )
                        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
                        RETURNING
                            product_id,
                            merchant_id,
                            category_id,
                            name,
                            description,
                            price,
                            count_in_stock,
                            brand,
                            weight,
                            slug_product,
                            image_product,
                            created_at,
                            updated_at;
                        """)
                .execute(Tuple.of(
                        req.getMerchantId() != null ? req.getMerchantId().longValue() : null,
                        req.getCategoryId() != null ? req.getCategoryId().longValue() : null,
                        req.getName(),
                        req.getDescription(),
                        req.getPrice(),
                        req.getCountInStock(),
                        req.getBrand(),
                        req.getWeight(),
                        req.getSlugProduct(),
                        req.getImageProduct()
                ))
                .map(rows -> Product.fromRow(rows.iterator().next()));
    }

    public Future<Product> updateProduct(UpdateProductRequest req) {
        return client
                .preparedQuery("""
                        UPDATE products
                        SET
                            category_id = $2,
                            name = $3,
                            description = $4,
                            price = $5,
                            count_in_stock = $6,
                            brand = $7,
                            weight = $8,
                            image_product = $9,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE
                            product_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            product_id,
                            merchant_id,
                            category_id,
                            name,
                            description,
                            price,
                            count_in_stock,
                            brand,
                            weight,
                            slug_product,
                            image_product,
                            created_at,
                            updated_at;
                        """)
                .execute(Tuple.of(
                        req.getProductId() != null ? req.getProductId().longValue() : null,
                        req.getCategoryId() != null ? req.getCategoryId().longValue() : null,
                        req.getName(),
                        req.getDescription(),
                        req.getPrice(),
                        req.getCountInStock(),
                        req.getBrand(),
                        req.getWeight(),
                        req.getImageProduct()
                ))
                .map(rows -> rows.iterator().hasNext() ? Product.fromRow(rows.iterator().next()) : null);
    }

    public Future<Product> updateProductCountStock(Long productId, Integer countInStock) {
        return client
                .preparedQuery("""
                        UPDATE products
                        SET
                            count_in_stock = $2
                        WHERE
                            product_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            product_id,
                            price,
                            count_in_stock;
                        """)
                .execute(Tuple.of(productId, countInStock))
                .map(rows -> {
                    if (rows.iterator().hasNext()) {
                        Row r = rows.iterator().next();
                        Product p = new Product();
                        p.setProductId(r.getLong("product_id"));
                        p.setPrice(r.getInteger("price"));
                        p.setCountInStock(r.getInteger("count_in_stock"));
                        return p;
                    }
                    return null;
                });
    }

    public Future<Product> trashProduct(Long productId) {
        return client
                .preparedQuery("""
                        UPDATE products
                        SET
                            deleted_at = current_timestamp
                        WHERE
                            product_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            product_id,
                            merchant_id,
                            category_id,
                            name,
                            description,
                            price,
                            count_in_stock,
                            brand,
                            weight,
                            slug_product,
                            image_product,
                            barcode,
                            created_at,
                            updated_at,
                            deleted_at;
                        """)
                .execute(Tuple.of(productId))
                .map(rows -> rows.iterator().hasNext() ? Product.fromRow(rows.iterator().next()) : null);
    }

    public Future<Product> restoreProduct(Long productId) {
        return client
                .preparedQuery("""
                        UPDATE products
                        SET
                            deleted_at = NULL
                        WHERE
                            product_id = $1
                            AND deleted_at IS NOT NULL
                        RETURNING
                            product_id,
                            merchant_id,
                            category_id,
                            name,
                            description,
                            price,
                            count_in_stock,
                            brand,
                            weight,
                            slug_product,
                            image_product,
                            barcode,
                            created_at,
                            updated_at,
                            deleted_at;
                        """)
                .execute(Tuple.of(productId))
                .map(rows -> rows.iterator().hasNext() ? Product.fromRow(rows.iterator().next()) : null);
    }

    public Future<Void> deleteProductPermanently(Long productId) {
        return client
                .preparedQuery("DELETE FROM products WHERE product_id = $1 AND deleted_at IS NOT NULL")
                .execute(Tuple.of(productId))
                .mapEmpty();
    }

    public Future<Integer> restoreAllProducts() {
        return client
                .preparedQuery("UPDATE products SET deleted_at = NULL WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }

    public Future<Integer> deleteAllPermanentProducts() {
        return client
                .preparedQuery("DELETE FROM products WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }


    private String normalizeSearch(String search) {
        if (search == null || search.isBlank())
            return null;
        return search;
    }

    private PagedResult<Product> mapPagedProducts(RowSet<Row> rows) {
        List<Product> list = new ArrayList<>();
        int total = 0;
        for (Row row : rows) {
            list.add(Product.fromRow(row));
            if (total == 0)
                total = row.getInteger("total_count");
        }
        return new PagedResult<>(list, total);
    }
}