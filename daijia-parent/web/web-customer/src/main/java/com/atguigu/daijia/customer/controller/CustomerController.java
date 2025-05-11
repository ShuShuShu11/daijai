package com.atguigu.daijia.customer.controller;

import com.atguigu.daijia.common.login.GuiguLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "客户API接口管理")
@RestController
@RequestMapping("/customer")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerController {

    @Autowired
    private CustomerService customerInfoService;

    @Operation(summary = "小程序授权登录")
    @GetMapping("/login/{code}")
    public Result<String> wxLogin(@PathVariable String code) {
        return Result.ok(customerInfoService.login(code));
    }

//    @Operation(summary = "获取客户登录信息")
//    @GetMapping("/getCustomerLoginInfo")
//    public Result<CustomerLoginVo>
//    getCustomerLoginInfo(@RequestHeader(value = "token") String token) {
//        return Result.ok(customerInfoService.getCustomerLoginInfo(token));
//    }

    @Operation(summary = "获取客户登录信息")
    @GuiguLogin
    @GetMapping("/getCustomerLoginInfo")
    public Result<CustomerLoginVo> getCustomerLoginInfo() {

        //1、从本地线程获取id
        Long userId = AuthContextHolder.getUserId();

        return Result.ok(customerInfoService.getCustomerLoginInfo(userId));
    }

    @Operation(summary = "更新用户微信手机号")
    @GuiguLogin
    @PostMapping("/updateWxPhone")
    public Result updateWxPhone(@RequestBody UpdateWxPhoneForm updateWxPhoneForm) {
        updateWxPhoneForm.setCustomerId(AuthContextHolder.getUserId());
        //customerInfoService.updateWxPhoneNumber(updateWxPhoneForm);
        // 个人版的获取不到手机号，直接跳过
        return Result.ok(true);
    }



}

