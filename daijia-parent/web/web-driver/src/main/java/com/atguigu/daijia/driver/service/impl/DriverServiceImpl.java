package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverServiceImpl implements DriverService {

    @Autowired
    private DriverInfoFeignClient driverInfoFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    LocationFeignClient locationFeignClient;

    @Autowired
    NewOrderFeignClient newOrderFeignClient;


    @Override
    public String login(String code) {

        //1、获取司机id
        Long driverId = driverInfoFeignClient.login(code).getData();

        //2、生成token
        String token = UUID.randomUUID().toString().replace("-", "");

        //3、将id+token存入redisredisTemplate
        redisTemplate.opsForValue().set(
                RedisConstant.USER_LOGIN_KEY_PREFIX + token,
                driverId.toString(),
                RedisConstant.USER_LOGIN_KEY_TIMEOUT,
                TimeUnit.SECONDS);
        return token;
    }

    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        return driverInfoFeignClient.getDriverLoginInfo(driverId).getData();
    }

    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {

        return driverInfoFeignClient.getDriverAuthInfo(driverId).getData();
    }

    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {


        return driverInfoFeignClient.UpdateDriverAuthInfo(updateDriverAuthInfoForm).getData();
    }

    //创建司机人脸模型
    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        return driverInfoFeignClient.creatDriverFaceModel(driverFaceModelForm).getData();
    }

    @Override
    public Boolean isFaceRecognition(Long driverId) {
        return driverInfoFeignClient.isFaceRecognition(driverId).getData();
    }

    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        return driverInfoFeignClient.verifyDriverFace(driverFaceModelForm).getData();
    }

    //开始接单服务
    @Override
    public Boolean startService(Long driverId) {

        //1 判断完成认证
        DriverLoginVo driverLoginVo = driverInfoFeignClient.getDriverLoginInfo(driverId).getData();
        if(driverLoginVo.getAuthStatus()!=2) {
            throw new GuiguException(ResultCodeEnum.AUTH_ERROR);
        }

        //2 判断当日是否人脸识别
        Boolean isFace = driverInfoFeignClient.isFaceRecognition(driverId).getData();
        if(!isFace) {
            throw new GuiguException(ResultCodeEnum.FACE_ERROR);
        }

        //3 更新订单状态 1 开始接单
        driverInfoFeignClient.updateServiceStatus(driverId,1);

        //4 删除redis司机位置信息
        locationFeignClient.removeDriverLocation(driverId);

        //5 清空司机临时队列数据
        newOrderFeignClient.clearNewOrderQueueData(driverId);
        return true;
    }

    @Override
    public Boolean stopService(Long driverId) {
        //更新司机接单状态
        driverInfoFeignClient.updateServiceStatus(driverId, 0);

        //删除司机位置信息
        locationFeignClient.removeDriverLocation(driverId);

        //清空司机新订单队列
        newOrderFeignClient.clearNewOrderQueueData(driverId);
        return true;
    }


}
