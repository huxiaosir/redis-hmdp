package com.hmdp.utils;

/**
 * 实现分布式锁接口
 * @author : joisen
 * @date : 18:37 2022/10/31
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec  锁持有的超时时间，过期后自动释放
     * @return true代表取锁成功；false代表取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
