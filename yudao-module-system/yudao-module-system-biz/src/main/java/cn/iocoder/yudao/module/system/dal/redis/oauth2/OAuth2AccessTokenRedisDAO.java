package cn.iocoder.yudao.module.system.dal.redis.oauth2;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants.OAUTH2_ACCESS_TOKEN;

/**
 * {@link OAuth2AccessTokenDO} 的 RedisDAO
 *
 * @author 芋道源码
 */
@Repository
public class OAuth2AccessTokenRedisDAO {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public OAuth2AccessTokenDO get(String accessToken) {
        String redisKey = formatKey(accessToken);
        return JsonUtils.parseObject(stringRedisTemplate.opsForValue().get(redisKey), OAuth2AccessTokenDO.class);
    }

    public void set(OAuth2AccessTokenDO accessTokenDO) {
        String redisKey = formatKey(accessTokenDO.getAccessToken());
        // 清理多余字段，避免缓存
        accessTokenDO.setUpdater(null).setUpdateTime(null).setCreateTime(null).setCreator(null).setDeleted(null);
        long time = LocalDateTimeUtil.between(LocalDateTime.now(), accessTokenDO.getExpiresTime(), ChronoUnit.SECONDS);
        if (time > 0) {
            stringRedisTemplate.opsForValue().set(redisKey, JsonUtils.toJsonString(accessTokenDO), time, TimeUnit.SECONDS);
        }
    }

    public void delete(String accessToken) {
        String redisKey = formatKey(accessToken);
        stringRedisTemplate.delete(redisKey);
    }

    public void deleteList(Collection<String> accessTokens) {
        List<String> redisKeys = CollectionUtils.convertList(accessTokens, OAuth2AccessTokenRedisDAO::formatKey);
        stringRedisTemplate.delete(redisKeys);
    }
    
    /**
     * 清理所有过期的令牌缓存
     * 
     * 这个方法使用模式匹配查找所有的OAuth2访问令牌缓存键，然后逐个检查和清理已过期的缓存
     * 
     * @return 清理的令牌数量
     */
    public int cleanExpiredTokens() {
        // 查找所有符合模式的键
        String keyPattern = String.format(OAUTH2_ACCESS_TOKEN, "*");
        Set<String> keys = stringRedisTemplate.keys(keyPattern);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (String key : keys) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                continue;
            }
            
            // 解析缓存的令牌对象
            OAuth2AccessTokenDO token = JsonUtils.parseObject(json, OAuth2AccessTokenDO.class);
            if (token == null) {
                // 解析失败，说明可能是格式已变更的旧数据，直接删除
                stringRedisTemplate.delete(key);
                count++;
                continue;
            }
            
            // 检查令牌是否已过期
            if (token.getExpiresTime() == null || token.getExpiresTime().isBefore(LocalDateTime.now())) {
                stringRedisTemplate.delete(key);
                count++;
            }
        }
        
        return count;
    }

    private static String formatKey(String accessToken) {
        return String.format(OAUTH2_ACCESS_TOKEN, accessToken);
    }

}
