package cn.iocoder.yudao.module.bpm.dal.dataobject.task;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 流程实例评论 DO
 *
 * @author 芋道源码
 */
@TableName(value = "bpm_process_instance_comment", autoResultMap = true)
@KeySequence("bpm_process_instance_comment_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmProcessInstanceCommentDO extends TenantBaseDO {

    /**
     * 评论编号
     */
    @TableId
    private Long id;
    
    /**
     * 流程实例编号
     */
    private String processInstanceId;
    /**
     * 流程实例名称
     */
    private String processName;
    
    /**
     * 评论用户编号
     */
    private Long userId;
    
    /**
     * 评论用户昵称
     */
    private String userNickname;
    
    /**
     * 评论用户头像
     */
    private String userAvatar;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 评论图片地址数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> picUrls;
    /**
     * 艾特用户数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> atUserIds;


} 