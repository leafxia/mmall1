package com.mmall.service.Impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mmall.common.ServerResponse;
import com.mmall.dao.CategoryMapper;
import com.mmall.model.Category;
import com.mmall.service.ICategoryService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * @author leaf
 * @create 2018-11-12 20:15
 */
@Service("iCategoryService")
public class CategoryServiceImpl implements ICategoryService {
    private Logger logger = LoggerFactory.getLogger(CategoryServiceImpl.class);

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 添加商品
     *
     * @param categoryName
     * @param parentId
     * @return
     */
    public ServerResponse addCategory(String categoryName, Integer parentId) {
        if (parentId == null || StringUtils.isBlank((categoryName))) {
            return ServerResponse.createByErrorMessage("添加商品参数错误");
        }
        Category category = new Category();
        category.setName(categoryName);
        category.setParentId(parentId);
        category.setStatus(true);//这个分类是可以用的
        int rowCount = categoryMapper.insert(category);
        if (rowCount > 0) {
            return ServerResponse.createBySuccessMessage("添加品类成功");
        }
        return ServerResponse.createByErrorMessage("添加品类失败");

    }

    /**
     * 跟新商品名字
     *
     * @param categoryId
     * @param categoryNam
     * @return
     */
    public ServerResponse updateCategoryName(Integer categoryId, String categoryNam) {
        if (categoryId == null || StringUtils.isBlank(categoryNam)) {
            return ServerResponse.createByErrorMessage("添加商品参数错误");
        }

        Category category = new Category();
        category.setId(categoryId);
        category.setName(categoryNam);
        int rowCount = categoryMapper.updateByPrimaryKeySelective(category);
        if (rowCount > 0) {
            return ServerResponse.createBySuccess("跟新品类名字成功");
        }
        return ServerResponse.createByErrorMessage("跟新品类名字失败");
    }

    /**
     * 查找当前节点的下一级所有的平级节点
     *
     * @return
     */
    public ServerResponse<List<Category>> getChildrenParallelCategory(Integer categoryId) {
        List<Category> categoryList = categoryMapper.selectCategoryChildrenByparentId(categoryId);
        if (CollectionUtils.isEmpty(categoryList)) {
            logger.info("未找到当前分类的子分类");
        }
        return ServerResponse.createBySuccess(categoryList);
    }

    /**
     * 查询当前节点的Id并且递归子节点的id
     */
    public ServerResponse<List<Integer>>  selectCategoryAndChildrenById(Integer categoryId) {
//初始化
        Set<Category> categorySet =Sets.newHashSet();
        findChildCategery(categorySet,categoryId);
        //返回categoryId的集合
        List<Integer> categoryIdList=Lists.newArrayList();
        if(categoryId !=null){
            for (Category categoryIdItem : categorySet){

                categoryIdList.add(categoryIdItem.getId());
            }
        }
        return  ServerResponse.createBySuccess(categoryIdList);
    }

    //递归函数 自己掉自己 算出子节点
    private Set<Category> findChildCategery(Set<Category> categorySet, Integer categoryId) {
        Category category = categoryMapper.selectByPrimaryKey(categoryId);
        if (category != null) {
            categorySet.add(category);
        }
        //查找子节点 递归算法一定要有一个推出的条件
        List<Category> categoryList =categoryMapper.selectCategoryChildrenByparentId(categoryId);
        for(Category categoryItem : categoryList){
            findChildCategery(categorySet,categoryItem.getId());
        }
        return categorySet;

    }

}
