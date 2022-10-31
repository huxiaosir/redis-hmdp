package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        // 从redis中查询类型缓存
        String typeJson = stringRedisTemplate.opsForValue().get(key);
        // 如果缓存不为空 直接返回
        if (StrUtil.isNotBlank(typeJson)) {
            List<ShopType> shopTypeList = JSONUtil.toList(typeJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        // 为空 则从数据库中查询，再存入redis中
        List<ShopType> list = query().orderByAsc("sort").list();
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(list));
        stringRedisTemplate.expire(key,RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(list);
    }
}
