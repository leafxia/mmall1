package com.mmall.dao;

import com.mmall.model.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(User record);

    int insertSelective(User record);

    User selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(User record);

    int updateByPrimaryKey(User record);

    /**
     * 判断用户名是否存在
     */
    int checkUsername(String username);

    int checkEmail(String email);

    /**
     * 登陆
     */
    User selectLogin(@Param("username") String username,@Param("password") String password);

    String selectQuestionByUsername(String username);
    /**
     * 找回密码问题的答案
     */
    int checkAnswer(@Param("username")String username,@Param("question")String question,@Param("answer")String answer);

    /**
     * 修改密码
     */
    int updatePasswordByUsername(@Param("username")String username,@Param("passwordNew") String passwordNew);
/**
 *
 */
    int checkPassword(@Param("password")String password,@Param("userId")Integer userId);

    /**
     * 邮箱检查
     */
    int checkEmailByUserId(@Param("email")String email,@Param("userId")Integer userId);
}