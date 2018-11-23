package com.mmall.service.Impl;

import com.google.common.collect.Lists;
import com.mmall.service.IFilleService;
import com.mmall.util.FTPUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * @author leaf
 * @create 2018-11-14 15:39
 * 文件
 */
@Service("iFilleService")
public class FileServiceImpl implements IFilleService {
    private Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    /**
     * 返回上传后的文件名
     */
    public String upload(MultipartFile file, String path) {
        //拿到原始文件名
        String fileName = file.getOriginalFilename();
        //扩展名
        String fileExtensionName = fileName.substring(fileName.lastIndexOf(".") + 1);
//新文件名
        String uploadFileName = UUID.randomUUID().toString() + "." + fileExtensionName;
        logger.info("开始上传文件，上传的文件名：{}，上传的路劲：{}，新文件名：{}", fileName, path, uploadFileName);

        //申明目录的文件夹‘
        File fileDir = new File(path);
        if (!fileDir.exists()) {
            //创建目录
            fileDir.setWritable(true);
            fileDir.mkdirs();
        }

        File targetFile = new File(path,uploadFileName);

        try {
            //上传文件 file.transferTo(targetFile);
            file.transferTo(targetFile);

            //将targetFile上传到FTP服务器上
            FTPUtil.uploadFile(Lists.newArrayList(targetFile));
            //已经上传到FTP服务器
            // 上传完后删除upload下面的文件
            targetFile.delete();

        } catch (IOException e) {
           logger.error("上传文件异常",e);
           //没文件名
           return  null;
        }
        //目标问价你的文件名
        return  targetFile.getName();
    }
}
