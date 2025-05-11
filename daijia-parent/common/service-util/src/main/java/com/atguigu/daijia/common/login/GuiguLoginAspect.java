package com.atguigu.daijia.common.login;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * ClassName: GuiguLoginAspect
 * Description:
 *
 * @Author Shu
 * @Create 2025-04-03 9:17
 */
@Component
@Aspect
public class GuiguLoginAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    @Around("execution(* com.atguigu.daijia.*.controller.*.*(..)) && @annotation(guiguLogin)")
    public Object login(ProceedingJoinPoint proceedingJoinPoint, GuiguLogin guiguLogin) throws Throwable{
        //1、获取request对象
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes sra = (ServletRequestAttributes)attributes;
        HttpServletRequest request = sra.getRequest();

        //2、从请求头获取token
        String token = request.getHeader("token");

        //3、判空
        if(!StringUtils.hasText(token)){
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }

        //4、查询redis，得到id
        String customerId = (String)redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX + token);

        //5、把用户id放到ThreadLocal里面，以便后续可以直接获得，而不是再次调用redis
        if (StringUtils.hasText(customerId)){
            AuthContextHolder.setUserId(Long.parseLong(customerId));
        }

        //6、执行业务方法
        return proceedingJoinPoint.proceed();
    }

}
