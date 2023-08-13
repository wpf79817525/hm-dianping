package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "heimadp:login:code:";
    public static final Long LOGIN_CODE_TTL = 1L;
    public static final String LOGIN_USER_KEY = "heimadp:login:token:";
    public static final Long LOGIN_USER_TTL = 5L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "heimadp:cache:shop:";

    public static final String LOCK_SHOP_KEY = "heimadp:lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "heimadp:seckill:stock:";
    public static final String BLOG_LIKED_KEY = "heimadp:blog:liked:";
    public static final String FEED_KEY = "heimadp:feed:";
    public static final String SHOP_GEO_KEY = "shop:GEO:";
    public static final String USER_SIGN_KEY = "user:sign:";
}
