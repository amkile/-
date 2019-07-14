package com.yazuo.zeus.openapi.core.lock;

import com.yazuo.baikal.cache.client.api.PrivateCacheClient;
import com.yazuo.baikal.cache.client.api.PrivateCacheClientClusterImpl;
import com.yazuo.baikal.cache.client.api.PrivateCacheClientPoolImpl;
import com.yazuo.baikal.cache.client.api.PublicCacheClient;
import com.yazuo.baikal.cache.client.config.CacheClientConfig;
import com.yazuo.baikal.cache.client.enums.JedisConfigType;
import com.yazuo.baikal.cache.client.util.ConfigCenterUtil;
import com.yazuo.baikal.cache.client.vo.JedisAddress;
import com.yazuo.baikal.cache.client.vo.JedisConfig;
import com.yazuo.common.yazuolog.YazuoLogger;
import com.yazuo.zeus.openapi.core.utils.PageUtils;
import com.yazuo.zeus.openapi.trade.controller.ThirdPartyActiveController;
import org.apache.commons.io.FileUtils;
import org.aspectj.util.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.*;
import redis.clients.util.Pool;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;


/**
 * 分布式锁
 *
 * 注意：
 * 1.此处破坏了类的封装，通过反射获取雅座封装的私有数据，因为我找不到底层项目，而且之前人没有实现执行脚本的接口，
 * 这里通过反射获取原始jedis执行lua脚本。
 * 2.雅座缓存替换需要替换这里的实现
 * ^_^
 * @Author: yangguolong
 * @Description:
 * @Date: Create in 10:27 2019/6/12
 */
@Service
public class RedisLock {
    private static final YazuoLogger logger = YazuoLogger.create(RedisLock.class);

    @Resource
    private  PrivateCacheClient privateCacheClient;

    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_WITH_EXPIRE_TIME = "PX";
    private static final String SUCCESS = "OK";
    private static JedisConfigType REDIS_TYPE;
    private static final String FILE_PATH = RedisLock.class.getResource("/").getPath()+"unlock.lua";
    private ThreadLocal<String> LOCAL_UUID = new ThreadLocal<>();
    private static String SCRIPT;

    public RedisLock(){
    }

    @PostConstruct
    public void config() throws IOException, NoSuchFieldException, IllegalAccessException {
        CacheClientConfig cacheClientConfig = (CacheClientConfig) getFieldValue(privateCacheClient.getClass().getSuperclass(),privateCacheClient,"config");
        JedisConfig remoteConfig = ConfigCenterUtil.getJedisConfig(cacheClientConfig.getConfigCenterUrl());
        REDIS_TYPE = remoteConfig.getConfigType();
        SCRIPT = FileUtils.readFileToString(new File(FILE_PATH));
        logger.info("redisLock初始化成功，redis模式："+REDIS_TYPE);
    }


    public boolean tryLock(String lock,int expireTime) {
        String uuid = UUID.randomUUID().toString();
        LOCAL_UUID.set(uuid);
        // 原子操作
        String result = privateCacheClient.set(lock,uuid,SET_IF_NOT_EXIST,SET_WITH_EXPIRE_TIME,expireTime);
        if (SUCCESS.equals(result!=null?result.toUpperCase():null)) {
            return true;
        }
        return  false;
    }

    /**
     *
     * @param lock
     * @param expireTime 毫秒
     */
    public void lock(String lock,int expireTime) {
        Thread t = Thread.currentThread();
        logger.info("等待获取锁：lock="+lock+",超时时间："+expireTime+",线程id："+t.getId()+",线程名称："+t.getName());
        while (!tryLock(lock,expireTime)){};
        logger.info("获取锁成功：lock="+lock+",超时时间："+expireTime+",线程id："+t.getId()+",线程名称："+t.getName());
    }


    public void unLock(String lock) throws NoSuchFieldException {
        Object o = null;
        logger.info("解锁开始");
        try {
            switch (REDIS_TYPE) {
                case JedisCluster:
                    o = execute1(SCRIPT, lock, LOCAL_UUID.get());
                    break;
                case JedisPool:
                    o = execute2(SCRIPT, lock, LOCAL_UUID.get());
                    break;
                case JedisSentinelPool:
                    o = execute2(SCRIPT, lock, LOCAL_UUID.get());
                    break;
                default:
                    break;
            }
            logger.info("解锁完成，解锁结果："+o);
        } catch (IllegalAccessException e) {
            logger.info(e);
        } catch (NoSuchFieldException e) {
            logger.info("redisLock反射失效："+e);
            throw new NoSuchFieldException("redisLock反射失效，请查看是否更换redis方法："+e);
        }

    }

    private Object execute1(String script,String lock, String uuid) throws NoSuchFieldException, IllegalAccessException {
        // 此处未作关闭处理，仿照雅座写的，不清楚为啥？
        JedisCluster j = getRedisSource1();
        // 和雅座统一key值前缀
        lock = privateCacheClient.getPrivateCacheKey(lock);
        return j.eval(script,Arrays.asList(lock),Arrays.asList(uuid));
    }

    private Object execute2(String script,String lock, String uuid) throws NoSuchFieldException, IllegalAccessException {
        // 此处做了关闭处理
        try (Jedis j = getRedisSource2()){
            // 和雅座统一key值前缀
            lock = privateCacheClient.getPrivateCacheKey(lock);
            return j.eval(script,Arrays.asList(lock),Arrays.asList(uuid));
        }
    }


    /**
     * 反射的数据
     */
    private static JedisCluster jedisCluster;

    /**
     * 反射的数据
     */
    private static Pool<Jedis> pool;
    /**
     * 原始接口，key值需要通过雅座getPrivateCacheKey封装
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private JedisCluster getRedisSource1() throws NoSuchFieldException, IllegalAccessException {
        if (jedisCluster != null) {
            return jedisCluster;
        }
        jedisCluster = (JedisCluster) getFieldValue(privateCacheClient.getClass(),privateCacheClient,"cluster");
        return jedisCluster;
    }

    private Jedis getRedisSource2() throws NoSuchFieldException, IllegalAccessException {
        if (pool != null) {
            return pool.getResource();
        }
        pool = (Pool<Jedis>) getFieldValue(privateCacheClient.getClass(),privateCacheClient,"pool");
        return pool.getResource();
    }

    private Object getFieldValue(Class c,Object o,String name ) throws NoSuchFieldException, IllegalAccessException {
        Field configField = c.getDeclaredField(name);
        configField.setAccessible(true);
        return configField.get(o);
    }


//    public static void main(String[] args) {
//        System.out.println(FILE_PATH);
//    }

}
