package cn.iocoder.yudao.coreservice.modules.system.dal.mysql.auth;

import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.auth.SysUserSessionDO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserSessionCoreMapper extends BaseMapperX<SysUserSessionDO> {

}
