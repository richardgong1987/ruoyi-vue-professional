package cn.iocoder.yudao.adminserver.modules.system.service.logger;

import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.adminserver.BaseDbUnitTest;
import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.operatelog.SysOperateLogExportReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.operatelog.SysOperateLogPageReqVO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.logger.SysOperateLogDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.logger.SysOperateLogMapper;
import cn.iocoder.yudao.adminserver.modules.system.service.logger.impl.SysOperateLogServiceImpl;
import cn.iocoder.yudao.adminserver.modules.system.service.user.SysUserService;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.coreservice.modules.system.enums.common.SysSexEnum;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.operatelog.core.dto.OperateLogCreateReqDTO;
import cn.iocoder.yudao.framework.operatelog.core.enums.OperateTypeEnum;
import cn.iocoder.yudao.framework.test.core.util.RandomUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.buildTime;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@Import({SysOperateLogServiceImpl.class})
public class SysOperateLogServiceImplTest extends BaseDbUnitTest {

    @Resource
    private SysOperateLogService operateLogServiceImpl;

    @Resource
    private SysOperateLogMapper operateLogMapper;

    @MockBean
    private SysUserService userService;

    @Test
    public void testCreateOperateLogAsync() throws InterruptedException, ExecutionException {
        String traceId = TracerUtils.getTraceId();
        OperateLogCreateReqDTO reqVO = RandomUtils.randomPojo(OperateLogCreateReqDTO.class, o -> {
            o.setTraceId(traceId);
            o.setUserId(randomLongId());
            o.setExts(MapUtil.<String, Object>builder("orderId", randomLongId()).build());
        });

        // ??????service??????
        Future<Boolean> future = operateLogServiceImpl.createOperateLogAsync(reqVO);
        future.get();
        // ????????????????????????
        SysOperateLogDO sysOperateLogDO = operateLogMapper.selectOne("trace_id", traceId);
        assertPojoEquals(reqVO, sysOperateLogDO);
    }

    @Test
    public void testGetOperateLogPage() {
        // ??????????????????
        // ???????????????
        SysUserDO user = RandomUtils.randomPojo(SysUserDO.class, o -> {
            o.setNickname("wangkai");
            o.setSex(SysSexEnum.MALE.getSex());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        when(userService.getUsersByNickname("wangkai")).thenReturn(Collections.singletonList(user));
        Long userId = user.getId();
        // ??????????????????
        SysOperateLogDO sysOperateLogDO = RandomUtils.randomPojo(SysOperateLogDO.class, o -> {
            o.setUserId(userId);
            o.setModule("order");
            o.setType(OperateTypeEnum.CREATE.getType());
            o.setStartTime(buildTime(2021, 3, 6));
            o.setResultCode(GlobalErrorCodeConstants.SUCCESS.getCode());
            o.setExts(MapUtil.<String, Object>builder("orderId", randomLongId()).build());
        });
        operateLogMapper.insert(sysOperateLogDO);

        // ?????????????????????????????????
        // ?????? userId
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setUserId(userId + 1)));
        // module ??????
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setModule("user")));
        // type ??????
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setType(OperateTypeEnum.IMPORT.getType())));
        // createTime ??????
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setStartTime(buildTime(2021, 2, 6))));
        // resultCode ??????
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setResultCode(GlobalErrorCodeConstants.BAD_REQUEST.getCode())));

        // ??????????????????
        SysOperateLogPageReqVO reqVO = new SysOperateLogPageReqVO();
        reqVO.setUserNickname("wangkai");
        reqVO.setModule("order");
        reqVO.setType(OperateTypeEnum.CREATE.getType());
        reqVO.setBeginTime(buildTime(2021, 3, 5));
        reqVO.setEndTime(buildTime(2021, 3, 7));
        reqVO.setSuccess(true);

        // ??????service??????
        PageResult<SysOperateLogDO> pageResult = operateLogServiceImpl.getOperateLogPage(reqVO);
        // ??????????????????????????????????????????
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(sysOperateLogDO, pageResult.getList().get(0));
    }

    @Test
    public void testGetOperateLogs() {
        // ??????????????????
        // ???????????????
        SysUserDO user = RandomUtils.randomPojo(SysUserDO.class, o -> {
            o.setNickname("wangkai");
            o.setSex(SysSexEnum.MALE.getSex());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        when(userService.getUsersByNickname("wangkai")).thenReturn(Collections.singletonList(user));
        Long userId = user.getId();
        // ??????????????????
        SysOperateLogDO sysOperateLogDO = RandomUtils.randomPojo(SysOperateLogDO.class, o -> {
            o.setUserId(userId);
            o.setModule("order");
            o.setType(OperateTypeEnum.CREATE.getType());
            o.setStartTime(buildTime(2021, 3, 6));
            o.setResultCode(GlobalErrorCodeConstants.SUCCESS.getCode());
            o.setExts(MapUtil.<String, Object>builder("orderId", randomLongId()).build());
        });
        operateLogMapper.insert(sysOperateLogDO);

        // ?????????????????????????????????
        // ?????? userId
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setUserId(userId + 1)));
        // module ??????
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setModule("user")));
        // type ??????
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setType(OperateTypeEnum.IMPORT.getType())));
        // createTime ??????
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setStartTime(buildTime(2021, 2, 6))));
        // resultCode ??????
        operateLogMapper.insert(ObjectUtils.cloneIgnoreId(sysOperateLogDO, logDO -> logDO.setResultCode(GlobalErrorCodeConstants.BAD_REQUEST.getCode())));

        // ??????????????????
        SysOperateLogExportReqVO reqVO = new SysOperateLogExportReqVO();
        reqVO.setUserNickname("wangkai");
        reqVO.setModule("order");
        reqVO.setType(OperateTypeEnum.CREATE.getType());
        reqVO.setBeginTime(buildTime(2021, 3, 5));
        reqVO.setEndTime(buildTime(2021, 3, 7));
        reqVO.setSuccess(true);

        // ?????? service ??????
        List<SysOperateLogDO> list = operateLogServiceImpl.getOperateLogs(reqVO);
        // ??????????????????????????????????????????
        assertEquals(1, list.size());
        assertPojoEquals(sysOperateLogDO, list.get(0));
    }

}
