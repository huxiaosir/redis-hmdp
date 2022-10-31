package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author : joisen
 * @date : 10:59 2022/10/29
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    // 由于LoginInterceptor没有交给spring管理，在获取对象时采用new，
    // 故注入redis不能采用添加注解的方式,采用构造函数注入的方式
    private StringRedisTemplate stringRedisTemplate;
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        log.info("RefreshTokenInterceptor拦截到请求：{}",uri);
        //  1 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 4 令牌不存在是不需要刷新的，直接放行给下一个拦截器处理
            log.info("令牌不存在，RefreshTokenInterceptor已放行请求：{}",uri);
            return true;
        }
        //  2 基于token获取redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 3 判断用户是否存在
        if (userMap.isEmpty()) {
            //用户不存在的话也不需要刷新令牌，直接放行给下一个拦截器处理
            log.info("令牌不存在，RefreshTokenInterceptor已放行请求：{}",uri);
            return true;
        }
        // todo 5 将查询到的Hash数据转换为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(),false);
        // 6 存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        //todo 7 刷新touken有效期
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
