package com.shop.service.mqclient.reciver;

import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.shop.service.mqclient.feign.ShopFeignClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RabbitListener(queues = "OrderInitQueue")
@Component
public class OrderReciver {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ShopFeignClient shopFeignClient;

    @RabbitHandler
    public void process(JSONObject data, Message message, Channel channel){
        System.out.println("订单队列");
        String id = data.get("team_id").toString();
        String userId = data.get("user_id").toString();
        System.out.println("发送的用户id是："+userId);
        
        try {
            // 构建订单数据
            JSONObject orderData = new JSONObject();
            orderData.put("team_id", id);
            orderData.put("user_id", userId);
            
            // 调用shop服务的创建订单接口
            com.shop.service.module.entity.Result result = shopFeignClient.createTeamOrder(orderData);
            
            if (result.getCode() == 200) {
                System.out.println("订单创建成功: " + orderData);
                // 发送确认消息到死信队列
                JSONObject j = new JSONObject();
                j.put("user_id", userId);
                j.put("team_id", id);
                // 订单号可能需要从结果中获取，这里简单处理
                
                // 确认消息
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), true);
            } else {
                System.out.println("订单创建失败: " + result.getData());
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
        // 发送消息到订单超时队列
        try {
            JSONObject j = new JSONObject();
            j.put("user_id", userId);
            j.put("team_id", id);
            rabbitTemplate.convertAndSend("OrderTimeoutExchange", "OrderTimeoutRouter", j,
                    message1 -> {
                        message1.getMessageProperties().setExpiration((15*60*1000)+"");
                        return message1;
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
