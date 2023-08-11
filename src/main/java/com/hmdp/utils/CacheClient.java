package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/***
 * redis缓存工具类
 */
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /***
     * 缓存一个对象到redis中
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /***
     * 将对象封装在逻辑过期时间类中，再缓存到redis中
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /***
     * 使用缓存空值方案防止缓存穿透，使用了泛型和函数式编程
     * @param id
     * @return
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                          Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否缓存是否命中
        if (StrUtil.isNotBlank(json)) { // isNotBlank == is not " " and null
            // 3. 若命中，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 如果缓存的是空值
        if (json != null) { // 等价于 "".equals(shopJson) 这里才是判断缓存空值的地方，因为缓存的空值是""
            // 返回错误信息
            return null;
        }

        // 4. 若缓存未命中，则根据id查询数据库
        //R r = getById(id); 这句不能执行，因为使用了泛型，所以将查数据库的逻辑交给调用者，让调用者传入一段代码进来
        R r = dbFallback.apply(id);

        // 5. 数据库不存在，返回错误
        if (r == null) {
            // 防止缓存穿透，使用缓存空值的方案
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 存在，写入redis
        this.set(key, r, time, unit);

        // 7. 返回
        return r;
    }
}
