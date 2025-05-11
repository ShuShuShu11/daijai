package com.atguigu.daijia.customer.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
/**
 * 创建 微信工具包Bean对象
 */
public class WxConfigOperator {

    @Autowired
    private WxConfigProperties wxConfigProperties;

    @Bean
    public WxMaService wxMaService(){
        //1、获取微信小程序id和密钥
        WxMaDefaultConfigImpl wxMaDefaultConfig = new WxMaDefaultConfigImpl();
        wxMaDefaultConfig.setAppid(wxConfigProperties.getAppId());
        wxMaDefaultConfig.setSecret(wxConfigProperties.getSecret());

        //2、创建WxMaService对象
        WxMaService wxMaService = new WxMaServiceImpl();
        wxMaService.setWxMaConfig(wxMaDefaultConfig);
        return wxMaService;

    }

}
