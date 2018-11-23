package com.mmall.dao;

import com.mmall.model.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(Product record);

    int insertSelective(Product record);

    Product selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(Product record);

    int updateByPrimaryKey(Product record);

    /**
     * 分页
     */
    List<Product> selectList();

    /**
     * 后台商品搜索
     */
    List<Product> selectByNameAndProductId(@Param("pardoctName") String pardoctName,@Param("productId") Integer productId);

    List<Product> selectByNameAndCategoryIds(@Param("pardoctName") String pardoctName,@Param("categoryIdList")List<Integer> categoryIdList );
}