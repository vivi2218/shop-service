package com.shop.service.mqclient.feign;

import com.alibaba.fastjson.JSONObject;
import com.shop.service.module.entity.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Feign客户端，用于调用shop-service的REST接口
 */
@FeignClient(name = "shop-service", url = "http://localhost:8003")
public interface ShopFeignClient {

    /**
     * 创建订单
     */
    @PostMapping("/order/create/team")
    Result createTeamOrder(@RequestBody JSONObject orderData);

    /**
     * 取消订单
     */
    @PostMapping("/order/cancel/team")
    Result cancelTeamOrder(@RequestBody JSONObject orderData);

    /**
     * 更新团队信息
     */
    @PutMapping("/team/update")
    Result updateTeam(@RequestBody JSONObject teamData);

    /**
     * 获取团队信息
     */
    @GetMapping("/team/find/id/{id}")
    Result getTeamById(@PathVariable("id") Long id);

    /**
     * 更新订单状态
     */
    @PutMapping("/order/update/status")
    public Result updateOrderStatus(@RequestParam("orderId") Long orderId, @RequestParam("status") Integer status);
    
    /**
     * 使团队活动超时
     */
    @PutMapping("/team/timeout/{id}")
    Result makeActivityTimeout(@PathVariable("id") Long id);

    /**
     * 根据订单号查询订单
     */
    @GetMapping("/order/find/orderNo/{orderNo}")
    Result findOrderByOrderNo(@PathVariable("orderNo") String orderNo);
}