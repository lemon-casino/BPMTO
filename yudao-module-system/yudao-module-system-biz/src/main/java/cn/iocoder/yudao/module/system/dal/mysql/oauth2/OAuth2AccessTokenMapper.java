package cn.iocoder.yudao.module.system.dal.mysql.oauth2;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OAuth2AccessTokenMapper extends BaseMapperX<OAuth2AccessTokenDO> {

    @TenantIgnore // 获取 token 的时候，需要忽略租户编号。原因是：一些场景下，可能不会传递 tenant-id 请求头，例如说文件上传、积木报表等等
    default OAuth2AccessTokenDO selectByAccessToken(String accessToken) {
        return selectOne(OAuth2AccessTokenDO::getAccessToken, accessToken);
    }

    default List<OAuth2AccessTokenDO> selectListByRefreshToken(String refreshToken) {
        return selectList(OAuth2AccessTokenDO::getRefreshToken, refreshToken);
    }

    default PageResult<OAuth2AccessTokenDO> selectPage(OAuth2AccessTokenPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<OAuth2AccessTokenDO>()
                .eqIfPresent(OAuth2AccessTokenDO::getUserId, reqVO.getUserId())
                .eqIfPresent(OAuth2AccessTokenDO::getUserType, reqVO.getUserType())
                .likeIfPresent(OAuth2AccessTokenDO::getClientId, reqVO.getClientId())
                .gt(OAuth2AccessTokenDO::getExpiresTime, LocalDateTime.now())
                .orderByDesc(OAuth2AccessTokenDO::getId));
    }

    /**
     * 查询指定用户的有效访问令牌列表
     * 
     * @param userId 用户编号
     * @param userType 用户类型
     * @param expiresTime 过期时间下限
     * @return 访问令牌列表
     */
    default List<OAuth2AccessTokenDO> selectListByUserIdAndUserTypeAndExpiresTimeGt(Long userId, Integer userType, LocalDateTime expiresTime) {
        return selectList(new LambdaQueryWrapperX<OAuth2AccessTokenDO>()
                .eq(OAuth2AccessTokenDO::getUserId, userId)
                .eq(OAuth2AccessTokenDO::getUserType, userType)
                .gt(OAuth2AccessTokenDO::getExpiresTime, expiresTime)
                .orderByDesc(OAuth2AccessTokenDO::getExpiresTime));
    }
    
    /**
     * 物理删除过期的访问令牌，忽略逻辑删除的标记
     * 
     * @param expireTime 过期时间
     * @return 删除的数量
     */
    @TenantIgnore
    @Delete("DELETE FROM system_oauth2_access_token WHERE expires_time < #{expireTime} OR deleted = 1")
    int physicallyDeleteByExpireTime(@Param("expireTime") LocalDateTime expireTime);

}
