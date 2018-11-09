package com.mmall.dao;

import com.mmall.model.Cart;

public interface CartMapper {
    /**
     * 根据主键删除
     * @param id
     * @return
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * 插入一个购物车
     * @param record
     * @return
     */
    int insert(Cart record);

    /**
     * 有选择的进行插入
     * @param record
     * @return
     */
    int insertSelective(Cart record);

    Cart selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(Cart record);

    int updateByPrimaryKey(Cart record);
}