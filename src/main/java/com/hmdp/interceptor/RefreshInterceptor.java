package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从请求头中拿到对应的token
        String token = request.getHeader("authorization");
        if (token == null) {
            return true;
        }

        // 2.根据token查询Redis得到对应的Map
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return true;
        }

        // 3.将对应的Map转为user对象
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.valueOf((String) entries.get("id")));
        userDTO.setNickName(entries.get("nickName").toString());
        userDTO.setIcon(entries.get("icon").toString());

        // 4.将user对象存入ThreadLocal
        UserHolder.saveUser(userDTO);

        // 5.刷新对应token的期限(为方便测试先注释掉)
//        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }
}
