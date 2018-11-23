package com.mmall.service;

import com.mmall.common.ServerResponse;
import com.mmall.model.User;
import org.springframework.stereotype.Service;

/**
 * @author leaf
 * @create 2018-11-09 20:25
 */
@Service
public interface IUserService {
    ServerResponse<User> login(String username, String password);
    ServerResponse<String > register(User user);
    ServerResponse<String> checkValid(String str, String type);
    ServerResponse<String> selectQuestion(String username);
    ServerResponse<String> forgetCheckAnswer(String username,String question,String answer);
    ServerResponse<String> forgetResetPassword(String username, String passwordnew, String forgetToken);
    ServerResponse<String> resetPassword(String passwordOld,String passwordNew,User user);
    ServerResponse<User> updateInformation(User user);
    ServerResponse<User> getInformation(Integer userId);
    ServerResponse checkAdminRole(User user);

}
