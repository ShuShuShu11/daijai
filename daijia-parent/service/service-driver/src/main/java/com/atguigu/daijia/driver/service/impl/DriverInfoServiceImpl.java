package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.config.WxConfigOperator;
import com.atguigu.daijia.driver.mapper.*;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.*;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.iai.v20180301.IaiClient;
import com.tencentcloudapi.iai.v20180301.models.*;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverInfoServiceImpl extends ServiceImpl<DriverInfoMapper, DriverInfo> implements DriverInfoService {

    @Autowired
    private DriverInfoMapper driverInfoMapper;

    @Autowired
    private DriverAccountMapper driverAccountMapper; //账户初始化

    @Autowired
    private WxMaService wxMaService;  // wx工具对象

    @Autowired
    private DriverSetMapper driverSetMapper; //初始化司机设置

    @Autowired
    private DriverLoginLogMapper driverLoginLogMapper; //登录日志

    @Autowired
    private TencentCloudProperties tencentCloudProperties;

    @Autowired
    private CosService cosService;

    @Autowired
    private DriverFaceRecognitionMapper driverFaceRecognitionMapper;

    /**
     * 司机登录
     * @param code
     * @return 司机id
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long login(String code) {

        String openId = null;
        try {
            //获取openId
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
            openId = sessionInfo.getOpenid();
            log.info("【小程序授权】openId={}", openId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        DriverInfo driverInfo = this.getOne(new LambdaQueryWrapper<DriverInfo>().eq(DriverInfo::getWxOpenId, openId));
        if (null == driverInfo) {
            driverInfo = new DriverInfo();
            driverInfo.setNickname(String.valueOf(System.currentTimeMillis()));
            driverInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
            driverInfo.setWxOpenId(openId);
            this.save(driverInfo);  // 插入登录信息

            //初始化默认设置
            DriverSet driverSet = new DriverSet();
            driverSet.setDriverId(driverInfo.getId());
            driverSet.setOrderDistance(new BigDecimal(0));//0：无限制
            driverSet.setAcceptDistance(new BigDecimal(SystemConstant.ACCEPT_DISTANCE));//默认接单范围：5公里
            driverSet.setIsAutoAccept(0);//0：否 1：是
            driverSetMapper.insert(driverSet);

            //初始化司机账户
            DriverAccount driverAccount = new DriverAccount();
            driverAccount.setDriverId(driverInfo.getId());
            driverAccountMapper.insert(driverAccount);
        }

        //登录日志
        DriverLoginLog driverLoginLog = new DriverLoginLog();
        driverLoginLog.setDriverId(driverInfo.getId());
        driverLoginLog.setMsg("小程序登录");
        driverLoginLogMapper.insert(driverLoginLog);
        return driverInfo.getId();
    }

    /**
     * 获取司机信息
     * @param driverId
     * @return 司机信息
     */
    @Override
    public DriverLoginVo getDriverLoginInfo(long driverId) {

        //1、获取driverInfo，即完整信息
        DriverInfo driverInfo = this.getById(driverId);

        //2、赋值
        DriverLoginVo driverLoginVo = new DriverLoginVo();
        BeanUtils.copyProperties(driverInfo,driverLoginVo);

        //3、是否创建人脸库人员，接单时做人脸识别判断
        Boolean isArchiveFace = StringUtils.hasText(driverInfo.getFaceModelId());
        driverLoginVo.setIsArchiveFace(isArchiveFace);

        return driverLoginVo;
    }

    /**
     * 获取司机认证信息
     * @param driverId
     * @return 认证信息
     */
    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);
        DriverAuthInfoVo driverAuthInfoVo = new DriverAuthInfoVo();
        BeanUtils.copyProperties(driverInfo,driverAuthInfoVo);

        // 设置信息   调用方法现在生成的！  【目前的问题：driverInfo中没有认证信息！！】
        driverAuthInfoVo.setIdcardBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardBackUrl()));
        driverAuthInfoVo.setIdcardFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardFrontUrl()));
        driverAuthInfoVo.setIdcardHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardHandUrl()));
        driverAuthInfoVo.setDriverLicenseFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseFrontUrl()));
        driverAuthInfoVo.setDriverLicenseBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseBackUrl()));
        driverAuthInfoVo.setDriverLicenseHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseHandUrl()));

        return driverAuthInfoVo;
    }

    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {

        Long driverId = updateDriverAuthInfoForm.getDriverId();

        //修改操作
        DriverInfo driverInfo = new DriverInfo();
        driverInfo.setId(driverId);
        BeanUtils.copyProperties(updateDriverAuthInfoForm,driverInfo);

        // 把司机信息更新到数据库中
        boolean update = this.updateById(driverInfo);

        return update;
    }

    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {

        //根据司机id获取司机信息
        DriverInfo driverInfo =
                driverInfoMapper.selectById(driverFaceModelForm.getDriverId());

        try{
            Credential cred = new Credential(tencentCloudProperties.getSecretId(),
                    tencentCloudProperties.getSecretKey());

            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);

            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(),
                    clientProfile);

            // 实例化一个请求对象,每个接口都会对应一个request对象
            CreatePersonRequest req = new CreatePersonRequest();
            //设置相关值
            req.setGroupId(tencentCloudProperties.getPersionGroupId());
            //基本信息
            req.setPersonId(String.valueOf(driverInfo.getId()));
            req.setGender(Long.parseLong(driverInfo.getGender()));
            req.setQualityControl(4L);
            req.setUniquePersonControl(4L);
            req.setPersonName(driverInfo.getName());
            req.setImage(driverFaceModelForm.getImageBase64());

            // 返回的resp是一个CreatePersonResponse的实例，与请求对象对应
            CreatePersonResponse resp = client.CreatePerson(req);

            // 输出json格式的字符串回包
            System.out.println();
            System.out.println("#############");
            System.out.println(AbstractModel.toJsonString(resp));
            System.out.println();

            String faceId = resp.getFaceId();
            System.out.println("faceId："+faceId);
            System.out.println("111111111111111111111111111");
            if(StringUtils.hasText(faceId)) {
                // 保存人脸模型！！！！  有值，说明是第一次创建
                driverInfo.setFaceModelId(faceId);
                driverInfoMapper.updateById(driverInfo);
                System.out.println("222222222222222222222222222");
            }
        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public DriverSet getDriverSet(Long driverId) {
        LambdaQueryWrapper<DriverSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DriverSet::getDriverId, driverId);
        return driverSetMapper.selectOne(queryWrapper);
    }

    @Override
    public Boolean isFaceRecognition(Long driverId) {

        LambdaQueryWrapper<DriverFaceRecognition> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(DriverFaceRecognition::getDriverId, driverId);
        queryWrapper.eq(DriverFaceRecognition::getFaceDate, new DateTime().toString("yyyy-MM-dd"));
        long count = driverFaceRecognitionMapper.selectCount(queryWrapper);
        return count != 0;
//        return true;
    }

    /**
     * 人脸验证
     * 文档地址：
     * https://cloud.tencent.com/document/api/867/44983
     * https://console.cloud.tencent.com/api/explorer?Product=iai&Version=2020-03-03&Action=VerifyFace
     *
     * @param driverFaceModelForm
     * @return
     */
    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            VerifyFaceRequest req = new VerifyFaceRequest();
            req.setImage(driverFaceModelForm.getImageBase64());
            req.setPersonId(String.valueOf(driverFaceModelForm.getDriverId()));
            // 返回的resp是一个VerifyFaceResponse的实例，与请求对象对应
            VerifyFaceResponse resp = client.VerifyFace(req);
            // 输出json格式的字符串回包
            System.out.println(VerifyFaceResponse.toJsonString(resp));
            if (resp.getIsMatch()) {
                //活体检查
                if(this.detectLiveFace(driverFaceModelForm.getImageBase64())) {
                    DriverFaceRecognition driverFaceRecognition = new DriverFaceRecognition();
                    driverFaceRecognition.setDriverId(driverFaceModelForm.getDriverId());
                    driverFaceRecognition.setFaceDate(new Date());
                    driverFaceRecognitionMapper.insert(driverFaceRecognition);
                    return true;
                };
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        throw new GuiguException(ResultCodeEnum.DATA_ERROR);
    }

    //更新接单状态
    // update driver_set set status=? where driver_id=?
    @Override
    public Boolean updateServiceStatus(Long driverId, Integer status) {
        LambdaQueryWrapper<DriverSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DriverSet::getDriverId,driverId);
        DriverSet driverSet = new DriverSet();
        driverSet.setServiceStatus(status);
        // 根据wrapper构建的查询条件，将符合条件的DriverSet记录的serviceStatus字段更新为 driverSet对象中设置的值。
        driverSetMapper.update(driverSet,wrapper);
        return true;
    }

    @Override
    public DriverInfoVo getDriverInfoOrder(Long driverId) {
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);
        DriverInfoVo driverInfoVo = new DriverInfoVo();
        BeanUtils.copyProperties(driverInfo, driverInfoVo);
        //驾龄
        Integer driverLicenseAge = new DateTime().getYear() - new DateTime(driverInfo.getDriverLicenseIssueDate()).getYear() + 1;
        driverInfoVo.setDriverLicenseAge(driverLicenseAge);
        return driverInfoVo;
    }

    @Override
    public String getDriverOpenId(Long driverId) {
        DriverInfo driverInfo = this.getOne(new LambdaQueryWrapper<DriverInfo>().eq(DriverInfo::getId, driverId).select(DriverInfo::getWxOpenId));
        return driverInfo.getWxOpenId();
    }

    /**
     * 人脸静态活体检测
     * 文档地址：
     * https://cloud.tencent.com/document/api/867/48501
     * https://console.cloud.tencent.com/api/explorer?Product=iai&Version=2020-03-03&Action=DetectLiveFace
     * @param imageBase64
     * @return
     */
    private Boolean detectLiveFace(String imageBase64) {
        try{
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DetectLiveFaceRequest req = new DetectLiveFaceRequest();
            req.setImage(imageBase64);
            // 返回的resp是一个DetectLiveFaceResponse的实例，与请求对象对应
            DetectLiveFaceResponse resp = client.DetectLiveFace(req);
            // 输出json格式的字符串回包
            System.out.println(DetectLiveFaceResponse.toJsonString(resp));
            if(resp.getIsLiveness()) {
                return true;
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        return false;
    }
}