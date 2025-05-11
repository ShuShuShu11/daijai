package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import org.springframework.web.bind.annotation.RequestBody;

public interface CustomerService {


    String login(String code);

//    CustomerLoginVo getCustomerLoginInfo(String token);
    CustomerLoginVo getCustomerLoginInfo(Long userId);

    Boolean updateWxPhoneNumber(@RequestBody UpdateWxPhoneForm updateWxPhoneForm);
}
