package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment.BpmProcessInstanceCommentCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskUrgeReqVO;
import cn.iocoder.yudao.module.bpm.convert.task.BpmProcessInstanceCommentConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCommentDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceCommentMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程实例评论 Service 实现类
 *
 * @author 芋道源码
 */
@Slf4j
@Service
@Validated
public class BpmProcessInstanceCommentServiceImpl implements BpmProcessInstanceCommentService {

    @Resource
    private BpmProcessInstanceCommentMapper commentMapper;

    @Resource
    private AdminUserApi adminUserApi;
    @Value("${yudao.system.sms.ding-talk.at-url}")
    private String dingTalkTodoUrl;

    @Value("${yudao.system.sms.ding-talk.urge}")
    private String urgeTalkTodoUrl;

    @Value("${yudao.system.sms.ding-talk.whether-to-send}")
    private Boolean whetherToSend;

    @Override
    public List<BpmProcessInstanceCommentDO> getCommentListByProcessInstanceId(String processInstanceId) {
        return commentMapper.selectListByProcessInstanceId(processInstanceId);
    }

    @Override
    public Long createComment(Long userId, BpmProcessInstanceCommentCreateReqVO createReqVO) {
        // 获取用户信息
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        
        // 创建评论
        BpmProcessInstanceCommentDO comment = BpmProcessInstanceCommentConvert.INSTANCE.convert(createReqVO);
        comment.setUserId(userId);
        comment.setUserNickname(user.getNickname());
        comment.setUserAvatar(user.getAvatar());
        commentMapper.insert(comment);
        List<AdminUserRespDTO> userList = adminUserApi.getUserList(createReqVO.getAtUserIds());
        // 判断是否有@ 用户 如果有 发送通知
           if(whetherToSend){
               userList.forEach(adminUserRespDTO -> {
                   // TODO 发送通知这个是@ 通知
                   String content = createReqVO.getContent();
                   Map<String, Object> paramMap = new HashMap<>();
                   paramMap.put("title", createReqVO.getProcessName());
                   paramMap.put("mobile", adminUserRespDTO.getMobile());
                   paramMap.put("processInstanceId", createReqVO.getProcessInstanceId());
                   // 发送POST请求
                   String result = HttpUtil.post(dingTalkTodoUrl, paramMap);
               });
        }

        return comment.getId();
    }

    @Override
    public void addUrgeTask(BpmTaskUrgeReqVO reqVO) {
        //
        reqVO.getUrgeList().forEach(urge -> {
           urge.setMobile( adminUserApi.getUser(urge.getUserId()).getMobile());
        });
        System.out.println("打印--->" + reqVO);
        // 发送催办信息
        if(whetherToSend){
            String jsonStr = JSONUtil.toJsonStr(reqVO); // 使用JSONUtil将对象转为JSON字符串
            String result = HttpUtil.post(urgeTalkTodoUrl, jsonStr);
        }


    }


} 