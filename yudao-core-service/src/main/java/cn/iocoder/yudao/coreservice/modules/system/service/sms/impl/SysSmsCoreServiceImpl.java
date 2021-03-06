package cn.iocoder.yudao.coreservice.modules.system.service.sms.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.coreservice.modules.member.dal.dataobject.user.MbrUserDO;
import cn.iocoder.yudao.coreservice.modules.member.service.user.MbrUserCoreService;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.sms.SysSmsTemplateDO;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.coreservice.modules.system.mq.message.sms.SysSmsSendMessage;
import cn.iocoder.yudao.coreservice.modules.system.mq.producer.sms.SysSmsCoreProducer;
import cn.iocoder.yudao.coreservice.modules.system.service.sms.SysSmsCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.sms.SysSmsLogCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.sms.SysSmsTemplateCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.user.SysUserCoreService;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.sms.core.client.SmsClient;
import cn.iocoder.yudao.framework.sms.core.client.SmsClientFactory;
import cn.iocoder.yudao.framework.sms.core.client.SmsCommonResult;
import cn.iocoder.yudao.framework.sms.core.client.dto.SmsReceiveRespDTO;
import cn.iocoder.yudao.framework.sms.core.client.dto.SmsSendRespDTO;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.coreservice.modules.system.enums.SysErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 短信 Service Core 实现
 *
 * @author 芋道源码
 */
@Service
public class SysSmsCoreServiceImpl implements SysSmsCoreService {

    @Resource
    private SysUserCoreService sysUserCoreService;
    @Resource
    private MbrUserCoreService mbrUserCoreService;
    @Resource
    private SysSmsTemplateCoreService smsTemplateCoreService;
    @Resource
    private SysSmsLogCoreService smsLogCoreService;

    @Resource
    private SmsClientFactory smsClientFactory;

    @Resource
    private SysSmsCoreProducer smsCoreProducer;

    @Override
    public Long sendSingleSmsToAdmin(String mobile, Long userId, String templateCode, Map<String, Object> templateParams) {
        // 如果 mobile 为空，则加载用户编号对应的手机号
        if (StrUtil.isEmpty(mobile)) {
            SysUserDO user = sysUserCoreService.getUser(userId);
            if (user != null) {
                mobile = user.getMobile();
            }
        }
        // 执行发送
        return this.sendSingleSms(mobile, userId, UserTypeEnum.ADMIN.getValue(), templateCode, templateParams);
    }

    @Override
    public Long sendSingleSmsToMember(String mobile, Long userId, String templateCode, Map<String, Object> templateParams) {
        // 如果 mobile 为空，则加载用户编号对应的手机号
        if (StrUtil.isEmpty(mobile)) {
            MbrUserDO user = mbrUserCoreService.getUser(userId);
            if (user != null) {
                mobile = user.getMobile();
            }
        }
        // 执行发送
        return this.sendSingleSms(mobile, userId, UserTypeEnum.MEMBER.getValue(), templateCode, templateParams);
    }

    @Override
    public Long sendSingleSms(String mobile, Long userId, Integer userType,
                              String templateCode, Map<String, Object> templateParams) {
        // 校验短信模板是否合法
        SysSmsTemplateDO template = this.checkSmsTemplateValid(templateCode);
        // 校验手机号码是否存在
        mobile = this.checkMobile(mobile);
        // 构建有序的模板参数。为什么放在这个位置，是提前保证模板参数的正确性，而不是到了插入发送日志
        List<KeyValue<String, Object>> newTemplateParams = this.buildTemplateParams(template, templateParams);

        // 创建发送日志
        Boolean isSend = CommonStatusEnum.ENABLE.getStatus().equals(template.getStatus()); // 如果模板被禁用，则不发送短信，只记录日志
        String content = smsTemplateCoreService.formatSmsTemplateContent(template.getContent(), templateParams);
        Long sendLogId = smsLogCoreService.createSmsLog(mobile, userId, userType, isSend, template, content, templateParams);

        // 发送 MQ 消息，异步执行发送短信
        if (isSend) {
            smsCoreProducer.sendSmsSendMessage(sendLogId, mobile, template.getChannelId(),
                    template.getApiTemplateId(), newTemplateParams);
        }
        return sendLogId;
    }

    @Override
    public void sendBatchSms(List<String> mobiles, List<Long> userIds, Integer userType,
                             String templateCode, Map<String, Object> templateParams) {
        throw new UnsupportedOperationException("暂时不支持该操作，感兴趣可以实现该功能哟！");
    }

    @VisibleForTesting
    public SysSmsTemplateDO checkSmsTemplateValid(String templateCode) {
        // 获得短信模板。考虑到效率，从缓存中获取
        SysSmsTemplateDO template = smsTemplateCoreService.getSmsTemplateByCodeFromCache(templateCode);
        // 短信模板不存在
        if (template == null) {
            throw exception(SMS_SEND_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    /**
     * 将参数模板，处理成有序的 KeyValue 数组
     *
     * 原因是，部分短信平台并不是使用 key 作为参数，而是数组下标，例如说腾讯云 https://cloud.tencent.com/document/product/382/39023
     *
     * @param template 短信模板
     * @param templateParams 原始参数
     * @return 处理后的参数
     */
    @VisibleForTesting
    public List<KeyValue<String, Object>> buildTemplateParams(SysSmsTemplateDO template, Map<String, Object> templateParams) {
        return template.getParams().stream().map(key -> {
            Object value = templateParams.get(key);
            if (value == null) {
                throw exception(SMS_SEND_MOBILE_TEMPLATE_PARAM_MISS, key);
            }
            return new KeyValue<>(key, value);
        }).collect(Collectors.toList());
    }

    @VisibleForTesting
    public String checkMobile(String mobile) {
        if (StrUtil.isEmpty(mobile)) {
            throw exception(SMS_SEND_MOBILE_NOT_EXISTS);
        }
        return mobile;
    }


    @Override
    public void doSendSms(SysSmsSendMessage message) {
        // 获得渠道对应的 SmsClient 客户端
        SmsClient smsClient = smsClientFactory.getSmsClient(message.getChannelId());
        Assert.notNull(smsClient, String.format("短信客户端(%d) 不存在", message.getChannelId()));
        // 发送短信
        SmsCommonResult<SmsSendRespDTO> sendResult = smsClient.sendSms(message.getLogId(), message.getMobile(),
                message.getApiTemplateId(), message.getTemplateParams());
        smsLogCoreService.updateSmsSendResult(message.getLogId(), sendResult.getCode(), sendResult.getMsg(),
                sendResult.getApiCode(), sendResult.getApiMsg(), sendResult.getApiRequestId(),
                sendResult.getData() != null ? sendResult.getData().getSerialNo() : null);
    }

    @Override
    public void receiveSmsStatus(String channelCode, String text) throws Throwable {
        // 获得渠道对应的 SmsClient 客户端
        SmsClient smsClient = smsClientFactory.getSmsClient(channelCode);
        Assert.notNull(smsClient, String.format("短信客户端(%s) 不存在", channelCode));
        // 解析内容
        List<SmsReceiveRespDTO> receiveResults = smsClient.parseSmsReceiveStatus(text);
        if (CollUtil.isEmpty(receiveResults)) {
            return;
        }
        // 更新短信日志的接收结果. 因为量一般不大，所以先使用 for 循环更新
        receiveResults.forEach(result -> smsLogCoreService.updateSmsReceiveResult(result.getLogId(),
                result.getSuccess(), result.getReceiveTime(), result.getErrorCode(), result.getErrorCode()));
    }

}
