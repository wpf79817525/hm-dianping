package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.UserUserDTOSwitch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IUserService userService;
    @Override
    @Transactional
    public Result followOrCancel(Long followId, Boolean needFollow) {
        // 用于关注或取消关注
        // 获取当前用户的userId
        Long userId = UserHolder.getUser().getId();
        // 获取当前用户对应的key,对应的value是一个集合，存放followId
        String key = "follow:" + userId;
        // 2. 判断needFollow:true代表关注,false代表取关
        // 2.1 取关，删除表项
        if (needFollow == null || !needFollow)
        {
            // 删除数据库表项
            QueryWrapper<Follow> wrapper = new QueryWrapper<>();
            wrapper.eq("user_id",userId).eq("follow_user_id",followId);
            boolean success = remove(wrapper);
            // 删除当前用户对应的redis集合的元素
            if (success)
                stringRedisTemplate.opsForSet().remove(key,followId);
        }
        // 2.2 关注，添加表项
        else
        {
            // 添加数据库表项
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            boolean success = save(follow);
            // 增加当前用户对应的redis集合的元素
            if (success)
                stringRedisTemplate.opsForSet().add(key,followId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result checkIsFollow(Long followId) {
        // 用于查看是否关注userId 是否关注了 followId
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        return Result.ok(count != null && count > 0);
    }

    @Override
    public Result getCommonFollow(Long authorId) {
        // 获取author和当前user的共同关注
        // 1. 改进对应的关注取关代码，不仅需要在数据库进行操作，另外还需要在redis进行操作:记录一个用户关注了谁
        // 2. 获取user和author对应的集合，求交集
        String key1 = "follow:" + UserHolder.getUser().getId();
        String key2 = "follow:" + authorId;
        Set<String> common_str = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (common_str == null || common_str.isEmpty())
            return Result.ok(Collections.emptyList());
        // 3. 将交集进行转换，转换为ids
        List<Long> ids = common_str.stream().map(idStr -> Long.valueOf(idStr)).collect(Collectors.toList());
        // 4. 查询ids对应的users
        List<User> users = userService.listByIds(ids);
        // 5. 将users转换为userDTOs
        List<UserDTO> userDTOS = UserUserDTOSwitch.getUserDTOS(users);
        return Result.ok(userDTOS);
    }


}
