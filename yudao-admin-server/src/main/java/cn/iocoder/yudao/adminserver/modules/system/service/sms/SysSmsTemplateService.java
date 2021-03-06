package cn.iocoder.yudao.adminserver.modules.system.service.sms;

import cn.iocoder.yudao.adminserver.modules.system.controller.sms.vo.template.SysSmsTemplateCreateReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.sms.vo.template.SysSmsTemplateExportReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.sms.vo.template.SysSmsTemplatePageReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.sms.vo.template.SysSmsTemplateUpdateReqVO;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.sms.SysSmsTemplateDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

import javax.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * 短信模板 Service 接口
 *
 * @author zzf
 * @date 2021/1/25 9:24
 */
public interface SysSmsTemplateService {

    /**
     * 获得短信模板
     *
     * @param code 模板编码
     * @return 短信模板
     */
    SysSmsTemplateDO getSmsTemplateByCode(String code);

    /**
     * 创建短信模板
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSmsTemplate(@Valid SysSmsTemplateCreateReqVO createReqVO);

    /**
     * 更新短信模板
     *
     * @param updateReqVO 更新信息
     */
    void updateSmsTemplate(@Valid SysSmsTemplateUpdateReqVO updateReqVO);

    /**
     * 删除短信模板
     *
     * @param id 编号
     */
    void deleteSmsTemplate(Long id);

    /**
     * 获得短信模板
     *
     * @param id 编号
     * @return 短信模板
     */
    SysSmsTemplateDO getSmsTemplate(Long id);

    /**
     * 获得短信模板列表
     *
     * @param ids 编号
     * @return 短信模板列表
     */
    List<SysSmsTemplateDO> getSmsTemplateList(Collection<Long> ids);

    /**
     * 获得短信模板分页
     *
     * @param pageReqVO 分页查询
     * @return 短信模板分页
     */
    PageResult<SysSmsTemplateDO> getSmsTemplatePage(SysSmsTemplatePageReqVO pageReqVO);

    /**
     * 获得短信模板列表, 用于 Excel 导出
     *
     * @param exportReqVO 查询条件
     * @return 短信模板分页
     */
    List<SysSmsTemplateDO> getSmsTemplateList(SysSmsTemplateExportReqVO exportReqVO);

    /**
     * 获得指定短信渠道下的短信模板数量
     *
     * @param channelId 短信渠道编号
     * @return 数量
     */
    Integer countByChannelId(Long channelId);

}
