package cn.iocoder.yudao.module.system.service.sms;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.sms.SmsChannelDO;
import cn.iocoder.yudao.module.system.dal.dataobject.sms.SmsTemplateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.framework.sms.core.client.SmsClient;
import cn.iocoder.yudao.module.system.framework.sms.core.client.dto.SmsReceiveRespDTO;
import cn.iocoder.yudao.module.system.mq.message.sms.SmsSendMessage;
import cn.iocoder.yudao.module.system.mq.producer.sms.SmsProducer;
import cn.iocoder.yudao.module.system.service.member.MemberService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

/**
 * 短信发送 Service 发送的实现
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class SmsSendServiceImpl implements SmsSendService {

    @Resource
    private AdminUserService adminUserService;
    @Resource
    private MemberService memberService;
    @Resource
    private SmsChannelService smsChannelService;
    @Resource
    private SmsTemplateService smsTemplateService;
    @Resource
    private SmsLogService smsLogService;

    @Resource
    private SmsProducer smsProducer;
    
    @Value("${yudao.system.sms.ding-talk.department-url}")
    private String dingTalkDepartmentUrl;
    
    @Value("${yudao.system.sms.ding-talk.todo-url}")
    private String dingTalkTodoUrl;

    @Value("${yudao.system.sms.ding-talk.whether-to-send}")
    private Boolean whetherToSend;

    @Override
    @DataPermission(enable = false) // 发送短信时，无需考虑数据权限
    public Long sendSingleSmsToAdmin(String mobile, Long userId, String templateCode, Map<String, Object> templateParams) {
        // 如果 mobile 为空，则加载用户编号对应的手机号
        if (StrUtil.isEmpty(mobile)) {
            AdminUserDO user = adminUserService.getUser(userId);
            if (user != null) {
                mobile = user.getMobile();
            }
        }
        // 执行发送
        return sendSingleSms(mobile, userId, UserTypeEnum.ADMIN.getValue(), templateCode, templateParams);
    }

    @Override
    public Long sendSingleSmsToMember(String mobile, Long userId, String templateCode, Map<String, Object> templateParams) {
        // 如果 mobile 为空，则加载用户编号对应的手机号
        if (StrUtil.isEmpty(mobile)) {
            mobile = memberService.getMemberUserMobile(userId);
        }
        // 执行发送
        return sendSingleSms(mobile, userId, UserTypeEnum.MEMBER.getValue(), templateCode, templateParams);
    }

    @Override
    public Long sendSingleSms(String mobile, Long userId, Integer userType,
                              String templateCode, Map<String, Object> templateParams) {
        // 校验短信模板是否合法
        SmsTemplateDO template = validateSmsTemplate(templateCode);
        // 校验短信渠道是否合法
        SmsChannelDO smsChannel = validateSmsChannel(template.getChannelId());

        // 校验手机号码是否存在
        mobile = validateMobile(mobile);
        // 构建有序的模板参数。为什么放在这个位置，是提前保证模板参数的正确性，而不是到了插入发送日志
        List<KeyValue<String, Object>> newTemplateParams = buildTemplateParams(template, templateParams);
//循环 newTemplateParams
//        log.info("短信模板参数: {}", newTemplateParams);
        newTemplateParams.forEach(keyValue -> log.info("短信模板参数: {} -> {}", keyValue.getKey(), keyValue.getValue()));
        // 创建发送日志。如果模板被禁用，则不发送短信，只记录日志
        Boolean isSend = CommonStatusEnum.ENABLE.getStatus().equals(template.getStatus())
                && CommonStatusEnum.ENABLE.getStatus().equals(smsChannel.getStatus());
        String content = smsTemplateService.formatSmsTemplateContent(template.getContent(), templateParams);
        Long sendLogId = smsLogService.createSmsLog(mobile, userId, userType, isSend, template, content, templateParams);

        // 发送 MQ 消息，异步执行发送短信
        if (isSend) {
            smsProducer.sendSmsSendMessage(sendLogId, mobile, template.getChannelId(),
                    template.getApiTemplateId(), newTemplateParams);
        }
        return sendLogId;
    }

    @VisibleForTesting
    SmsChannelDO validateSmsChannel(Long channelId) {
        // 获得短信模板。考虑到效率，从缓存中获取
        SmsChannelDO channelDO = smsChannelService.getSmsChannel(channelId);
        // 短信模板不存在
        if (channelDO == null) {
            throw exception(SMS_CHANNEL_NOT_EXISTS);
        }
        return channelDO;
    }

    @VisibleForTesting
    SmsTemplateDO validateSmsTemplate(String templateCode) {
        // 获得短信模板。考虑到效率，从缓存中获取
        SmsTemplateDO template = smsTemplateService.getSmsTemplateByCodeFromCache(templateCode);
        // 短信模板不存在
        if (template == null) {
            throw exception(SMS_SEND_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    /**
     * 将参数模板，处理成有序的 KeyValue 数组
     * <p>
     * 原因是，部分短信平台并不是使用 key 作为参数，而是数组下标，例如说 <a href="https://cloud.tencent.com/document/product/382/39023">腾讯云</a>
     *
     * @param template       短信模板
     * @param templateParams 原始参数
     * @return 处理后的参数
     */
    @VisibleForTesting
    List<KeyValue<String, Object>> buildTemplateParams(SmsTemplateDO template, Map<String, Object> templateParams) {
        return template.getParams().stream().map(key -> {
            Object value = templateParams.get(key);
            if (value == null) {
                throw exception(SMS_SEND_MOBILE_TEMPLATE_PARAM_MISS, key);
            }
            return new KeyValue<>(key, value);
        }).collect(Collectors.toList());
    }

    @VisibleForTesting
    public String validateMobile(String mobile) {
        if (StrUtil.isEmpty(mobile)) {
            throw exception(SMS_SEND_MOBILE_NOT_EXISTS);
        }
        return mobile;
    }

    @Override
    public void doSendSms(SmsSendMessage message) {
        if(whetherToSend){
            String url = dingTalkDepartmentUrl + "?mobile=" + message.getMobile();
            String response = HttpUtil.get(url);
            List<KeyValue<String, Object>> templateParams = message.getTemplateParams();
            if (response != null) {
                // 从templateParams提取参数
                String processInstanceName = "";
                String startUserNickname = "";
                String detailUrl = "";
                String reason = "";
                Boolean isMultiInstance = false;
                for (KeyValue<String, Object> param : templateParams) {
                    switch (param.getKey()) {
                        case "processInstanceName":
                            processInstanceName = (String) param.getValue();
                            break;
                        case "startUserNickname":
                            startUserNickname = (String) param.getValue();
                            break;
                        case "detailUrl":
                            detailUrl = (String) param.getValue();
                            break;
                        case "reason":
                            reason = (String) param.getValue();
                            break;
                        case "isMultiInstance":
                            isMultiInstance = (Boolean) param.getValue();
                            break;
                    }
                }
                // 判断是否包含"不通过"
                String messagex = "";
                if (!reason.contains("不通过")) {
                    // 不包含"不通过"，生成审批通知
                    messagex= String.format(
                            "您接收到了来自 %s 发起关于 %s 的审批",
                            startUserNickname,
                            processInstanceName
                    );
                } else {
                    // 包含"不通过"，执行其他操作（例如记录日志）
                    messagex ="未通过";
                }
                // 构建请求参数
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("unionId", response);
                paramMap.put("title", messagex);
                paramMap.put("content", detailUrl);
                paramMap.put("reason", reason);
                paramMap.put("mobile", message.getMobile());
                paramMap.put("processInstanceName", processInstanceName);
                paramMap.put("isMultiInstance", isMultiInstance);
                paramMap.put("fruits", Collections.singletonList(response));
                // 判断 reason 是不是  转办 以及 isMultiInstance 是不是true  如果是 将当前登录人的手机号码加入getLoginUserId()
                if (reason.contains("转办") && isMultiInstance) {
                    Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
                    String loginUserMobile = adminUserService.getUser(loginUserId).getMobile();
                    paramMap.put("enrolleeMobile",loginUserMobile );
                }
                // 发送POST请求
                String result = HttpUtil.post(dingTalkTodoUrl, paramMap);

                System.out.println("代办事项创建结果：" + result);
            }
        }


    }

    @Override
    public void receiveSmsStatus(String channelCode, String text) throws Throwable {
        // 获得渠道对应的 SmsClient 客户端
        SmsClient smsClient = smsChannelService.getSmsClient(channelCode);
        Assert.notNull(smsClient, "短信客户端({}) 不存在", channelCode);
        // 解析内容
        List<SmsReceiveRespDTO> receiveResults = smsClient.parseSmsReceiveStatus(text);
        if (CollUtil.isEmpty(receiveResults)) {
            return;
        }
        // 更新短信日志的接收结果. 因为量一般不大，所以先使用 for 循环更新
        receiveResults.forEach(result -> smsLogService.updateSmsReceiveResult(result.getLogId(),
                result.getSuccess(), result.getReceiveTime(), result.getErrorCode(), result.getErrorMsg()));
    }
}
