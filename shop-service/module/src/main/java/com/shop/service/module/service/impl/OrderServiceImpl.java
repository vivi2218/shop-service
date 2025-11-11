package com.shop.service.module.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shop.service.module.entity.*;
import com.shop.service.module.mapper.*;
import com.shop.service.module.service.OrderService;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OrderServiceImpl extends BaseServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private TeamUserMapper teamUserMapper;

    @Autowired
    private TeamMapper teamMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Result insertOrder(OrderEntity orderEntity, JSONObject userInfo) {
        SqlSession sqlSession = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false);
        GoodsMapper goodsMapper = sqlSession.getMapper(GoodsMapper.class);
        OrderMapper orderMapper = sqlSession.getMapper(OrderMapper.class);
        String password = orderEntity.getPassword();
        String userPassword = userInfo.get("password").toString();
        BCryptPasswordEncoder p = new BCryptPasswordEncoder();
        if(password == null){
            sqlSession.rollback();
            sqlSession.close();
            return Result.end(500,"","支付密码不可以为空");
        }
        if(p.matches(password,userPassword) == false){
            sqlSession.rollback();
            sqlSession.close();
            return Result.end(500,"","支付密码错误");
        }
        GoodsEntity goods = goodsMapper.selectById(orderEntity.getGoodsId());
        if(orderEntity.getCount() > goods.getCount()){
            sqlSession.rollback();
            sqlSession.close();
            return Result.end(500,"","库存不足");
        }
        goods.setCount(goods.getCount()-orderEntity.getCount());
        goods.setSaleCount(goods.getSaleCount()+orderEntity.getCount());
        goodsMapper.updateById(goods);
        String orderNo = "SPDD-"+new Date().getTime()+ UUID.randomUUID().toString().split("-")[0];
        orderEntity.setOrderNo(orderNo);
        orderEntity.setStatus(1);
        int res = orderMapper.insert(orderEntity);

        System.out.println(res);
        sqlSession.commit();
        sqlSession.close();
        return Result.end(200,orderEntity,"支付成功");


    }

    @Override
    @Transactional
    public Result insertFromBuyCar(JSONObject params, JSONObject userInfo) {
        SqlSession sqlSession = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false);
        GoodsMapper goodsMapper = sqlSession.getMapper(GoodsMapper.class);
        BuyCarMapper buyCarMapper = sqlSession.getMapper(BuyCarMapper.class);
        OrderMapper orderMapper = sqlSession.getMapper(OrderMapper.class);
        String password = params.get("password").toString();
        String userPassword = userInfo.get("password").toString();
        BCryptPasswordEncoder p = new BCryptPasswordEncoder();
        if(password == null){
            sqlSession.rollback();
            sqlSession.close();
            return Result.end(500,"","支付密码不可以为空");
        }
        if(p.matches(password,userPassword) == false){
            sqlSession.rollback();
            sqlSession.close();
            return Result.end(500,"","支付密码错误");
        }
        List orders = (List) params.get("orders");
        AtomicReference<Result> result = null;
        orders.forEach(item -> {
            String eachOrderJson = JSONObject.toJSONString(item);
            OrderEntity eachOrder = JSONObject.parseObject(eachOrderJson, OrderEntity.class);
            eachOrder.setUserId(Long.valueOf(userInfo.get("id").toString()));
            eachOrder.setInsertTime(new Date());
            String orderNo = "SPDD-"+new Date().getTime()+ UUID.randomUUID().toString().split("-")[0];
            eachOrder.setOrderNo(orderNo);
            eachOrder.setStatus(1);
            GoodsEntity goods = goodsMapper.selectById(eachOrder.getGoodsId());
            if(eachOrder.getCount() > goods.getCount()){
                result.set(Result.end(500, "", "结算的商品有某个库存不足，请更新数据重新下单"));
            }
            goods.setCount(goods.getCount()-eachOrder.getCount());
            goods.setSaleCount(goods.getSaleCount()+eachOrder.getCount());
            goodsMapper.updateById(goods);
            orderMapper.insert(eachOrder);
            BuyCarEntity buyCar = buyCarMapper.selectById(eachOrder.getBuyCarId());
            buyCar.setIsDeal(1);
            buyCar.setBuyCount(eachOrder.getCount());
            buyCar.setOrderNo(orderNo);
            buyCar.setOrderId(eachOrder.getId());
            buyCarMapper.updateById(buyCar);
        });
        if(result!=null){
            sqlSession.rollback();
            sqlSession.close();
            return result.get();
        }else{
            sqlSession.commit();
            sqlSession.close();
            return Result.end(200,"","购物车结算成功");
        }

    }

    @Override
    public Result send(Long id, String postCode) {
        OrderEntity order = orderMapper.selectById(id);
        order.setExpressNo(postCode);
        order.setStatus(2);
        orderMapper.updateById(order);
        return Result.end(200,"","发货成功");
    }

    @Override
    public Result getGoods(Long id) {

        OrderEntity order = orderMapper.selectById(id);
        order.setStatus(3);
        orderMapper.updateById(order);
        return Result.end(200,"","收货成功");
    }

    @Override
    public Result fallbackGoods(OrderEntity order) {
        OrderEntity orderEntity = orderMapper.selectById(order.getId());
        orderEntity.setFallbackImg(order.getFallbackImg());
        orderEntity.setFallbackReason(order.getFallbackReason());
        orderEntity.setFallbackTime(new Date());
        orderEntity.setStatus(4);
        orderMapper.updateById(orderEntity);
        return Result.end(200,"","退款申请成功");
    }

    @Override
    public Result fallbackGoodsOver(Long id) {
        OrderEntity order = orderMapper.selectById(id);
        order.setStatus(5);
        orderMapper.updateById(order);
        return Result.end(200,"","退款处理成功");
    }

    @Override
    public Result updateFromTeam(String id, UserEntity user) {
        OrderEntity order = orderMapper.selectById(id);
        QueryWrapper<AddressEntity> q = new QueryWrapper<AddressEntity>();
        q.eq("user_id",user.getId());
        q.orderByDesc("is_default");
        List<AddressEntity> list = addressMapper.selectList(q);
        if(list.size() == 0){
            return Result.end(500,"","您还没有设置默认收货地址请设置");
        }
        if(list.get(0).getIsDefault()!=1){
            return Result.end(500,"","您还没有设置默认收货地址请设置");
        }
        AddressEntity address = list.get(0);
        order.setProvince(address.getProvince());
        order.setCity(address.getCity());
        order.setName(address.getName());
        order.setArea(address.getArea());
        order.setAddress(address.getAddress());
        order.setPostCode(address.getPostCode());
        order.setPhone(address.getPhone());
        order.setType(3);
        order.setUserId(Long.valueOf(user.getId()+""));
        order.setRemark("活动下单");
        if(order.getStatus() == 0){
            order.setStatus(1);
        }
        orderMapper.updateById(order);
        return Result.end(200,"","支付成功");
    }

    @Override
    @Transactional
    public Result removeOrder(String id, UserEntity user) {
        OrderEntity order = orderMapper.selectById(id);
        Long teamId = order.getTeamId();
        QueryWrapper q = new QueryWrapper();
        q.eq("team_id",teamId);
        q.eq("user_id",user.getId());
        teamUserMapper.delete(q);
        TeamEntity team = teamMapper.selectById(teamId);
        if(team.getStatus() == 2){
            team.setStatus(0);

        }
        String prefix = "team_activity_";
        team.setHasMember(team.getHasMember()-1);
        redisTemplate.opsForValue().getAndSet(prefix+teamId,JSONObject.toJSONString(team));
        teamMapper.updateById(team);
        orderMapper.deleteById(order);
        return Result.end(200,"","取消订单成功");

    }

    @Override
    @Transactional
    public Result createTeamOrder(JSONObject orderData) {
        try {
            String prefix = "team_activity_";
            String orderPrefix = "team_order_";
            String teamId = orderData.getString("team_id");
            String userId = orderData.getString("user_id");
            
            // 从Redis获取团队信息
            String teamJson = (String) redisTemplate.opsForValue().get(prefix + teamId);
            TeamEntity team = JSONObject.parseObject(teamJson, TeamEntity.class);
            
            if (team == null) {
                return Result.end(500, "", "团队信息不存在");
            }
            
            Long goodsId = team.getGoodsId();
            
            // 创建订单
            OrderEntity order = new OrderEntity();
            order.setUserId(Long.valueOf(userId));
            order.setGoodsId(goodsId);
            String orderNo = "PTDD-" + System.currentTimeMillis() + UUID.randomUUID().toString().split("-")[0].toUpperCase(Locale.ROOT);
            order.setOrderNo(orderNo);
            order.setInsertTime(new Date());
            order.setStatus(0);
            order.setTeamId(Long.valueOf(teamId));
            order.setActivityName(team.getName());
            order.setActivityDiscount(team.getTeamDiscount());
            
            // 获取商品信息
            GoodsEntity goods = goodsMapper.selectById(goodsId);
            order.setGoodsDiscount(goods.getDiscount());
            order.setGoodsName(goods.getName());
            order.setGoodsPrice(goods.getPrice());
            order.setCount(1);
            order.setPay(goods.getPrice() * goods.getDiscount() * team.getTeamDiscount() / 100);
            
            // 保存订单
            orderMapper.insert(order);
            
            // 添加团队成员
            TeamUserEntity teamUser = new TeamUserEntity();
            teamUser.setTeamId(Long.valueOf(teamId));
            teamUser.setUserId(Long.valueOf(userId));
            teamUserMapper.insert(teamUser);
            
            // 更新团队状态
            if (team.getSize() == team.getHasMember()) {
                team.setStatus(2);
            }
            teamMapper.updateById(team);
            
            // 设置Redis标识
            redisTemplate.opsForValue().setIfAbsent(orderPrefix + teamId + userId, 1, Duration.ofMinutes(15));
            
            return Result.end(200, "", "订单创建成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.end(500, "", "订单创建失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result cancelTeamOrder(JSONObject orderData) {
        try {
            Long orderId = orderData.getLong("orderId");
            
            // 查询订单
            OrderEntity order = orderMapper.selectById(orderId);
            if (order == null) {
                return Result.end(500, "", "订单不存在");
            }
            
            // 查询团队信息
            Long teamId = order.getTeamId();
            String prefix = "team_activity_";
            String teamJson = (String) redisTemplate.opsForValue().get(prefix + teamId);
            TeamEntity team = JSONObject.parseObject(teamJson, TeamEntity.class);
            
            if (team != null) {
                // 删除团队成员
                QueryWrapper q = new QueryWrapper();
                q.eq("user_id", order.getUserId());
                q.eq("team_id", teamId);
                teamUserMapper.delete(q);
                
                // 更新团队信息
                team.setHasMember(team.getHasMember() - 1);
                if (team.getStatus() == 2) {
                    team.setStatus(0);
                }
                redisTemplate.opsForValue().getAndSet(prefix + teamId, JSONObject.toJSONString(team));
                teamMapper.updateById(team);
            }
            
            // 删除订单
            orderMapper.deleteById(order);
            
            return Result.end(200, "", "订单取消成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.end(500, "", "订单取消失败: " + e.getMessage());
        }
    }
}
