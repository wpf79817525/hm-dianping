package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    IFollowService followService;
    @PutMapping("/{followId}/{needFollow}")
    public Result followOrCancel(@PathVariable("followId") Long followId,@PathVariable("needFollow") Boolean needFollow) {
        return followService.followOrCancel(followId,needFollow);
    }

    @GetMapping("/or/not/{followId}")
    public Result isFollow(@PathVariable("followId") Long followId) {
        return followService.checkIsFollow(followId);
    }

    @GetMapping("/common/{id}")
    public Result getCommonFollow(@PathVariable("id") Long authorId) {
        return followService.getCommonFollow(authorId);
    }

}
