package cn.iocoder.yudao.coreservice.modules.system.dal.mysql.logger;

import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.logger.SysLoginLogDO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysLoginLogCoreMapper extends BaseMapperX<SysLoginLogDO> {

}
