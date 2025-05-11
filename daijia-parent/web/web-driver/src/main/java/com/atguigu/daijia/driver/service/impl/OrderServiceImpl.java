package com.atguigu.daijia.driver.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.config.ThreadPoolConfig;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderFeeForm;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequestForm;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import com.atguigu.daijia.rules.client.ProfitsharingRuleFeignClient;
import com.atguigu.daijia.rules.client.RewardRuleFeignClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderServiceImpl implements OrderService {

    @Autowired
    MapFeignClient mapFeignClient;

    @Autowired
    FeeRuleFeignClient feeRuleFeignClient;

    @Autowired
    OrderInfoFeignClient orderInfoFeignClient;

    @Autowired
    private NewOrderFeignClient newOrderFeignClient;

    @Autowired
    private LocationFeignClient locationFeignClient;

    @Autowired
    private RewardRuleFeignClient rewardRuleFeignClient;

    @Autowired
    private ProfitsharingRuleFeignClient profitsharingRuleFeignClient;

    @Autowired
    private ThreadPoolConfig threadPoolConfig;


    @Override
    public Integer getOrderStatus(Long orderId) {
        return orderInfoFeignClient.getOrderStatus(orderId).getData();
    }


    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        return newOrderFeignClient.findNewOrderQueueData(driverId).getData();
    }


    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        return orderInfoFeignClient.robNewOrder(driverId, orderId).getData();
    }

    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        Result<CurrentOrderInfoVo> result = orderInfoFeignClient.searchDriverCurrentOrder(driverId);
        return result.getData();
    }

    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long driverId) {
        //订单信息
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        if(orderInfo.getDriverId().longValue() != driverId.longValue()) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        //账单信息
        OrderBillVo orderBillVo = null;
        //分账信息
        OrderProfitsharingVo orderProfitsharing = null;
        if (orderInfo.getStatus().intValue() >= OrderStatus.END_SERVICE.getStatus().intValue()) {
            orderBillVo = orderInfoFeignClient.getOrderBillInfo(orderId).getData();

            //获取分账信息
            orderProfitsharing = orderInfoFeignClient.getOrderProfitsharing(orderId).getData();
        }

        //封装订单信息
        OrderInfoVo orderInfoVo = new OrderInfoVo();
        orderInfoVo.setOrderId(orderId);
        BeanUtils.copyProperties(orderInfo, orderInfoVo);
        orderInfoVo.setOrderBillVo(orderBillVo);
        orderInfoVo.setOrderProfitsharingVo(orderProfitsharing);
        return orderInfoVo;
    }

    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        return mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();
    }

    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {

        //防止刷单，计算司机的经纬度与代驾的起始经纬度是否在1公里范围内
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        OrderLocationVo orderLocationVo = locationFeignClient.getCacheOrderLocation(orderId).getData();

        //司机的位置与代驾起始点位置的距离
        double distance = LocationUtil.getDistance(orderInfo.getStartPointLatitude().doubleValue(), orderInfo.getStartPointLongitude().doubleValue(), orderLocationVo.getLatitude().doubleValue(), orderLocationVo.getLongitude().doubleValue());
        if(distance > SystemConstant.DRIVER_START_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_START_LOCATION_DISTION_ERROR);
        }
        return orderInfoFeignClient.driverArriveStartLocation(orderId, driverId).getData();
    }

    @Override
    public Boolean startDrive(StartDriveForm startDriveForm) {
        return orderInfoFeignClient.startDrive(startDriveForm).getData();
    }

    @Override
    public Boolean endDrive(OrderFeeForm orderFeeForm) {
        //1.获取订单信息
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderFeeForm.getOrderId()).getData();
        if(orderInfo.getDriverId().longValue() != orderFeeForm.getDriverId().longValue()) {
            throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }

        //2.防止刷单，计算司机的经纬度与代驾的终点经纬度是否在2公里范围内
        OrderServiceLastLocationVo orderServiceLastLocationVo = locationFeignClient.getOrderServiceLastLocation(orderFeeForm.getOrderId()).getData();
        //司机的位置与代驾终点位置的距离
        double distance = LocationUtil.getDistance(orderInfo.getEndPointLatitude().doubleValue(), orderInfo.getEndPointLongitude().doubleValue(), orderServiceLastLocationVo.getLatitude().doubleValue(), orderServiceLastLocationVo.getLongitude().doubleValue());
        if(distance > SystemConstant.DRIVER_START_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_END_LOCATION_DISTION_ERROR);
        }

        //2.计算订单实际里程
        BigDecimal realDistance = locationFeignClient.calculateOrderRealDistance(orderFeeForm.getOrderId()).getData();
        log.info("结束代驾，订单实际里程：{}", realDistance);

        //3.计算代驾实际费用
        FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
        feeRuleRequestForm.setDistance(realDistance);
        feeRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
        //等候时间
        Integer waitMinute = Math.abs((int) ((orderInfo.getArriveTime().getTime() - orderInfo.getAcceptTime().getTime()) / (1000 * 60)));
        feeRuleRequestForm.setWaitMinute(waitMinute);
        log.info("结束代驾，费用参数：{}", JSON.toJSONString(feeRuleRequestForm));
        FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm).getData();
        log.info("费用明细：{}", JSON.toJSONString(feeRuleResponseVo));

        //订单总金额 需加上 路桥费、停车费、其他费用、乘客好处费
        BigDecimal totalAmount = feeRuleResponseVo.getTotalAmount().add(orderFeeForm.getTollFee()).add(orderFeeForm.getParkingFee()).add(orderFeeForm.getOtherFee()).add(orderInfo.getFavourFee());
        feeRuleResponseVo.setTotalAmount(totalAmount);

        //4.计算系统奖励
        //4.1.获取订单数
        String startTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 00:00:00";
        String endTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 24:00:00";
        Long orderNum = orderInfoFeignClient.getOrderNumByTime(startTime, endTime).getData();
        //4.2.封装参数
        RewardRuleRequestForm rewardRuleRequestForm = new RewardRuleRequestForm();
        rewardRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
        rewardRuleRequestForm.setOrderNum(orderNum);
        //4.3.执行
        RewardRuleResponseVo rewardRuleResponseVo = rewardRuleFeignClient.calculateOrderRewardFee(rewardRuleRequestForm).getData();
        log.info("结束代驾，系统奖励：{}", JSON.toJSONString(rewardRuleResponseVo));

        //5.计算分账信息
        ProfitsharingRuleRequestForm profitsharingRuleRequestForm = new ProfitsharingRuleRequestForm();
        profitsharingRuleRequestForm.setOrderAmount(feeRuleResponseVo.getTotalAmount());
        profitsharingRuleRequestForm.setOrderNum(orderNum);
        ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleFeignClient.calculateOrderProfitsharingFee(profitsharingRuleRequestForm).getData();
        log.info("结束代驾，分账信息：{}", JSON.toJSONString(profitsharingRuleResponseVo));

        //6.封装更新订单账单相关实体对象
        UpdateOrderBillForm updateOrderBillForm = new UpdateOrderBillForm();
        updateOrderBillForm.setOrderId(orderFeeForm.getOrderId());
        updateOrderBillForm.setDriverId(orderFeeForm.getDriverId());
        //路桥费、停车费、其他费用
        updateOrderBillForm.setTollFee(orderFeeForm.getTollFee());
        updateOrderBillForm.setParkingFee(orderFeeForm.getParkingFee());
        updateOrderBillForm.setOtherFee(orderFeeForm.getOtherFee());
        //乘客好处费
        updateOrderBillForm.setFavourFee(orderInfo.getFavourFee());

        //实际里程
        updateOrderBillForm.setRealDistance(realDistance);
        //订单奖励信息
        BeanUtils.copyProperties(rewardRuleResponseVo, updateOrderBillForm);
        //代驾费用信息
        BeanUtils.copyProperties(feeRuleResponseVo, updateOrderBillForm);

        //分账相关信息
        BeanUtils.copyProperties(profitsharingRuleResponseVo, updateOrderBillForm);
        updateOrderBillForm.setProfitsharingRuleId(profitsharingRuleResponseVo.getProfitsharingRuleId());
        log.info("结束代驾，更新账单信息：{}", JSON.toJSONString(updateOrderBillForm));

        //7.结束代驾更新账单
        orderInfoFeignClient.endDrive(updateOrderBillForm);
        return true;
    }

    @SneakyThrows
    public Boolean endDriveThread(OrderFeeForm orderFeeForm) {
        //1.获取订单信息
        CompletableFuture<OrderInfo> orderInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderFeeForm.getOrderId()).getData();
            if (orderInfo.getDriverId().longValue() != orderFeeForm.getDriverId().longValue()) {
                throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
            }
            return orderInfo;
        });


        //2.防止刷单，计算司机的经纬度与代驾的终点经纬度是否在2公里范围内
        CompletableFuture<OrderServiceLastLocationVo> orderServiceLastLocationVoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            OrderServiceLastLocationVo orderServiceLastLocationVo = locationFeignClient.getOrderServiceLastLocation(orderFeeForm.getOrderId()).getData();
            return orderServiceLastLocationVo;
        });


        // 合并前面两步
        CompletableFuture.allOf(orderInfoCompletableFuture,
                orderServiceLastLocationVoCompletableFuture)
                .join();  // 都完成，才进行后续处理


        //司机的位置与代驾终点位置的距离  需要用到前面两步
        // 获取结果
        OrderInfo orderInfo = orderInfoCompletableFuture.get();
        OrderServiceLastLocationVo orderServiceLastLocationVo = orderServiceLastLocationVoCompletableFuture.get();
        double distance = LocationUtil.getDistance(orderInfo.getEndPointLatitude().doubleValue(), orderInfo.getEndPointLongitude().doubleValue(), orderServiceLastLocationVo.getLatitude().doubleValue(), orderServiceLastLocationVo.getLongitude().doubleValue());
        if (distance > SystemConstant.DRIVER_START_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_END_LOCATION_DISTION_ERROR);
        }   // 判断是否刷单

        ///////////////////////////////////////////////////////

        //2.计算订单实际里程
        CompletableFuture<BigDecimal> realDistanceCompletableFuture = CompletableFuture.supplyAsync(() -> {
            BigDecimal realDistance = locationFeignClient.calculateOrderRealDistance(orderFeeForm.getOrderId()).getData();
            log.info("结束代驾，订单实际里程：{}", realDistance);
            return realDistance;
        });

        //3.计算代驾实际费用
        CompletableFuture<FeeRuleResponseVo> feeRuleResponseVoCompletableFuture = realDistanceCompletableFuture.thenApplyAsync((realDistance) -> {
            FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
            feeRuleRequestForm.setDistance(realDistance);
            feeRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
            //等候时间
            Integer waitMinute = Math.abs((int) ((orderInfo.getArriveTime().getTime() - orderInfo.getAcceptTime().getTime()) / (1000 * 60)));
            feeRuleRequestForm.setWaitMinute(waitMinute);

            log.info("结束代驾，费用参数：{}", JSON.toJSONString(feeRuleRequestForm));
            FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm).getData();
            log.info("费用明细：{}", JSON.toJSONString(feeRuleResponseVo));

            //订单总金额 需加上 路桥费、停车费、其他费用、乘客好处费
            BigDecimal totalAmount = feeRuleResponseVo.getTotalAmount().add(orderFeeForm.getTollFee()).add(orderFeeForm.getParkingFee()).add(orderFeeForm.getOtherFee()).add(orderInfo.getFavourFee());
            feeRuleResponseVo.setTotalAmount(totalAmount);
            return feeRuleResponseVo;
        });

        ///////////////////////////////////////////////////////////////

        //4.计算系统奖励
        CompletableFuture<Long> orderNumCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //4.1.获取订单数
            String startTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 00:00:00";
            String endTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 24:00:00";
            Long orderNum = orderInfoFeignClient.getOrderNumByTime(startTime, endTime).getData();
            return orderNum;
        });

        //4.2计算奖励
        CompletableFuture<RewardRuleResponseVo> rewardRuleCompletableFuture = orderNumCompletableFuture.thenApplyAsync((orderNum) -> {
            //封装参数
            RewardRuleRequestForm rewardRuleRequestForm = new RewardRuleRequestForm();
            rewardRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
            rewardRuleRequestForm.setOrderNum(orderNum);
            //执行
            RewardRuleResponseVo rewardRuleResponseVo = rewardRuleFeignClient.calculateOrderRewardFee(rewardRuleRequestForm).getData();
            log.info("结束代驾，系统奖励：{}", JSON.toJSONString(rewardRuleResponseVo));
            return rewardRuleResponseVo;
        });

        ////////////////////////////////////////
        //5.计算分账信息
        CompletableFuture<ProfitsharingRuleResponseVo> profitsharingRuleResponseVoCompletableFuture = feeRuleResponseVoCompletableFuture.thenCombineAsync(orderNumCompletableFuture,
                (feeRuleResponseVo, orderNum) -> {

                    ProfitsharingRuleRequestForm profitsharingRuleRequestForm = new ProfitsharingRuleRequestForm();
                    profitsharingRuleRequestForm.setOrderAmount(feeRuleResponseVo.getTotalAmount());
                    profitsharingRuleRequestForm.setOrderNum(orderNum);
                    ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleFeignClient.calculateOrderProfitsharingFee(profitsharingRuleRequestForm).getData();
                    log.info("结束代驾，分账信息：{}", JSON.toJSONString(profitsharingRuleResponseVo));
                    return profitsharingRuleResponseVo;
                });

        // 合并
        CompletableFuture.allOf(
                orderInfoCompletableFuture,
                realDistanceCompletableFuture,
                feeRuleResponseVoCompletableFuture,
                orderNumCompletableFuture,
                rewardRuleCompletableFuture,
                profitsharingRuleResponseVoCompletableFuture
        ).join();
        //获取执行结果
        BigDecimal realDistance = realDistanceCompletableFuture.get();
        FeeRuleResponseVo feeRuleResponseVo = feeRuleResponseVoCompletableFuture.get();
        RewardRuleResponseVo rewardRuleResponseVo = rewardRuleCompletableFuture.get();
        ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleResponseVoCompletableFuture.get();

        //6.封装更新订单账单相关实体对象
        UpdateOrderBillForm updateOrderBillForm = new UpdateOrderBillForm();
        updateOrderBillForm.setOrderId(orderFeeForm.getOrderId());
        updateOrderBillForm.setDriverId(orderFeeForm.getDriverId());
        //路桥费、停车费、其他费用
        updateOrderBillForm.setTollFee(orderFeeForm.getTollFee());
        updateOrderBillForm.setParkingFee(orderFeeForm.getParkingFee());
        updateOrderBillForm.setOtherFee(orderFeeForm.getOtherFee());
        //乘客好处费
        updateOrderBillForm.setFavourFee(orderInfo.getFavourFee());
        //实际里程
        updateOrderBillForm.setRealDistance(realDistance);
        //订单奖励信息
        BeanUtils.copyProperties(rewardRuleResponseVo, updateOrderBillForm);
        //代驾费用信息
        BeanUtils.copyProperties(feeRuleResponseVo, updateOrderBillForm);
        //分账相关信息
        BeanUtils.copyProperties(profitsharingRuleResponseVo, updateOrderBillForm);
        updateOrderBillForm.setProfitsharingRuleId(profitsharingRuleResponseVo.getProfitsharingRuleId());
        log.info("结束代驾，更新账单信息：{}", JSON.toJSONString(updateOrderBillForm));

        //7.结束代驾更新账单
        orderInfoFeignClient.endDrive(updateOrderBillForm);
        return true;
    }

    @Override
    public PageVo findDriverOrderPage(Long driverId, Long page, Long limit) {
        return orderInfoFeignClient.findDriverOrderPage(driverId, page, limit).getData();
    }

    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        return orderInfoFeignClient.sendOrderBillInfo(orderId, driverId).getData();
    }

}
