package com.popcorn.store.domain.goods.repository;

import com.popcorn.store.domain.goods.dto.GoodsStockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GoodsReservationRepository  {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GoodsStockResponse reserveStock(UUID goodsId, int quantity){

        String sql = """
                UPDATE goods_variants
                   SET reservation_stock = reservation_stock + :quantity,
                       updated_at = now()
                 WHERE goods_id = :goodsId
                   AND stock - reservation_stock >= :quantity
                   AND deleted_at IS NULL
                RETURNING goods_id, stock, reservation_stock
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("goodsId", goodsId)
                .addValue("quantity", quantity);

        return jdbcTemplate.query(sql, params, rs -> rs.next() ? mapStock(rs) : null);

    }

    public GoodsStockResponse cancelStock(UUID goodsId, int quantity){

        String sql = """
                UPDATE goods_variants
                   SET reservation_stock = reservation_stock - :quantity,
                       updated_at = now()
                 WHERE goods_id = :goodsId
                   AND reservation_stock >= :quantity
                   AND deleted_at IS NULL
                RETURNING goods_id, stock, reservation_stock
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("goodsId", goodsId)
                .addValue("quantity", quantity);

        return jdbcTemplate.query(sql, params, rs -> rs.next() ? mapStock(rs) : null);

    }

    public GoodsStockResponse failStock(UUID goodsId, int quantity){
        String sql = """
                UPDATE goods_variants
                   SET reservation_stock = reservation_stock - :quantity,
                       updated_at = now()
                 WHERE goods_id = :goodsId
                   AND reservation_stock >= :quantity
                   AND deleted_at IS NULL
                RETURNING goods_id, stock, reservation_stock
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("goodsId", goodsId)
                .addValue("quantity", quantity);

        return jdbcTemplate.query(sql, params, rs -> rs.next() ? mapStock(rs) : null);

    }

    public GoodsStockResponse completeStock(UUID goodsId, int quantity){
        String sql = """
                UPDATE goods_variants
                   SET reservation_stock = reservation_stock + :quantity,
                       stock = stock - :quantity,
                       updated_at = now()
                 WHERE goods_id = :goodsId
                   AND stock - reservation_stock>= :quantity
                   AND deleted_at IS NULL
                RETURNING goods_id, stock, reservation_stock
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("goodsId", goodsId)
                .addValue("quantity", quantity);

        return jdbcTemplate.query(sql, params, rs -> rs.next() ? mapStock(rs) : null);

    }


    private GoodsStockResponse mapStock(ResultSet rs) throws SQLException {
        return new GoodsStockResponse(
                UUID.fromString(rs.getString("goods_id")),
                null,
                rs.getInt("stock"),
                rs.getInt("reservation_stock")
        );

    }
}
