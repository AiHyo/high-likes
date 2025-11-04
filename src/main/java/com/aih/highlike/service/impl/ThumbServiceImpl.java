package com.aih.highlike.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.aih.highlike.constant.ThumbConstant;
import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
import com.aih.highlike.manager.cache.CacheManager;
import com.aih.highlike.mapper.ThumbMapper;
import com.aih.highlike.model.entity.Thumb;
import com.aih.highlike.model.entity.User;
import com.aih.highlike.service.BlogService;
import com.aih.highlike.service.ThumbService;
import com.aih.highlike.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 点赞服务实现（同步版本）
 */
@Slf4j
//@Service("thumbServiceSync")
@Service("thumbServiceLocalCache")
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private CacheManager cacheManager;

    /**
     * 点赞
     * <p>
     * 执行流程：
     * 1. 参数校验和登录态检查
     * 2. 使用用户ID作为锁，防止同一用户并发点赞
     * 3. 在事务中执行：
     *    - 从 Redis 检查是否已点赞
     *    - 更新博客点赞数（MySQL）
     *    - 保存点赞记录（MySQL）
     *    - 写入点赞记录到 Redis
     * <p>
     * 注意：锁包裹事务，确保并发安全和数据一致性
     *
     * @param blogId  博客ID
     * @param request HTTP请求
     * @return 是否成功
     */
    @Override
    public boolean doThumb(Long blogId, HttpServletRequest request) {
        if (blogId == null || blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客ID无效");
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        Long userId = loginUser.getId();
        // 构建 Redis Key: thumb:user:{userId}
        // 用于存储该用户的所有点赞记录，使用 Hash 结构
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        // 使用用户ID作为锁，防止同一用户并发点赞
        // intern() 确保相同字符串使用同一个锁对象
        synchronized (String.valueOf(userId).intern()) {
            // 使用编程式事务，确保事务在锁内完整执行
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                // 从 Redis 检查是否已点赞
                Boolean exists = hasThumb(blogId, userId);
                if (Boolean.TRUE.equals(exists)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "已点赞，请勿重复操作");
                }

                // 增加博客点赞数（MySQL）
                boolean updateSuccess = blogService.incrementThumbCount(blogId);
                if (!updateSuccess) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "点赞失败");
                }

                // 保存点赞记录到数据库
                Thumb thumb = new Thumb();
                thumb.setUserId(userId);
                thumb.setBlogId(blogId);
                boolean saveSuccess = this.save(thumb);

                if (!saveSuccess) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "点赞失败");
                }

                // 点赞成功后，将记录存入 Redis
                // Redis Hash 操作：HSET thumb:user:{userId} {blogId} {thumbId}
                // 存储格式：Hash 的 field 是 blogId，value 是点赞记录ID
                redisTemplate.opsForHash().put(userThumbKey, blogId.toString(), thumb.getId());

                // 如果本地缓存存在该 Key，则更新
                cacheManager.putIfPresent(userThumbKey, blogId.toString(), thumb.getId());

                return true;
            }));
        }
    }

    /**
     * 取消点赞
     * <p>
     * 执行流程：
     * 1. 参数校验和登录态检查
     * 2. 使用用户ID作为锁，防止同一用户并发取消点赞
     * 3. 在事务中执行：
     *    - 从 Redis 获取点赞记录ID
     *    - 更新博客点赞数（MySQL）
     *    - 删除点赞记录（MySQL）
     *    - 删除 Redis 中的点赞记录
     * <p>
     * 注意：锁包裹事务，确保并发安全和数据一致性
     *
     * @param blogId  博客ID
     * @param request HTTP请求
     * @return 是否成功
     */
    @Override
    public boolean cancelThumb(Long blogId, HttpServletRequest request) {
        if (blogId == null || blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客ID无效");
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        Long userId = loginUser.getId();
        // 构建 Redis Key: thumb:user:{userId}
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        // 使用用户ID作为锁，防止同一用户并发取消点赞
        synchronized (String.valueOf(userId).intern()) {
            // 使用编程式事务，确保事务在锁内完整执行
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                // 从 Redis 获取点赞记录ID
                // Redis Hash 操作：HGET thumb:user:{userId} {blogId}
                // 获取该用户对该博客的点赞记录ID
//                Object thumbIdObj = redisTemplate.opsForHash().get(userThumbKey, blogId.toString());
                Object thumbIdObj = cacheManager.get(userThumbKey, blogId.toString());
                if (thumbIdObj == null || thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "未点赞，无法取消");
                }

                // 更新数据库
                Long thumbId = (Long) thumbIdObj;
                // 减少博客的点赞数
                boolean updateSuccess = blogService.decrementThumbCount(blogId);
                if (!updateSuccess) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败");
                }
                // 删除数据库中的点赞记录
                boolean removeSuccess = this.removeById(thumbId);
                if (!removeSuccess) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败");
                }

                // 更新 Redis
                // 删除该用户对该博客的点赞记录
                // Redis Hash 操作：HDEL thumb:user:{userId} {blogId}
                redisTemplate.opsForHash().delete(userThumbKey, blogId.toString());
                // 如果本地缓存存在该 Key，更新为未点赞标识
                cacheManager.putIfPresent(userThumbKey, blogId.toString(), ThumbConstant.UN_THUMB_CONSTANT);

                return true;
            }));
        }
    }


    /**
     * 判断用户是否已点赞
     *
     * @param blogId 博客ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        if (blogId == null || userId == null) {
            return false;
        }
        // 获取缓存中的点赞信息
        String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        Object thumbIdObj = cacheManager.get(hashKey, blogId.toString());
        if (thumbIdObj == null) {
            return false;
        }
        // 判断是否已点赞
        Long thumbId = (Long) thumbIdObj;
        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
    }

    /**
     * 批量获取用户的点赞记录（从 Redis 查询）
     * <p>
     * Redis 操作说明：
     * - 使用 HMGET 命令批量获取 Hash 中多个字段的值
     * - 命令格式：HMGET thumb:user:{userId} {blogId1} {blogId2} {blogId3} ...
     * - 返回值列表与传入的 blogIdList 顺序一一对应
     * - 如果某个 blogId 不存在，对应位置返回 null
     * <p>
     * 性能优势：
     * - 一次性获取多个博客的点赞状态，避免 N+1 查询问题
     * - 批量查询 100 条记录，耗时约 5ms（MySQL 需要 100ms+）
     * <p>
     * 使用示例：
     * <pre>
     * List<Object> blogIds = Arrays.asList("1", "2", "3");
     * List<Object> thumbIds = multiGetThumbs(userId, blogIds);
     * // thumbIds[0] 对应 blogId=1 的点赞记录ID（可能为null）
     * // thumbIds[1] 对应 blogId=2 的点赞记录ID（可能为null）
     * // thumbIds[2] 对应 blogId=3 的点赞记录ID（可能为null）
     * </pre>
     *
     * @param userId     用户ID
     * @param blogIdList 博客ID列表
     * @return 点赞记录ID列表，与 blogIdList 顺序对应，未点赞的位置为 null
     */
    @Override
    public List<Object> multiGetThumbs(Long userId, List<Object> blogIdList) {
        if (userId == null || CollUtil.isEmpty(blogIdList)) {
            return List.of();
        }
        // 构建 Redis Key: thumb:user:{userId}
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        // Redis Hash 操作：HMGET thumb:user:{userId} {blogId1} {blogId2} ...
        // 批量获取该用户对多个博客的点赞记录ID
        // 返回的列表与 blogIdList 顺序一一对应
        return redisTemplate.opsForHash().multiGet(userThumbKey, blogIdList);
    }

    /**
     * 将用户的点赞记录从数据库同步到 Redis
     * <p>
     * 使用场景：
     * - 用户登录时初始化缓存
     * - 缓存失效后重新加载
     * <p>
     * 同步流程：
     * 1. 从 MySQL 查询用户所有点赞记录
     * 2. 构建 Map（blogId -> thumbId）
     * 3. 使用 HMSET 批量写入 Redis
     * <p>
     * Redis 操作：
     * - 命令：HMSET thumb:user:{userId} {blogId1} {thumbId1} {blogId2} {thumbId2} ...
     * - 数据结构：Hash，一个用户的所有点赞记录存储在一个 Hash 中
     * - Key: thumb:user:{userId}
     * - Field: blogId（字符串）
     * - Value: thumbId（点赞记录ID）
     *
     * @param userId 用户ID
     */
    @Override
    public void syncUserThumbsToRedis(Long userId) {
        if (userId == null) {
            return;
        }

        // 构建 Redis Key: thumb:user:{userId}
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        // 从数据库查询用户所有点赞记录
        List<Thumb> thumbList = this.lambdaQuery()
                .eq(Thumb::getUserId, userId)
                .list();

        if (CollUtil.isEmpty(thumbList)) {
            log.info("用户 {} 暂无点赞记录", userId);
            return;
        }

        // 构建 Map，准备批量写入 Redis
        // Map 结构：{blogId: thumbId}
        java.util.Map<String, Object> thumbMap = new java.util.HashMap<>();
        for (Thumb thumb : thumbList) {
            thumbMap.put(thumb.getBlogId().toString(), thumb.getId());
        }

        // 批量写入 Redis
        // Redis Hash 操作：HMSET thumb:user:{userId} {blogId1} {thumbId1} {blogId2} {thumbId2} ...
        // 一次性写入所有点赞记录，性能优于逐条写入
        redisTemplate.opsForHash().putAll(userThumbKey, thumbMap);
        log.info("用户 {} 的 {} 条点赞记录已同步到 Redis", userId, thumbList.size());
    }

    /**
     * 清除用户的点赞缓存
     * <p>
     * 使用场景：
     * - 用户登出时清理缓存
     * - 缓存数据异常时重置
     * <p>
     * Redis 操作：
     * - 命令：DEL thumb:user:{userId}
     * - 删除整个 Hash 结构，释放内存
     *
     * @param userId 用户ID
     */
    @Override
    public void clearUserThumbCache(Long userId) {
        if (userId == null) {
            return;
        }

        // 构建 Redis Key: thumb:user:{userId}
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        // Redis 操作：DEL thumb:user:{userId}
        // 删除该用户的所有点赞缓存
        redisTemplate.delete(userThumbKey);
        log.info("用户 {} 的点赞缓存已清除", userId);
    }
}