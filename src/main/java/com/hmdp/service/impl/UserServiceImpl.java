package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机格式是否正确
        if(RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机格式不正确，请重新发送!!!");
        //  1.1如果手机格式正确 则产生验证码
        String code = RandomUtil.randomNumbers(6);
        // 2.将验证码存入Redis并设置期限
        String key = RedisConstants.LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key,code);
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_CODE_TTL,TimeUnit.MINUTES);
        // 3.这里模拟将验证码进行发送
        log.debug("发送的验证码为：" + code);
        return Result.ok();
    }

    @Override
    public Result checkByPhoneAndCode(LoginFormDTO loginForm, HttpSession session) {
        // loginForm存储手机号 + 验证码
        String phone = loginForm.getPhone();
        String receive_code = loginForm.getCode();
        // 1.查看验证码是否正确
        String key = RedisConstants.LOGIN_CODE_KEY + phone;
        String own_code = stringRedisTemplate.opsForValue().get(key);
        //  1.1如果根据手机号没有查到对应的验证码(可能过期了/修改了对应的手机号)
        if (own_code == null) {
            return Result.fail("您修改了手机号 或者 验证码已失效...");
        }
        //  1.2查到了手机对应的验证码但匹配不上
        if(!receive_code.equals(own_code))
            return Result.fail("验证码输入错误...请重新输入...");
        // 2.查询手机号是否已经注册
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone",phone);
        User user = userMapper.selectOne(wrapper);
        UserDTO userDTO = new UserDTO();
        //  2.1查询到了对应手机号(已注册)
        if(user != null)
        {
            userDTO.setId(user.getId());
            userDTO.setIcon(user.getIcon());
            userDTO.setNickName(user.getNickName());
        }
        //  2.2未查询到对应手机号，需要注册
        else
        {
            User user1 = new User();
            // 需要设置主键自增
            user1.setPhone(phone);
            user1.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
            userMapper.insert(user1);
            userDTO.setId(user1.getId());
            userDTO.setNickName(user1.getNickName());
        }
        // 3.生成对应的随机token作为登录令牌
        String token = UUID.randomUUID().toString(true);

        // 4.将userDTO转为Map并存入Redis然后设置期限
        Map<String,String> userDTO_map = new HashMap<>();
        userDTO_map.put("id",userDTO.getId().toString());
        userDTO_map.put("nickName",userDTO.getNickName());
        userDTO_map.put("icon",userDTO.getIcon());
        String user_key = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(user_key,userDTO_map);
        stringRedisTemplate.expire(user_key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);


        // 返回数据后跳转到了index，在首页点击"我的"进入info，info启动时默认发送/user/me请求，会将user进行交接，所以需要在session中设置用以获取user
        // session.setAttribute("user",userDTO);
        // 直接在这里加入到session即可(只跟硬盘交互一次——与mysql，将查到的信息存入内存，以后都从内存获取信息)
        return Result.ok(token);
    }

    @Override
    public List<User> getByIds(List<Long> ids) {
        return userMapper.selectByIds(ids);
    }

    @Override
    public Result sign() {
        // TODO 实现用户签到功能
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 获取今天的时间
        LocalDateTime time = LocalDateTime.now();
        String format = time.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        int dayOfMonth = time.getDayOfMonth();
        String key = USER_SIGN_KEY + userId + format;
        // 3. 通过redis的bitMap签到
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
        // 4. 返回ok
        return Result.ok();
    }

    @Override
    public Result getSignCount() {
        // TODO 获取截止到今天为止连续签到天数(从今天开始数连续签到了多少天)
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 获取今天的时间
        LocalDateTime time = LocalDateTime.now();
        String format = time.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        int dayOfMonth = time.getDayOfMonth();
        String key = USER_SIGN_KEY + userId + format;
        // 3. 获取对应的bitMap对应的十进制值
        List<Long> nums = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (nums == null || nums.isEmpty())
            return Result.ok(0);
        Long num = nums.get(0);
        int count = 0;
        while (num != 0)
        {
            if ((num & 1) == 1)
                count += 1;
            else
                break;
            num >>>= 1;
        }
        return Result.ok(count);
    }
}
