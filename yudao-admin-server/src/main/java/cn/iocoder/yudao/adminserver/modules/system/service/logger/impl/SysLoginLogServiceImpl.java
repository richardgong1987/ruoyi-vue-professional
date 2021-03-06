package cn.iocoder.yudao.adminserver.modules.system.service.logger.impl;

import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.loginlog.SysLoginLogExportReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.loginlog.SysLoginLogPageReqVO;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.logger.SysLoginLogMapper;
import cn.iocoder.yudao.adminserver.modules.system.service.logger.SysLoginLogService;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.logger.SysLoginLogDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 登录日志 Service 实现
 */
@Service
public class SysLoginLogServiceImpl implements SysLoginLogService {

    @Resource
    private SysLoginLogMapper loginLogMapper;

    @Override
    public PageResult<SysLoginLogDO> getLoginLogPage(SysLoginLogPageReqVO reqVO) {
        return loginLogMapper.selectPage(reqVO);
    }

    @Override
    public List<SysLoginLogDO> getLoginLogList(SysLoginLogExportReqVO reqVO) {
        return loginLogMapper.selectList(reqVO);
    }

}
