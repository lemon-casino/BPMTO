package cn.iocoder.yudao.module.system.service.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * OAuth2.0 Token Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class OAuth2TokenServiceImpl implements OAuth2TokenService {

    @Resource
    private OAuth2AccessTokenMapper oauth2AccessTokenMapper;
    @Resource
    private OAuth2RefreshTokenMapper oauth2RefreshTokenMapper;

    @Resource
    private OAuth2AccessTokenRedisDAO oauth2AccessTokenRedisDAO;

    @Resource
    private OAuth2ClientService oauth2ClientService;
    @Resource
    @Lazy // 懒加载，避免循环依赖
    private AdminUserService adminUserService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuth2AccessTokenDO createAccessToken(Long userId, Integer userType, String clientId, List<String> scopes) {
        OAuth2ClientDO clientDO = oauth2ClientService.validOAuthClientFromCache(clientId);
        
        // 检查是否有未过期的访问令牌
        OAuth2AccessTokenDO accessTokenDO = getAccessTokenByUserIdAndUserType(userId, userType);
        if (accessTokenDO != null) {
            // 找到有效令牌，更新其过期时间而不是直接返回
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime newExpiresTime = now.plusSeconds(clientDO.getAccessTokenValiditySeconds());
            log.info("[createAccessToken] 找到有效令牌, 令牌ID:{}, 更新过期时间: {}", accessTokenDO.getId(), newExpiresTime);
            
            // 更新访问令牌过期时间
            accessTokenDO.setExpiresTime(newExpiresTime);
            oauth2AccessTokenMapper.updateById(accessTokenDO);
            
            // 更新刷新令牌过期时间
            String refreshToken = accessTokenDO.getRefreshToken();
            if (StrUtil.isNotEmpty(refreshToken)) {
                OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(refreshToken);
                if (refreshTokenDO != null) {
                    refreshTokenDO.setExpiresTime(now.plusSeconds(clientDO.getRefreshTokenValiditySeconds()));
                    oauth2RefreshTokenMapper.updateById(refreshTokenDO);
                }
            }
            
            // 更新Redis缓存
            oauth2AccessTokenRedisDAO.set(accessTokenDO);
            
            return accessTokenDO;
        }
        
        // 检查是否有未过期的刷新令牌
        LocalDateTime now = LocalDateTime.now();
        List<OAuth2RefreshTokenDO> refreshTokens = oauth2RefreshTokenMapper.selectListByUserIdAndUserTypeAndExpiresTimeGt(
                userId, userType, now);
        
        // 如果有未过期的刷新令牌，则基于最新的刷新令牌创建访问令牌
        if (CollUtil.isNotEmpty(refreshTokens)) {
            OAuth2RefreshTokenDO refreshToken = refreshTokens.get(0); // 取最新的刷新令牌
            // 更新刷新令牌的过期时间
            refreshToken.setExpiresTime(now.plusSeconds(clientDO.getRefreshTokenValiditySeconds()));
            oauth2RefreshTokenMapper.updateById(refreshToken);
            log.info("[createAccessToken] 找到有效刷新令牌, 令牌ID:{}, 更新过期时间: {}", refreshToken.getId(), refreshToken.getExpiresTime());
            
            return createOAuth2AccessToken(refreshToken, clientDO);
        }
        
        // 如果没有刷新令牌，则新建刷新令牌和访问令牌
        log.info("[createAccessToken] 未找到有效令牌, 为用户 {} 创建新令牌", userId);
        OAuth2RefreshTokenDO refreshTokenDO = createOAuth2RefreshToken(userId, userType, clientDO, scopes);
        return createOAuth2AccessToken(refreshTokenDO, clientDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuth2AccessTokenDO refreshAccessToken(String refreshToken, String clientId) {
        // 查询刷新令牌
        OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(refreshToken);
        if (refreshTokenDO == null) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "无效的刷新令牌");
        }

        // 校验 Client 匹配
        OAuth2ClientDO clientDO = oauth2ClientService.validOAuthClientFromCache(clientId);
        if (ObjectUtil.notEqual(clientId, refreshTokenDO.getClientId())) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "刷新令牌的客户端编号不正确");
        }
        
        // 清理过期的令牌
        Long userId = refreshTokenDO.getUserId();
        Integer userType = refreshTokenDO.getUserType();
        LocalDateTime now = LocalDateTime.now();
        cleanExpiredAccessTokens(userId, userType, now);
        cleanExpiredRefreshTokens(userId, userType, now);

        // 移除相关的访问令牌
        List<OAuth2AccessTokenDO> accessTokenDOs = oauth2AccessTokenMapper.selectListByRefreshToken(refreshToken);
        if (CollUtil.isNotEmpty(accessTokenDOs)) {
            oauth2AccessTokenMapper.deleteByIds(convertSet(accessTokenDOs, OAuth2AccessTokenDO::getId));
            oauth2AccessTokenRedisDAO.deleteList(convertSet(accessTokenDOs, OAuth2AccessTokenDO::getAccessToken));
        }

        // 已过期的情况下，删除刷新令牌
        if (DateUtils.isExpired(refreshTokenDO.getExpiresTime())) {
            oauth2RefreshTokenMapper.deleteById(refreshTokenDO.getId());
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "刷新令牌已过期");
        }

        // 创建访问令牌
        return createOAuth2AccessToken(refreshTokenDO, clientDO);
    }
    
    /**
     * 清理指定用户过期的访问令牌
     * 
     * @param userId 用户编号
     * @param userType 用户类型
     * @param expireTime 过期时间
     */
    private void cleanExpiredAccessTokens(Long userId, Integer userType, LocalDateTime expireTime) {
        List<OAuth2AccessTokenDO> accessTokens = oauth2AccessTokenMapper.selectList(
                new LambdaQueryWrapperX<OAuth2AccessTokenDO>()
                        .eq(OAuth2AccessTokenDO::getUserId, userId)
                        .eq(OAuth2AccessTokenDO::getUserType, userType)
                        .lt(OAuth2AccessTokenDO::getExpiresTime, expireTime));
        
        if (CollUtil.isEmpty(accessTokens)) {
            return;
        }
        
        // 删除访问令牌
        oauth2AccessTokenMapper.deleteByIds(convertSet(accessTokens, OAuth2AccessTokenDO::getId));
        oauth2AccessTokenRedisDAO.deleteList(convertSet(accessTokens, OAuth2AccessTokenDO::getAccessToken));
    }
    
    /**
     * 清理指定用户过期的刷新令牌
     * 
     * @param userId 用户编号
     * @param userType 用户类型
     * @param expireTime 过期时间
     */
    private void cleanExpiredRefreshTokens(Long userId, Integer userType, LocalDateTime expireTime) {
        List<OAuth2RefreshTokenDO> refreshTokens = oauth2RefreshTokenMapper.selectList(
                new LambdaQueryWrapperX<OAuth2RefreshTokenDO>()
                        .eq(OAuth2RefreshTokenDO::getUserId, userId)
                        .eq(OAuth2RefreshTokenDO::getUserType, userType)
                        .lt(OAuth2RefreshTokenDO::getExpiresTime, expireTime));
        
        if (CollUtil.isEmpty(refreshTokens)) {
            return;
        }
        
        // 删除刷新令牌
        oauth2RefreshTokenMapper.deleteByIds(convertSet(refreshTokens, OAuth2RefreshTokenDO::getId));
    }
    
    /**
     * 定时清理所有过期的令牌，包括那些标记为已删除的记录
     * 每天凌晨11点执行一次
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void physicallyCleanTokens() {
        log.info("[physicallyCleanTokens] 开始物理清理过期令牌");
        
        // 使用 TenantUtils.executeIgnore 包装整个清理过程，忽略租户上下文检查
        TenantUtils.executeIgnore(() -> {
            LocalDateTime now = LocalDateTime.now();
            
            // 1. 清理过期的访问令牌（包括已删除的）
            // 注意：物理删除时会忽略逻辑删除字段的影响，将记录从数据库中实际删除
            int accessTokenCount = physicallyCleanAccessTokens(now);
            
            // 2. 清理过期的刷新令牌（包括已删除的）
            // 注意：物理删除时会忽略逻辑删除字段的影响，将记录从数据库中实际删除
            int refreshTokenCount = physicallyCleanRefreshTokens(now);
            
            // 3. 清理Redis中可能存在的额外过期令牌缓存
            // 这一步确保即使数据库中没有对应记录的令牌缓存也能被清理
            int redisTokenCount = oauth2AccessTokenRedisDAO.cleanExpiredTokens();
            
            log.info("[physicallyCleanTokens] 清理完成，共清理访问令牌 {} 个，刷新令牌 {} 个，Redis缓存令牌 {} 个", 
                    accessTokenCount, refreshTokenCount, redisTokenCount);
        });
    }
    
    @Override
    public int cleanToken() {
        log.info("[cleanToken] 开始手动清理过期令牌");
        
        // 使用 TenantUtils.executeIgnore 包装整个清理过程，忽略租户上下文检查
        return TenantUtils.executeIgnore(() -> {
            LocalDateTime now = LocalDateTime.now();
            
            // 1. 清理过期的访问令牌（包括已删除的）
            int accessTokenCount = physicallyCleanAccessTokens(now);
            
            // 2. 清理过期的刷新令牌（包括已删除的）
            int refreshTokenCount = physicallyCleanRefreshTokens(now);
            
            // 3. 清理Redis中可能存在的额外过期令牌缓存
            // 注意：Redis缓存的清理放在数据库记录清理之后，确保所有需要清理的记录都已处理
            int redisTokenCount = oauth2AccessTokenRedisDAO.cleanExpiredTokens();
            
            log.info("[cleanToken] 清理完成，共清理访问令牌 {} 个，刷新令牌 {} 个，Redis缓存令牌 {} 个", 
                    accessTokenCount, refreshTokenCount, redisTokenCount);
            
            // 返回所有清理的记录数，包括数据库中删除的记录和Redis中清理的缓存
            return accessTokenCount + refreshTokenCount + redisTokenCount;
        });
    }
    
    /**
     * 物理清理过期的访问令牌，包括被标记为已删除的记录
     * 
     * @param expireTime 过期时间
     * @return 清理的记录数
     */
    private int physicallyCleanAccessTokens(LocalDateTime expireTime) {
        log.info("[physicallyCleanAccessTokens] 开始物理删除过期的访问令牌，时间条件: {}", expireTime);
        
        // 执行物理删除，清理所有过期的或已标记为删除的访问令牌
        int count = oauth2AccessTokenMapper.physicallyDeleteByExpireTime(expireTime);
        log.info("[physicallyCleanAccessTokens] 完成物理删除，共删除 {} 条访问令牌记录", count);
        
        // 注意：上面只清理了数据库中的记录，还需要清理Redis中的缓存
        // 这里不再依赖于数据库查询的结果，而是依靠 OAuth2AccessTokenRedisDAO 中的 cleanExpiredTokens 方法
        // 该方法会扫描所有的Redis键并清理已过期的令牌
        
        return count;
    }
    
    /**
     * 物理清理过期的刷新令牌，包括被标记为已删除的记录
     * 
     * @param expireTime 过期时间
     * @return 清理的记录数
     */
    private int physicallyCleanRefreshTokens(LocalDateTime expireTime) {
        // 使用自定义的方法执行物理删除，确保绕过逻辑删除
        log.info("[physicallyCleanRefreshTokens] 开始物理删除过期的刷新令牌，时间条件: {}", expireTime);
        int count = oauth2RefreshTokenMapper.physicallyDeleteByExpireTime(expireTime);
        log.info("[physicallyCleanRefreshTokens] 完成物理删除，共删除 {} 条刷新令牌记录", count);
        return count;
    }

    @Override
    public OAuth2AccessTokenDO getAccessToken(String accessToken) {
        // 优先从 Redis 中获取
        OAuth2AccessTokenDO accessTokenDO = oauth2AccessTokenRedisDAO.get(accessToken);
        if (accessTokenDO != null) {
            return accessTokenDO;
        }

        // 获取不到，从 MySQL 中获取访问令牌
        accessTokenDO = oauth2AccessTokenMapper.selectByAccessToken(accessToken);
        if (accessTokenDO == null) {
            // 特殊：从 MySQL 中获取刷新令牌。原因：解决部分场景不方便刷新访问令牌场景
            // 例如说，积木报表只允许传递 token，不允许传递 refresh_token，导致无法刷新访问令牌
            // 再例如说，前端 WebSocket 的 token 直接跟在 url 上，无法传递 refresh_token
            OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(accessToken);
            if (refreshTokenDO != null && !DateUtils.isExpired(refreshTokenDO.getExpiresTime())) {
                accessTokenDO = convertToAccessToken(refreshTokenDO);
            }
        }

        // 如果在 MySQL 存在，则往 Redis 中写入
        if (accessTokenDO != null && !DateUtils.isExpired(accessTokenDO.getExpiresTime())) {
            oauth2AccessTokenRedisDAO.set(accessTokenDO);
        }
        return accessTokenDO;
    }

    @Override
    public OAuth2AccessTokenDO checkAccessToken(String accessToken) {
        OAuth2AccessTokenDO accessTokenDO = getAccessToken(accessToken);
        if (accessTokenDO == null) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "访问令牌不存在");
        }
        if (DateUtils.isExpired(accessTokenDO.getExpiresTime())) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "访问令牌已过期");
        }
        return accessTokenDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuth2AccessTokenDO removeAccessToken(String accessToken) {
        // 删除访问令牌
        OAuth2AccessTokenDO accessTokenDO = oauth2AccessTokenMapper.selectByAccessToken(accessToken);
        if (accessTokenDO == null) {
            return null;
        }
        oauth2AccessTokenMapper.deleteById(accessTokenDO.getId());
        oauth2AccessTokenRedisDAO.delete(accessToken);
        // 删除刷新令牌
        oauth2RefreshTokenMapper.deleteByRefreshToken(accessTokenDO.getRefreshToken());
        return accessTokenDO;
    }

    @Override
    public PageResult<OAuth2AccessTokenDO> getAccessTokenPage(OAuth2AccessTokenPageReqVO reqVO) {
        return oauth2AccessTokenMapper.selectPage(reqVO);
    }
    
    @Override
    public OAuth2AccessTokenDO getAccessTokenByUserIdAndUserType(Long userId, Integer userType) {
        List<OAuth2AccessTokenDO> accessTokens = oauth2AccessTokenMapper.selectListByUserIdAndUserTypeAndExpiresTimeGt(
                userId, userType, LocalDateTime.now());
        if (CollUtil.isEmpty(accessTokens)) {
            return null;
        }
        // 返回有效期最长的那个
        return accessTokens.get(0);
    }

    private OAuth2AccessTokenDO createOAuth2AccessToken(OAuth2RefreshTokenDO refreshTokenDO, OAuth2ClientDO clientDO) {
        OAuth2AccessTokenDO accessTokenDO = new OAuth2AccessTokenDO().setAccessToken(generateAccessToken())
                .setUserId(refreshTokenDO.getUserId()).setUserType(refreshTokenDO.getUserType())
                .setUserInfo(buildUserInfo(refreshTokenDO.getUserId(), refreshTokenDO.getUserType()))
                .setClientId(clientDO.getClientId()).setScopes(refreshTokenDO.getScopes())
                .setRefreshToken(refreshTokenDO.getRefreshToken())
                .setExpiresTime(LocalDateTime.now().plusSeconds(clientDO.getAccessTokenValiditySeconds()));
        accessTokenDO.setTenantId(TenantContextHolder.getTenantId()); // 手动设置租户编号，避免缓存到 Redis 的时候，无对应的租户编号
        oauth2AccessTokenMapper.insert(accessTokenDO);
        // 记录到 Redis 中
        oauth2AccessTokenRedisDAO.set(accessTokenDO);
        return accessTokenDO;
    }

    private OAuth2RefreshTokenDO createOAuth2RefreshToken(Long userId, Integer userType, OAuth2ClientDO clientDO, List<String> scopes) {
        OAuth2RefreshTokenDO refreshToken = new OAuth2RefreshTokenDO().setRefreshToken(generateRefreshToken())
                .setUserId(userId).setUserType(userType)
                .setClientId(clientDO.getClientId()).setScopes(scopes)
                .setExpiresTime(LocalDateTime.now().plusSeconds(clientDO.getRefreshTokenValiditySeconds()));
        oauth2RefreshTokenMapper.insert(refreshToken);
        return refreshToken;
    }

    private OAuth2AccessTokenDO convertToAccessToken(OAuth2RefreshTokenDO refreshTokenDO) {
        OAuth2AccessTokenDO accessTokenDO = BeanUtils.toBean(refreshTokenDO, OAuth2AccessTokenDO.class)
                .setAccessToken(refreshTokenDO.getRefreshToken());
        TenantUtils.execute(refreshTokenDO.getTenantId(),
                        () -> accessTokenDO.setUserInfo(buildUserInfo(refreshTokenDO.getUserId(), refreshTokenDO.getUserType())));
        return accessTokenDO;
    }

    /**
     * 加载用户信息，方便 {@link cn.iocoder.yudao.framework.security.core.LoginUser} 获取到昵称、部门等信息
     *
     * @param userId 用户编号
     * @param userType 用户类型
     * @return 用户信息
     */
    private Map<String, String> buildUserInfo(Long userId, Integer userType) {
        if (userType.equals(UserTypeEnum.ADMIN.getValue())) {
            AdminUserDO user = adminUserService.getUser(userId);
            return MapUtil.builder(LoginUser.INFO_KEY_NICKNAME, user.getNickname())
                    .put(LoginUser.INFO_KEY_DEPT_ID, StrUtil.toStringOrNull(user.getDeptId())).build();
        } else if (userType.equals(UserTypeEnum.MEMBER.getValue())) {
            // 注意：目前 Member 暂时不读取，可以按需实现
            return Collections.emptyMap();
        }
        return null;
    }

    private static String generateAccessToken() {
        return IdUtil.fastSimpleUUID();
    }

    private static String generateRefreshToken() {
        return IdUtil.fastSimpleUUID();
    }

}
