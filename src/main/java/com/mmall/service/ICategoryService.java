package com.mmall.service;

import com.mmall.common.ServerResponse;
import com.mmall.model.Category;

import java.util.List;

/**
 * @author leaf
 * @create 2018-11-12 20:15
 */
public interface ICategoryService {
    ServerResponse addCategory(String categoryName, Integer parentId);
    ServerResponse updateCategoryName(Integer categoryId, String categoryNam);
    ServerResponse<List<Category>> getChildrenParallelCategory(Integer categoryId);
    ServerResponse<List<Integer>> selectCategoryAndChildrenById(Integer categoryId);
}
