package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private UserServiceImpl userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        //shopService.saveShop2Redis(1L, 10L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void createUserTokens() {
        String path = "src/main/resources/tokens.txt";

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(path));

            for (int i = 1; i <= 100; i++) {
                User user = userService.query().eq("id", i).one();
                if (user == null) continue;
                // 7.1. 随机生成token，作为登录令牌
                String token = UUID.randomUUID().toString(true);
                // 7.2. 将User对象转化为HashMap存储
                UserDTO userDTO = new UserDTO();
                BeanUtils.copyProperties(user, userDTO);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true) // 忽略空值
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())); // UserDTO的long类型转String 传入字段名和字段值，返回修改后的字段值
                // 7.3. 存储
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                // 7.4.设置token有效期
                stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

                out.write(token + System.lineSeparator());
            }

            //out.write("test");
            out.close();
        } catch (IOException e) {
        }



    }

    @Test
    void loadShopData() {
        // 1. 查询店铺信息
        List<Shop> list = shopService.list();
        // 2. 把店铺分组，按照typeId分组，typeId一致的放到一个集合里
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3. 分批完成写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1. 获取类型id
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            // 3.2 获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}
