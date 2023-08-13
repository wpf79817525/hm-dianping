package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    ShopTypeMapper shopTypeMapper;
    @Override
    public List<ShopType> getTypeList() {
        // 获取全部的商铺类型
        // 1. 尝试从Redis缓存中获取
        ObjectMapper objectMapper = new ObjectMapper();
        String key = "heimadp:cache:shopType";
        List<String> stringList = stringRedisTemplate.opsForList().range(key, 0, stringRedisTemplate.opsForList().size(key) - 1);
        if (stringList != null && !stringList.isEmpty()) {
            List<ShopType> shopTypes = stringList.stream().
                    map(str -> JSONUtil.toBean(str, ShopType.class)).
                    collect(Collectors.toList());
            return shopTypes;
        }

        // 2. 从数据库获取
        QueryWrapper<ShopType> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort");
        List<ShopType> shopTypeList = shopTypeMapper.selectList(wrapper);
        System.out.println(shopTypeList);
        if (shopTypeList == null) {
            return null;
        }

        // 3. 写入Redis缓存
        shopTypeList.forEach(shopType -> {
            String json_shopType = JSONUtil.toJsonStr(shopType);
            stringRedisTemplate.opsForList().rightPush(key,json_shopType);     // 必须从右边进才能保证顺序
        });

        // 设置缓存时间
        stringRedisTemplate.expire(key,5, TimeUnit.MINUTES);

        return shopTypeList;
    }
}
