package com.atguigu.daijia.dispatch.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.dispatch.mapper.OrderJobMapper;
import com.atguigu.daijia.dispatch.service.NewOrderService;
import com.atguigu.daijia.dispatch.xxl.client.XxlJobClient;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.dispatch.OrderJob;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class NewOrderServiceImpl implements NewOrderService {

    @Autowired
    private XxlJobClient xxlJobClient;

    @Autowired
    private OrderJobMapper orderJobMapper;

    @Autowired
    private OrderInfoFeignClient orderInfoFeignClient;

    @Autowired
    private LocationFeignClient locationFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    // 创建job并启动,完成定时任务！
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addAndStartTask(NewOrderTaskVo newOrderTaskVo) {

        OrderJob orderJob = orderJobMapper.selectOne(new LambdaQueryWrapper<OrderJob>().eq(OrderJob::getOrderId, newOrderTaskVo.getOrderId()));
        if(null == orderJob) {

            // 创建job并启动，返回jobId
            Long jobId = xxlJobClient.addAndStart("newOrderTaskHandler", "", "0 0/1 * * * ?", "新订单任务,订单id："+newOrderTaskVo.getOrderId());

            //记录订单与任务的关联信息
            orderJob = new OrderJob();
            orderJob.setOrderId(newOrderTaskVo.getOrderId());
            orderJob.setJobId(jobId);
            orderJob.setParameter(JSONObject.toJSONString(newOrderTaskVo));
            orderJobMapper.insert(orderJob);
        }
        return orderJob.getJobId();
    }

    // 真正要执行的任务：搜索附件代驾司机
    @Override
    public void executeTask(long jobId) {

        //1 根据jobid查询数据库，当前任务是否已经创建
        //如果没有创建，不往下执行了
        LambdaQueryWrapper<OrderJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderJob::getOrderId,jobId);
        OrderJob orderJob = orderJobMapper.selectOne(wrapper);
        if(orderJob == null){
            System.out.println("1：没有创建Job!!!!!!!!!!!!!!!!!!!!!!!!1");
            return; // 不继续执行
        }
        System.out.println("1：创建了Job!!!!!!!!!!!!!!!!!!!!!!!!1");


        //2 查询订单状态，如果当前订单接单状态，继续执行。如果当前订单不是接单状态，停止任务调度
        //获取OrderJob里面对象
        String jsonString = orderJob.getParameter();
        NewOrderTaskVo newOrderTaskVo = JSONObject.parseObject(jsonString, NewOrderTaskVo.class);

        //获取orderId
        Long orderId = newOrderTaskVo.getOrderId();
        Integer status = orderInfoFeignClient.getOrderStatus(orderId).getData();
        if(status.intValue() != OrderStatus.WAITING_ACCEPT.getStatus().intValue()) {
            //停止任务调度
            xxlJobClient.stopJob(jobId);
            return;
        }


        //3 远程调用:搜索附近满足条件可以接单司机，获取满足可以接单司机集合
        SearchNearByDriverForm searchNearByDriverForm = new SearchNearByDriverForm();
        searchNearByDriverForm.setLongitude(newOrderTaskVo.getStartPointLongitude());
        searchNearByDriverForm.setLatitude(newOrderTaskVo.getStartPointLatitude());
        searchNearByDriverForm.setMileageDistance(newOrderTaskVo.getExpectDistance());
        //远程调用
        List<NearByDriverVo> nearByDriverVoList =
                locationFeignClient.searchNearByDriver(searchNearByDriverForm).getData();

        //5 遍历司机集合，得到每个司机，为每个司机创建临时队列，存储新订单信息
        // 给司机派发订单信息
        nearByDriverVoList.forEach(driver -> {
            //记录司机id，防止重复推送订单信息
            String repeatKey = RedisConstant.DRIVER_ORDER_REPEAT_LIST+newOrderTaskVo.getOrderId();
            // 判断driverId 是否已经存在于以 repeatKey 为键的 Redis 集合中。
            boolean isMember = redisTemplate.opsForSet().isMember(repeatKey, driver.getDriverId());
            if(!isMember) {
                //记录该订单已放入司机临时容器，将 driverId 添加到以 repeatKey 为键的 Redis 集合中
                // 每个乘客就有一个redis集合！！  将该司机放入！！！
                redisTemplate.opsForSet().add(repeatKey, driver.getDriverId());
                //过期时间：15分钟，新订单15分钟没人接单自动取消
                redisTemplate.expire(repeatKey, RedisConstant.DRIVER_ORDER_REPEAT_LIST_EXPIRES_TIME, TimeUnit.MINUTES);

                NewOrderDataVo newOrderDataVo = new NewOrderDataVo();
                newOrderDataVo.setOrderId(newOrderTaskVo.getOrderId());
                newOrderDataVo.setStartLocation(newOrderTaskVo.getStartLocation());
                newOrderDataVo.setEndLocation(newOrderTaskVo.getEndLocation());
                newOrderDataVo.setExpectAmount(newOrderTaskVo.getExpectAmount());
                newOrderDataVo.setExpectDistance(newOrderTaskVo.getExpectDistance());
                newOrderDataVo.setExpectTime(newOrderTaskVo.getExpectTime());
                newOrderDataVo.setFavourFee(newOrderTaskVo.getFavourFee());
                newOrderDataVo.setDistance(driver.getDistance());
                newOrderDataVo.setCreateTime(newOrderTaskVo.getCreateTime());

                // 将乘客放入到该司机的临时队列！司机接单了会定时轮询到他的临时队列获取订单消息
                String key = RedisConstant.DRIVER_ORDER_TEMP_LIST+driver.getDriverId();
                redisTemplate.opsForList().leftPush(key, JSONObject.toJSONString(newOrderDataVo));
                //过期时间：1分钟，1分钟未消费，自动过期
                //注：司机端开启接单，前端每5秒（远小于1分钟）拉取1次“司机临时队列”里面的新订单消息
                redisTemplate.expire(key, RedisConstant.DRIVER_ORDER_TEMP_LIST_EXPIRES_TIME, TimeUnit.MINUTES);
                log.info("该新订单信息已放入司机临时队列: {}", JSON.toJSONString(newOrderDataVo));

            }
        });
    }

    // 查询司机新订单数据
    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {

        ArrayList<NewOrderDataVo> dataVos = new ArrayList<>();

        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        Long size = redisTemplate.opsForList().size(key);
        if (size > 0){
            for (int i =0 ; i<size; i++){
                String content =  (String)redisTemplate.opsForList().leftPop(key);
                NewOrderDataVo newOrderDataVo = JSONObject.parseObject(content, NewOrderDataVo.class);
                dataVos.add(newOrderDataVo);
            }
        }
        return dataVos;
    }

    @Override
    public Boolean clearNewOrderQueueData(Long driverId) {
        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        //直接删除，司机开启服务后，有新订单会自动创建容器
        redisTemplate.delete(key);
        return true;
    }
}
