package com.shop.service.mqclient.reciver;

import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.shop.service.mqclient.feign.ShopFeignClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@RabbitListener(queues = "OrderTimeoutDeadQueue")
@Component
public class OrderDeadReciver {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ShopFeignClient shopFeignClient;

    @RabbitHandler
    public void process(JSONObject data, Message message, Channel channel){
        System.out.println("触发订单超时死信队列");
        System.out.println(data);
        
        try {
            String id = data.get("team_id").toString();
            String userId = data.get("user_id").toString();
            System.out.println("团队id是：" + id);
            System.out.println("用户id是：" + userId);
            
            // 构建取消订单数据
            JSONObject orderData = new JSONObject();
            // 这里简化处理，实际可能需要先查询订单ID
            // 也可以在OrderController中添加根据teamId和userId查询待支付订单的接口
            // 为了演示，假设我们已经有了订单ID的获取方式
            orderData.put("team_id", id);
            orderData.put("user_id", userId);
            
            // 调用shop服务的取消订单接口
            com.shop.service.module.entity.Result result = shopFeignClient.cancelTeamOrder(orderData);
            
            if (result.getCode() == 200) {
                System.out.println("订单取消成功: " + orderData);
                // 确认消息
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), true);
            } else {
                System.out.println("订单取消失败: " + result.getData());
                // 拒绝消息，不再重试
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                // 发生异常，拒绝消息
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
}
