package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.UserUserDTOSwitch;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private IFollowService followService;

    @Autowired
    private BlogMapper blogMapper;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> setBlogUser(blog));
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Integer id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在...");
        }
        // 注意给blog的isLike属性进行标识
        setBlogUser(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlogById(Long id) throws InterruptedException {
        RLock lock = redissonClient.getLock("Blog:likeLock:");
        boolean success = lock.tryLock(1, TimeUnit.SECONDS);
        if (!success)
            return Result.fail("点赞出现异常...");

        try {
            IBlogService proxy = (IBlogService) AopContext.currentProxy();
            return proxy.updateBlogLiked(id);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result getBlogLikes(Long blogId) {
        // 根据博客id获取对应的点赞用户，只按时间先后顺序取前五名
        // 1. 获取redis的对应key和key对应的集合
        String key = "Blog:like:" + blogId;
        Set<String> ids_str = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (ids_str == null || ids_str.isEmpty())
            return Result.ok(Collections.emptyList());
        // 2. 将对应的String转化为Long
        List<Long> ids = ids_str.stream().map(id_str -> Long.valueOf(id_str)).collect(Collectors.toList());
        // 3. 根据ids查询到对应的User并转换为UserDTO
        List<User> users = userService.getByIds(ids);
        List<UserDTO> userDTOS = UserUserDTOSwitch.getUserDTOS(users);
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogsWithAuthorId(Integer authorId,Integer current) {
        // 根据user_id查询所有博客
        Page<Blog> page = query().eq("user_id", authorId).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        return Result.ok(blogs);
    }

    private void setBlogUser(Blog blog) {
        // 将blog信息进一步完善，查询笔记对应作者信息
        Long authorId = blog.getUserId();
        User author = userService.getById(authorId);
        blog.setIcon(author.getIcon());
        blog.setName(author.getNickName());

        // 在一开始进入时会有bug，因为没有登录拿不到userId，因此如果user为null则直接返回
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        // 判断user是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score("Blog:like:" + blog.getId(), userId.toString());
        blog.setIsLike(score != null);
    }

    @Transactional
    public Result updateBlogLiked(Long id) {
        // 点赞功能
        // 1.根据博客id和用户id判断该用户是否点过赞 —— 查询redis的set是否包含该用户(数据库当然可以，但使用数据库操作太重，没必要)
        String key = "Blog:like:" + id;
        String userId = UserHolder.getUser().getId().toString();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        // 1.1 如果用户未点过赞，点赞 liked + 1 —— 向redis添加用户 + 数据库加一操作
        if (score == null)
        {
            // 修改点赞数量
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            // 添加用户
            if (success)
                stringRedisTemplate.opsForZSet().add(key,userId, System.currentTimeMillis());

        }
        // 1.2 如果用户已点赞，则取消点赞 liked - 1 —— 将redis的用户删掉 + 数据库减一操作
        else
        {
            // 修改点赞数量
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            // 删除用户
            if (success)
                stringRedisTemplate.opsForZSet().remove(key,userId);
        }
        return Result.ok();
    }

    @Override
    public Result saveBlog(Blog blog) {
        // TODO 不仅需要保存笔记blog，还需要向粉丝的收件箱推送blog
        // 1. 保存笔记blg
        // 1.1 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 1.2 保存探店博文
        boolean success = save(blog);
        if (!success)
            return Result.fail("保存笔记失败...");
        // 2. 获取当前用户的粉丝
        // 2.1 查询tb_follow表获取follows
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 2.2 将follows进行转换(提取粉丝)
        // 获取粉丝ids(其实从follows获取就行)
        List<Long> ids = follows.stream().map(follow -> follow.getUserId()).collect(Collectors.toList());
        // 3. 向每个粉丝的收件箱发送blogId
        for (Long fanId : ids) {
            // 收件箱
            String key = "feed:" + fanId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }

        // 4. 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryAttentions(Long maxTime, Integer offset) {
        // TODO 作为粉丝，从收件箱拿blog
        // 1 获取用户ID定义key
        String key = "feed:" + UserHolder.getUser().getId();
        // 2 查询到对应的value-score
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, maxTime, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty())
            return Result.ok();
        // 3 获取到所有的blogId
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        // 记录最后一个时间戳
        long lastTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Long blogId = Long.valueOf(typedTuple.getValue());
            blogIds.add(blogId);
            long time = typedTuple.getScore().longValue();
            if (lastTime == time)
                os += 1;
            else
            {
                os = 1;
                lastTime = time;
            }
        }
        // 4. TODO 根据blogIds查询Blog(按照自定义顺序)
        List<Blog> blogs = blogMapper.queryBlogsByIdsWithTimeOrder(blogIds);

        // 5. 给blogs的每个blog赋予user属性
        blogs.forEach(blog -> setBlogUser(blog));

        // 6. 返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(lastTime);
        System.out.println(offset + " " + scrollResult);
        return Result.ok(scrollResult);
    }
}
