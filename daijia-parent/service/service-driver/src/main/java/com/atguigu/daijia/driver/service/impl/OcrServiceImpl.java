package com.atguigu.daijia.driver.service.impl;

import com.alibaba.nacos.common.codec.Base64;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.OcrService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import com.atguigu.daijia.model.vo.driver.DriverLicenseOcrVo;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.*;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OcrServiceImpl implements OcrService {

    @Autowired
    TencentCloudProperties tencentCloudProperties;

    @Autowired
    CosService cosService;

    // 身份识别
    @Override
    public IdCardOcrVo idCardOcr(MultipartFile file) {
        try {
            //图片转换base64格式字符串
            byte[] base64 = Base64.encodeBase64(file.getBytes());
            String fileBase64 = new String(base64);

            // 实例化一个认证对象
            Credential cred = new Credential(tencentCloudProperties.getSecretId(),
                    tencentCloudProperties.getSecretKey());

            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("ocr.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);

            // 实例化要请求产品的client对象,clientProfile是可选的
            OcrClient client = new OcrClient(cred, tencentCloudProperties.getRegion(), clientProfile);

            // 实例化一个请求对象,每个接口都会对应一个request对象
            IDCardOCRRequest req = new IDCardOCRRequest();
            //设置文件
            req.setImageBase64(fileBase64);

            // 返回的resp是一个IDCardOCRResponse的实例，与请求对象对应
            IDCardOCRResponse resp = client.IDCardOCR(req);

            /////////////////转换为IdCardOcrVo对象////////////////////////////////
            IdCardOcrVo idCardOcrVo = new IdCardOcrVo();
            if (StringUtils.hasText(resp.getName())) {
                //身份证正面
                idCardOcrVo.setName(resp.getName());
                idCardOcrVo.setGender("男".equals(resp.getSex()) ? "1" : "2");
                idCardOcrVo.setBirthday(DateTimeFormat.forPattern("yyyy/MM/dd").parseDateTime(resp.getBirth()).toDate());
                idCardOcrVo.setIdcardNo(resp.getIdNum());
                idCardOcrVo.setIdcardAddress(resp.getAddress());

                //上传身份证正面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardFrontUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardFrontShowUrl(cosUploadVo.getShowUrl());
            } else {
                //身份证反面
                //证件有效期："2010.07.21-2020.07.21"
                String idcardExpireString = resp.getValidDate().split("-")[1];
                idCardOcrVo.setIdcardExpire(DateTimeFormat.forPattern("yyyy.MM.dd").parseDateTime(idcardExpireString).toDate());
                //上传身份证反面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardBackUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardBackShowUrl(cosUploadVo.getShowUrl());
            }
            return idCardOcrVo;

        } catch (Exception e) {

//            e.printStackTrace();  //服务未开通，请前往控制台开通相应服务：相关的识别功能，需要开启
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    @Override
    public DriverLicenseOcrVo driverLicenseOcr(MultipartFile file) {
        //将图片转换为base64字符串
        byte[] encoder = new byte[0];
        try {
            encoder = Base64.encodeBase64(file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String driverLicenseBase64 = new String(encoder);

        // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
        // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
        // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
        Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
        // 实例化一个http选项，可选的，没有特殊需求可以跳过
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("ocr.tencentcloudapi.com");
        // 实例化一个client选项，可选的，没有特殊需求可以跳过
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        // 实例化要请求产品的client对象,clientProfile是可选的
        OcrClient client = new OcrClient(cred, tencentCloudProperties.getRegion(), clientProfile);
        // 实例化一个请求对象,每个接口都会对应一个request对象
        DriverLicenseOCRRequest req = new DriverLicenseOCRRequest();
        req.setImageBase64(driverLicenseBase64);

        // 返回的resp是一个VehicleLicenseOCRResponse的实例，与请求对象对应
        DriverLicenseOCRResponse resp = null;
        try {
            resp = client.DriverLicenseOCR(req);
        } catch (TencentCloudSDKException e) {
            throw new RuntimeException(e);
        }
        // 输出json格式的字符串回包
        log.info(VehicleLicenseOCRResponse.toJsonString(resp));

        DriverLicenseOcrVo driverLicenseOcrVo = new DriverLicenseOcrVo();
        if (StringUtils.hasText(resp.getName())) {
            //驾驶证正面
            //驾驶证名称要与身份证名称一致
            driverLicenseOcrVo.setName(resp.getName());
            driverLicenseOcrVo.setDriverLicenseClazz(resp.getClass_());
            driverLicenseOcrVo.setDriverLicenseNo(resp.getCardCode());
            driverLicenseOcrVo.setDriverLicenseIssueDate(DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(resp.getDateOfFirstIssue()).toDate());
            driverLicenseOcrVo.setDriverLicenseExpire(DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(resp.getEndDate()).toDate());

            //上传驾驶证反面图片到腾讯云cos
            CosUploadVo cosUploadVo = cosService.upload(file, "driverLicense");
            driverLicenseOcrVo.setDriverLicenseFrontUrl(cosUploadVo.getUrl());
            driverLicenseOcrVo.setDriverLicenseFrontShowUrl(cosUploadVo.getShowUrl());
        } else {
            //驾驶证反面
            //上传驾驶证反面图片到腾讯云cos
            CosUploadVo cosUploadVo =  cosService.upload(file, "driverLicense");
            driverLicenseOcrVo.setDriverLicenseBackUrl(cosUploadVo.getUrl());
            driverLicenseOcrVo.setDriverLicenseBackShowUrl(cosUploadVo.getShowUrl());
        }

        return driverLicenseOcrVo;
    }
}

