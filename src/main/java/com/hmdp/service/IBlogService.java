package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Integer id);

    Result queryHotBlog(Integer current);

    Result likeBlogById(Long id) throws InterruptedException;

    Result updateBlogLiked(Long id);

    Result getBlogLikes(Long blogId);

    Result queryBlogsWithAuthorId(Integer authorId,Integer current);

    Result saveBlog(Blog blog);

    Result queryAttentions(Long maxTime, Integer offset);
}
