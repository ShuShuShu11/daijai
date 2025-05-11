package com.atguigu.daijia.map.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.map.service.MapService;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class MapServiceImpl implements MapService {

    @Autowired
    private RestTemplate restTemplate; // 用来远程调用腾讯服务

    @Value("${tencent.map.key}")
    private String key;    // 腾讯地图服务

    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {

        //1、定义调用腾讯地址
        String url = "https://apis.map.qq.com/ws/direction/v1/driving/?from={from}&to={to}&key={key}";

        //2、封装传递参数
        HashMap<String, String> map = new HashMap<>();
        map.put("from",(calculateDrivingLineForm.getStartPointLatitude()+","+calculateDrivingLineForm.getStartPointLongitude()));
        map.put("to",(calculateDrivingLineForm.getEndPointLatitude()+","+calculateDrivingLineForm.getEndPointLongitude()));
        map.put("key",key);

        //3、远程调用
        JSONObject result = restTemplate.getForObject(url, JSONObject.class, map);
        if(result.getIntValue("status" ) != 0){
            throw new GuiguException(ResultCodeEnum.MAP_FAIL);
        }

        //4、返回第一条最佳线路
        JSONObject route = result.getJSONObject("result")
                .getJSONArray("routes")
                .getJSONObject(0); // 获取array中的第一个对象，即最佳的路线

        DrivingLineVo drivingLineVo = new DrivingLineVo();
        drivingLineVo.setDistance(route
                .getBigDecimal("distance")
                .divide(new BigDecimal(1000))  //默认是米，除以1000，设置单位：千米
                .setScale(2, RoundingMode.HALF_UP)); //两位小数，向上取
        drivingLineVo.setDuration(route.getBigDecimal("duration"));
        drivingLineVo.setPolyline(route.getJSONArray("polyline"));

        return drivingLineVo;

    }
}
