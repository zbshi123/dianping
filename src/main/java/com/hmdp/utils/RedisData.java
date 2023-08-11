package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;


/***
 * 逻辑过期时间类。逻辑过期时间解决缓存击穿。
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
