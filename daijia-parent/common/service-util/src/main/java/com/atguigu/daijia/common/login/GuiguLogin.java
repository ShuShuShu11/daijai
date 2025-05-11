package com.atguigu.daijia.common.login;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ClassName: GuiguLogin
 * Description:
 *
 * @Author Shu
 * @Create 2025-04-03 9:14
 */
//登录判断
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GuiguLogin {

}
