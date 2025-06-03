package cn.iocoder.yudao.module.system.dal.mysql.oauth2;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OAuth2RefreshTokenMapper extends BaseMapperX<OAuth2RefreshTokenDO> {

    default int deleteByRefreshToken(String refreshToken) {
        return delete(new LambdaQueryWrapperX<OAuth2RefreshTokenDO>()
                .eq(OAuth2RefreshTokenDO::getRefreshToken, refreshToken));
    }

    @TenantIgnore // 获取 token 的时候，需要忽略租户编号。原因是：一些场景下，可能不会传递 tenant-id 请求头，例如说文件上传、积木报表等等
    default OAuth2RefreshTokenDO selectByRefreshToken(String refreshToken) {
        return selectOne(OAuth2RefreshTokenDO::getRefreshToken, refreshToken);
    }

    /**
     * 查询指定用户的有效刷新令牌列表
     * 
     * @param userId 用户编号
     * @param userType 用户类型
     * @param expiresTime 过期时间下限
     * @return 刷新令牌列表
     */
    default List<OAuth2RefreshTokenDO> selectListByUserIdAndUserTypeAndExpiresTimeGt(Long userId, Integer userType, LocalDateTime expiresTime) {
        return selectList(new LambdaQueryWrapperX<OAuth2RefreshTokenDO>()
                .eq(OAuth2RefreshTokenDO::getUserId, userId)
                .eq(OAuth2RefreshTokenDO::getUserType, userType)
                .gt(OAuth2RefreshTokenDO::getExpiresTime, expiresTime)
                .orderByDesc(OAuth2RefreshTokenDO::getExpiresTime));
    }
    
    /**
     * 物理删除过期的刷新令牌，忽略逻辑删除的标记
     * 
     * @param expireTime 过期时间
     * @return 删除的数量
     */
    @TenantIgnore
    @Delete("DELETE FROM system_oauth2_refresh_token WHERE expires_time < #{expireTime} OR deleted = 1")
    int physicallyDeleteByExpireTime(@Param("expireTime") LocalDateTime expireTime);

}
