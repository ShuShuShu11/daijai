package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.service.CiService;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CosServiceImpl implements CosService {

    @Autowired
    private TencentCloudProperties tencentCloudProperties;


    @Autowired
    private CiService ciService;

    private COSClient getCosClient() {
        // 1 初始化用户身份信息（secretId, secretKey）。
        String secretId = tencentCloudProperties.getSecretId();
        String secretKey = tencentCloudProperties.getSecretKey();
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 2 设置 bucket 的地域, COS 地域
        Region region = new Region(tencentCloudProperties.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        // 这里建议设置使用 https 协议
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 3 生成 cos 客户端。
        COSClient cosClient = new COSClient(cred, clientConfig);
        return cosClient;
    }

    @Override
    public CosUploadVo upload(MultipartFile file, String path) {

        //获取client对象
        COSClient cosClient = getCosClient();

        //文件上传
        //元数据信息
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(file.getSize());
        meta.setContentEncoding("UTF-8");
        meta.setContentType(file.getContentType());

        //向存储桶中保存文件
        String fileType = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".")); //文件后缀名
        String uploadPath = "/driver/" + path + "/" + UUID.randomUUID().toString().replaceAll("-", "") + fileType;
        // 01.jpg
        // /driver/auth/0o98754.jpg
        PutObjectRequest putObjectRequest = null;
        try {
            //bucket名称，上传路径，文件流，元数据
            putObjectRequest = new PutObjectRequest(tencentCloudProperties.getBucketPrivate(),
                    uploadPath,
                    file.getInputStream(),
                    meta);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        putObjectRequest.setStorageClass(StorageClass.Standard);
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest); //上传文件
        cosClient.shutdown();

        // TODO 图片审核   便于测试，直接返回true了
        //审核图片
        Boolean isAuditing = ciService.imageAuditing(uploadPath);
//        Boolean isAuditing = true;
        if(!isAuditing) {
            //删除违规图片
            cosClient.deleteObject(tencentCloudProperties.getBucketPrivate(), uploadPath);
            throw new GuiguException(ResultCodeEnum.IMAGE_AUDITION_FAIL);
        }


        //返回vo对象
        CosUploadVo cosUploadVo = new CosUploadVo();
        //1、上传地址
        cosUploadVo.setUrl(uploadPath);
        //2、图片临时访问url，回显使用，下载
        cosUploadVo.setShowUrl(getImageUrl(uploadPath));
        return cosUploadVo;
    }


    // 获得临时签名URL 用于临时访问
    @Override
    public String getImageUrl(String path) {

        if(!StringUtils.hasText(path)) return "";

        //获取client对象
        COSClient cosClient = getCosClient();

        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(tencentCloudProperties.getBucketPrivate(), path, HttpMethodName.GET);

        //设置临时URL有效期为15分钟
        Date expiration = new DateTime().plusMinutes(15).toDate();
        request.setExpiration(expiration);

        //得到url
        URL url = cosClient.generatePresignedUrl(request);
        cosClient.shutdown();
        return url.toString();
    }
}
