package com.sanedge.example_crud.repository;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.example_crud.domain.requests.merchant.CreateMerchantRequest;
import com.sanedge.example_crud.domain.requests.merchant.FindAllMerchants;
import com.sanedge.example_crud.domain.requests.merchant.UpdateMerchantRequest;
import com.sanedge.example_crud.domain.response.api.PagedResult;
import com.sanedge.example_crud.model.Merchant;

import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MerchantRepository {
    private final Pool client;

    public Future<PagedResult<Merchant>> getMerchants(FindAllMerchants req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            merchant_id,
                            user_id,
                            name,
                            description,
                            address,
                            contact_email,
                            contact_phone,
                            status,
                            created_at,
                            updated_at,
                            COUNT(*) OVER () AS total_count
                        FROM merchants
                        WHERE
                            deleted_at IS NULL
                            AND (
                                $1::TEXT IS NULL
                                OR name ILIKE '%' || $1 || '%'
                                OR contact_email ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedMerchants);
    }

    public Future<PagedResult<Merchant>> getMerchantsActive(FindAllMerchants req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            merchant_id,
                            user_id,
                            name,
                            description,
                            address,
                            contact_email,
                            contact_phone,
                            status,
                            created_at,
                            updated_at,
                            deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM merchants
                        WHERE
                            deleted_at IS NULL
                            AND (
                                $1::TEXT IS NULL
                                OR name ILIKE '%' || $1 || '%'
                                OR contact_email ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedMerchants);
    }

    public Future<PagedResult<Merchant>> getMerchantsTrashed(FindAllMerchants req) {
        int offset = (req.getPage() > 0 ? req.getPage() - 1 : 0) * req.getPageSize();
        return client
                .preparedQuery("""
                        SELECT
                            merchant_id,
                            user_id,
                            name,
                            description,
                            address,
                            contact_email,
                            contact_phone,
                            status,
                            created_at,
                            updated_at,
                            deleted_at,
                            COUNT(*) OVER () AS total_count
                        FROM merchants
                        WHERE
                            deleted_at IS NOT NULL
                            AND (
                                $1::TEXT IS NULL
                                OR name ILIKE '%' || $1 || '%'
                                OR contact_email ILIKE '%' || $1 || '%'
                            )
                        ORDER BY created_at DESC
                        LIMIT $2
                        OFFSET $3;
                        """)
                .execute(Tuple.of(normalizeSearch(req.getSearch()), req.getPageSize(), offset))
                .map(this::mapPagedMerchants);
    }

    public Future<Merchant> getMerchantById(Long merchantId) {
        return client
                .preparedQuery("""
                        SELECT
                            merchant_id,
                            user_id,
                            name,
                            description,
                            address,
                            contact_email,
                            contact_phone,
                            status,
                            created_at,
                            updated_at
                        FROM merchants
                        WHERE
                            merchant_id = $1
                            AND deleted_at IS NULL;
                        """)
                .execute(Tuple.of(merchantId))
                .map(rows -> rows.iterator().hasNext() ? Merchant.fromRow(rows.iterator().next()) : null);
    }

        public Future<Merchant> createMerchant(CreateMerchantRequest req) {
        return client
                .preparedQuery("""
                        INSERT INTO
                            merchants (
                                user_id,
                                name,
                                description,
                                address,
                                contact_email,
                                contact_phone,
                                status
                            )
                        VALUES ($1, $2, $3, $4, $5, $6, $7)
                        RETURNING
                            merchant_id,
                            user_id,
                            name,
                            description,
                            address,
                            contact_email,
                            contact_phone,
                            status,
                            created_at,
                            updated_at;
                        """)
                .execute(Tuple.of(
                        req.getUserId() != null ? req.getUserId().longValue() : null,
                        req.getName(),
                        req.getDescription(),
                        req.getAddress(),
                        req.getContactEmail(),
                        req.getContactPhone(),
                        req.getStatus()))
                .map(rows -> Merchant.fromRow(rows.iterator().next()));
    }

    public Future<Merchant> updateMerchant(UpdateMerchantRequest req) {
        return client
                .preparedQuery("""
                        UPDATE merchants
                        SET
                            name = $2,
                            description = $3,
                            address = $4,
                            contact_email = $5,
                            contact_phone = $6,
                            status = $7,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE
                            merchant_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            merchant_id,
                            user_id,
                            name,
                            description,
                            address,
                            contact_email,
                            contact_phone,
                            status,
                            created_at,
                            updated_at;
                        """)
                .execute(Tuple.of(
                        req.getMerchantId() != null ? req.getMerchantId().longValue() : null,
                        req.getName(),
                        req.getDescription(),
                        req.getAddress(),
                        req.getContactEmail(),
                        req.getContactPhone(),
                        req.getStatus()))
                .map(rows -> rows.iterator().hasNext() ? Merchant.fromRow(rows.iterator().next()) : null);
    }

    public Future<Merchant> trashMerchant(Long merchantId) {
        return client
                .preparedQuery("""
                        UPDATE merchants
                        SET
                            deleted_at = current_timestamp
                        WHERE
                            merchant_id = $1
                            AND deleted_at IS NULL
                        RETURNING
                            merchant_id,
                            user_id,
                            name,
                            description,
                            address,
                            contact_email,
                            contact_phone,
                            status,
                            created_at,
                            updated_at,
                            deleted_at;
                        """)
                .execute(Tuple.of(merchantId))
                .map(rows -> rows.iterator().hasNext() ? Merchant.fromRow(rows.iterator().next()) : null);
    }

    public Future<Merchant> restoreMerchant(Long merchantId) {
        return client
                .preparedQuery("""
                        UPDATE merchants
                        SET
                            deleted_at = NULL
                        WHERE
                            merchant_id = $1
                            AND deleted_at IS NOT NULL
                        RETURNING
                            merchant_id,
                            user_id,
                            name,
                            description,
                            address,
                            contact_email,
                            contact_phone,
                            status,
                            created_at,
                            updated_at,
                            deleted_at;
                        """)
                .execute(Tuple.of(merchantId))
                .map(rows -> rows.iterator().hasNext() ? Merchant.fromRow(rows.iterator().next()) : null);
    }

    public Future<Void> deleteMerchantPermanently(Long merchantId) {
        return client
                .preparedQuery("DELETE FROM merchants WHERE merchant_id = $1 AND deleted_at IS NOT NULL")
                .execute(Tuple.of(merchantId))
                .mapEmpty();
    }

    public Future<Integer> restoreAllMerchants() {
        return client
                .preparedQuery("UPDATE merchants SET deleted_at = NULL WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }

    public Future<Integer> deleteAllPermanentMerchants() {
        return client
                .preparedQuery("DELETE FROM merchants WHERE deleted_at IS NOT NULL")
                .execute()
                .map(RowSet::rowCount);
    }


    private String normalizeSearch(String search) {
        if (search == null || search.isBlank())
            return null;
        return search;
    }

    private PagedResult<Merchant> mapPagedMerchants(RowSet<Row> rows) {
        List<Merchant> list = new ArrayList<>();
        int total = 0;
        for (Row row : rows) {
            list.add(Merchant.fromRow(row));
            if (total == 0)
                total = row.getInteger("total_count");
        }
        return new PagedResult<>(list, total);
    }
}