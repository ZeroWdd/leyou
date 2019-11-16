package com.leyou.order.service;

import com.leyou.auth.entity.UserInfo;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.util.IdWorker;
import com.leyou.order.interceptor.LoginInterceptor;
import com.leyou.order.mapper.OrderDetailMapper;
import com.leyou.order.mapper.OrderMapper;
import com.leyou.order.mapper.OrderStatusMapper;
import com.leyou.order.pojo.Order;
import com.leyou.order.pojo.OrderDetail;
import com.leyou.order.pojo.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * @Auther: wdd
 * @Date: 2019/11/15 17:43
 * @Description:
 */
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderStatusMapper orderStatusMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IdWorker idWorker;

    static final String KEY_PREFIX = "leyou:cart:uid:";

    @Transactional
    public Long createOrder(Order order) {
        long orderId = 0;
        int count = 0;
        try {
            //生成id
            orderId = idWorker.nextId();
            //获取登录用户
            UserInfo userInfo = LoginInterceptor.getLoginUser();
            // 初始化数据
            order.setBuyerNick(userInfo.getUsername());
            order.setBuyerRate(false);
            order.setCreateTime(new Date());
            order.setOrderId(orderId);
            order.setUserId(userInfo.getId());
            count = orderMapper.insertSelective(order);
            if(count != 1){
                throw new LyException(ExceptionEnum.ORDER_SAVE_ERROR);
            }

            // 保存订单状态
            OrderStatus orderStatus = new OrderStatus();
            orderStatus.setOrderId(orderId);
            orderStatus.setCreateTime(order.getCreateTime());
            orderStatus.setStatus(1);// 初始状态为未付款
            count = orderStatusMapper.insertSelective(orderStatus);
            if(count != 1){
                throw new LyException(ExceptionEnum.ORDER_SAVE_ERROR);
            }

            // 订单详情中添加orderId
            long finalOrderId = orderId;
            order.getOrderDetails().forEach(od -> od.setOrderId(finalOrderId));
            // 保存订单详情,使用批量插入功能
            count = orderDetailMapper.insertList(order.getOrderDetails());
            if(count != order.getOrderDetails().size()){
                throw new LyException(ExceptionEnum.ORDER_SAVE_ERROR);
            }

            //更新库存

            //删除购物车数据 redis
            String key = KEY_PREFIX + userInfo.getId();
            BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(key);
            order.getOrderDetails().forEach(od -> hashOps.delete(od.getSkuId().toString()));
        } catch (LyException e) {
            e.printStackTrace();
            throw new LyException(ExceptionEnum.ORDER_SAVE_ERROR);
        }
        return orderId;
    }


    public Order queryById(Long id) {
        Order order = null;
        try {
            // 查询订单
            order = orderMapper.selectByPrimaryKey(id);
            if(StringUtils.isEmpty(order)){
                throw new LyException(ExceptionEnum.ORDER_NOT_FOUND);
            }
            // 查询订单详情
            OrderDetail detail = new OrderDetail();
            detail.setOrderId(id);
            List<OrderDetail> details = orderDetailMapper.select(detail);
            order.setOrderDetails(details);
            if(CollectionUtils.isEmpty(details)){
                throw new LyException(ExceptionEnum.ORDER_NOT_FOUND);
            }
            // 查询订单状态
            OrderStatus status = orderStatusMapper.selectByPrimaryKey(order.getOrderId());
            order.setStatus(status.getStatus());
            if(StringUtils.isEmpty(status)){
                throw new LyException(ExceptionEnum.ORDER_NOT_FOUND);
            }
        } catch (LyException e) {
            e.printStackTrace();
            throw new LyException(ExceptionEnum.ORDER_NOT_FOUND);
        }
        return order;
    }
}
