package com.atguigu.daijia.driver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ClassName: WxConfigProperties
 * Description:
 *
 * @Author Shu
 * @Create 2025-04-02 22:13
 */


@Component
@Data
@ConfigurationProperties(prefix = "wx.miniapp")
/**
 * 属性类，用于获取小程序的appId和secret两个配置！
 */
public class WxConfigProperties {
    private String appId;
    private String secret;
}
