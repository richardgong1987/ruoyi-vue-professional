package cn.iocoder.yudao.adminserver.modules.system.mq.message.dept;

import cn.iocoder.yudao.framework.mq.core.pubsub.AbstractChannelMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门数据刷新 Message
 *
 * @author 芋道源码
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDeptRefreshMessage extends AbstractChannelMessage {

    @Override
    public String getChannel() {
        return "system.dept.refresh";
    }

}
