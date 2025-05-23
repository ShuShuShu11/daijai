package com.atguigu.daijia.coupon.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.mapper.CouponInfoMapper;
import com.atguigu.daijia.coupon.mapper.CustomerCouponMapper;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.entity.coupon.CustomerCoupon;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {

    @Autowired
    private CouponInfoMapper couponInfoMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public PageVo<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoReceiveCouponVo> pageInfo = couponInfoMapper.findNoReceivePage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Override
    public PageVo<NoUseCouponVo> findNoUsePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoUseCouponVo> pageInfo = couponInfoMapper.findNoUsePage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Override
    public PageVo<UsedCouponVo> findUsedPage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<UsedCouponVo> pageInfo = couponInfoMapper.findUsedPage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Autowired
    private CustomerCouponMapper customerCouponMapper;

    //领取优惠卷
    @Override
    public Boolean receive(Long customerId, Long couponId) {
        //1 couponId查询优惠卷信息
        //判断如果优惠卷不存在
        CouponInfo couponInfo = couponInfoMapper.selectById(couponId);
        if(couponInfo == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //2 判断优惠卷是否过期
        if(couponInfo.getExpireTime().before(new Date())) {
            throw new GuiguException(ResultCodeEnum.COUPON_EXPIRE);
        }

        //3 检查库存，发行数量 和 领取数量
        if(couponInfo.getPublishCount() != 0 &&
                couponInfo.getReceiveCount() == couponInfo.getPublishCount()) {
            throw new GuiguException(ResultCodeEnum.COUPON_LESS);
        }
        RLock lock = null;
        try {
            // 分布式锁用客户id，防止同个用户并发请求，导致用户领取过多。
            // 而sql乐观锁，是防止多个用户，并发请求。
            lock = redissonClient.getLock(RedisConstant.COUPON_LOCK + customerId);
            boolean flag = lock.tryLock(RedisConstant.COUPON_LOCK_WAIT_TIME,
                    RedisConstant.COUPON_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if(flag) {
                //4 检查每个人限制领取数量
                if(couponInfo.getPerLimit() > 0) {
                    //统计当前客户已经领取优惠卷数量
                    LambdaQueryWrapper<CustomerCoupon> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(CustomerCoupon::getCouponId,couponId);
                    wrapper.eq(CustomerCoupon::getCustomerId,customerId);
                    Long count = customerCouponMapper.selectCount(wrapper);
                    //判断
                    if(count >= couponInfo.getPerLimit()) {
                        throw new GuiguException(ResultCodeEnum.COUPON_USER_LIMIT);
                    }
                }

                //5 领取优惠卷
                //5.1 更新领取数量
                int row = couponInfoMapper.updateReceiveCount(couponId);

                //5.2 添加领取记录
                this.saveCustomerCoupon(customerId,couponId,couponInfo.getExpireTime());

                return true;
            }
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(lock != null) {
                lock.unlock();
            }
        }
        return true;
    }

    @Override
    public List<AvailableCouponVo> findAvailableCoupon(Long customerId, BigDecimal orderAmount) {
        //1.定义符合条件的优惠券信息容器
        List<AvailableCouponVo> availableCouponVoList = new ArrayList<>();

        //2.获取未使用的优惠券列表
        List<NoUseCouponVo> list = couponInfoMapper.findNoUseList(customerId);

        //2.1.现金券
        List<NoUseCouponVo> type1List = list.stream().filter(item ->
                item.getCouponType().intValue() == 1).collect(Collectors.toList());
        for (NoUseCouponVo noUseCouponVo : type1List) {
            //使用门槛判断
            //2.1.1.没门槛，订单金额必须大于优惠券减免金额
            //减免金额
            BigDecimal reduceAmount = noUseCouponVo.getAmount();
            if (noUseCouponVo.getConditionAmount().doubleValue() == 0 && orderAmount.subtract(reduceAmount).doubleValue() > 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
            }
            //2.1.2.有门槛，订单金额大于优惠券门槛金额
            if (noUseCouponVo.getConditionAmount().doubleValue() > 0 && orderAmount.subtract(noUseCouponVo.getConditionAmount()).doubleValue() > 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
            }
        }

        //2.2.折扣券
        List<NoUseCouponVo> type2List = list.stream().filter(item ->
                item.getCouponType().intValue() == 2).collect(Collectors.toList());
        for (NoUseCouponVo noUseCouponVo : type2List) {
            //使用门槛判断
            //订单折扣后金额
            BigDecimal discountOrderAmount = orderAmount.multiply(noUseCouponVo.getDiscount()).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
            //减免金额
            BigDecimal reduceAmount = orderAmount.subtract(discountOrderAmount);
            //订单优惠金额
            //2.2.1.没门槛
            if (noUseCouponVo.getConditionAmount().doubleValue() == 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
            }
            //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
            if (noUseCouponVo.getConditionAmount().doubleValue() > 0 && discountOrderAmount.subtract(noUseCouponVo.getConditionAmount()).doubleValue() > 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
            }
        }

        //排序
        if (!CollectionUtils.isEmpty(availableCouponVoList)) {
            Collections.sort(availableCouponVoList, new Comparator<AvailableCouponVo>() {
                @Override
                public int compare(AvailableCouponVo o1, AvailableCouponVo o2) {
                    return o1.getReduceAmount().compareTo(o2.getReduceAmount());
                }
            });
        }
        return availableCouponVoList;
    }

    @Transactional(noRollbackFor = Exception.class)
    @Override
    public BigDecimal useCoupon(UseCouponForm useCouponForm) {
        //获取乘客优惠券
        CustomerCoupon customerCoupon = customerCouponMapper.selectById(useCouponForm.getCustomerCouponId());
        if(null == customerCoupon) {
            throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }
        //获取优惠券信息
        CouponInfo couponInfo = couponInfoMapper.selectById(customerCoupon.getCouponId());
        if(null == couponInfo) {
            throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }
        //判断该优惠券是否为乘客所有
        if(customerCoupon.getCustomerId().longValue() != useCouponForm.getCustomerId().longValue()) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        //获取优惠券减免金额
        BigDecimal reduceAmount = null;
        if(couponInfo.getCouponType().intValue() == 1) {
            //使用门槛判断
            //2.1.1.没门槛，订单金额必须大于优惠券减免金额
            if (couponInfo.getConditionAmount().doubleValue() == 0 && useCouponForm.getOrderAmount().subtract(couponInfo.getAmount()).doubleValue() > 0) {
                //减免金额
                reduceAmount = couponInfo.getAmount();
            }
            //2.1.2.有门槛，订单金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0 && useCouponForm.getOrderAmount().subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                //减免金额
                reduceAmount = couponInfo.getAmount();
            }
        } else {
            //使用门槛判断
            //订单折扣后金额
            BigDecimal discountOrderAmount = useCouponForm.getOrderAmount().multiply(couponInfo.getDiscount()).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
            //订单优惠金额
            //2.2.1.没门槛
            if (couponInfo.getConditionAmount().doubleValue() == 0) {
                //减免金额
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }
            //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0 && discountOrderAmount.subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                //减免金额
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }
        }
        if(reduceAmount.doubleValue() > 0) {
            int row = couponInfoMapper.updateUseCount(couponInfo.getId());
            if(row == 1) {
                CustomerCoupon updateCustomerCoupon = new CustomerCoupon();
                updateCustomerCoupon.setId(customerCoupon.getId());
                updateCustomerCoupon.setUsedTime(new Date());
                updateCustomerCoupon.setOrderId(useCouponForm.getOrderId());
                customerCouponMapper.updateById(updateCustomerCoupon);
                return reduceAmount;
            }
        }
        throw new GuiguException(ResultCodeEnum.DATA_ERROR);
    }

    private AvailableCouponVo buildBestNoUseCouponVo(NoUseCouponVo noUseCouponVo, BigDecimal reduceAmount) {
        AvailableCouponVo bestNoUseCouponVo = new AvailableCouponVo();
        BeanUtils.copyProperties(noUseCouponVo, bestNoUseCouponVo);
        bestNoUseCouponVo.setCouponId(noUseCouponVo.getId());
        bestNoUseCouponVo.setReduceAmount(reduceAmount);
        return bestNoUseCouponVo;
    }

    private void saveCustomerCoupon(Long customerId, Long couponId, Date expireTime) {
        CustomerCoupon customerCoupon = new CustomerCoupon();
        customerCoupon.setCustomerId(customerId);
        customerCoupon.setCouponId(couponId);
        customerCoupon.setStatus(1);
        customerCoupon.setReceiveTime(new Date());
        customerCoupon.setExpireTime(expireTime);
        customerCouponMapper.insert(customerCoupon);
    }

}
