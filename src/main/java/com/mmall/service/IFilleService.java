package com.mmall.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * @author leaf
 * @create 2018-11-14 15:39
 */
public interface IFilleService {
    String upload(MultipartFile file, String path);
}
