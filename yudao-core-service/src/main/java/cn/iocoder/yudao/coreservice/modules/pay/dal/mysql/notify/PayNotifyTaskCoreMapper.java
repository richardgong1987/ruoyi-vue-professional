package cn.iocoder.yudao.coreservice.modules.pay.dal.mysql.notify;

import cn.iocoder.yudao.coreservice.modules.pay.dal.dataobject.notify.PayNotifyTaskDO;
import cn.iocoder.yudao.coreservice.modules.pay.enums.notify.PayNotifyStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Date;
import java.util.List;

@Mapper
public interface PayNotifyTaskCoreMapper extends BaseMapperX<PayNotifyTaskDO> {

    /**
     * 获得需要通知的 PayNotifyTaskDO 记录。需要满足如下条件：
     *
     * 1. status 非成功
     * 2. nextNotifyTime 小于当前时间
     *
     * @return PayTransactionNotifyTaskDO 数组
     */
    default List<PayNotifyTaskDO> selectListByNotify() {
        return selectList(new QueryWrapper<PayNotifyTaskDO>()
                .in("status", PayNotifyStatusEnum.WAITING.getStatus(), PayNotifyStatusEnum.REQUEST_SUCCESS.getStatus(),
                        PayNotifyStatusEnum.REQUEST_FAILURE.getStatus())
                .le("next_notify_time", new Date()));
    }

}
