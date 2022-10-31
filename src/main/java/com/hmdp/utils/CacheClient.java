package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * @author : joisen
 * @date : 21:12 2022/10/30
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                      Function<ID, R> dbCallBack, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1 从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2 判断是否存在
        if (StrUtil.isNotBlank(json)) { // 当里面存在字符串时为true，其他情况都为false
            // 3 存在 直接返回
            return JSONUtil.toBean(json, type);
        }
        // 需要判断命中的是否是空值
        if (json != null){
            // 放回错误信息
            return null;
        }
        // 4 不存在 根据id查询数据库
        R r = dbCallBack.apply(id);
        // 5 不存在 返回false (查数据库)
        if (r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"");
            stringRedisTemplate.expire(key,RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 存在  写入redis
        this.set(key,r,time,unit);
        // 返回
        return r;
    }


    // 创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 缓存击穿解决方法
    public <R, ID> R queryWithLogicalExpire(String keyPrefix,ID id,
            Class<R> type, Function<ID,R> dbCallBack, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1 从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2 判断是否存在
        if (StrUtil.isBlank(json)) { // 当里面存在字符串时为true，其他情况都为false
            // 3 不存在 直接返回
            return null;
        }
        // 4 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回店铺信息
            return r;
        }
        // 5.2 已过期，需要缓存重建
        // 6 缓存重建
        // 6.1 获取互斥锁
        String lockKey = keyPrefix + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁成功
        if (isLock) {
            //  6.3 成功  开启独立线程，实现缓存重建
            // 注意 获取锁成功应该再次检测redis缓存是否过期 ，做doubleCheck。如果未过期，则无需重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    // 查询数据库
                    R r1 = dbCallBack.apply(id);
                    // 将信息存入redis
                    this.setWithLogicalExpire(key,r1,time,unit);

                } catch (Exception e) {
                    throw new RuntimeException();
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.4 是否失败都要返回商铺信息
        return r;
    }

    // 上锁，解决缓存击穿，通过redis的setnx来设置一个值，当该值存在时返回false，已达到互斥的作用
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag); // 不能直接返回flag，直接返回flag会出现自动拆箱，可能出现空指针。
    }
    // 释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


}
