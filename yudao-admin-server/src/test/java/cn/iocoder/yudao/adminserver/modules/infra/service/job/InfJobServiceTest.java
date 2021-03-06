package cn.iocoder.yudao.adminserver.modules.infra.service.job;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.adminserver.modules.infra.enums.InfErrorCodeConstants.JOB_CHANGE_STATUS_EQUALS;
import static cn.iocoder.yudao.adminserver.modules.infra.enums.InfErrorCodeConstants.JOB_CHANGE_STATUS_INVALID;
import static cn.iocoder.yudao.adminserver.modules.infra.enums.InfErrorCodeConstants.JOB_CRON_EXPRESSION_VALID;
import static cn.iocoder.yudao.adminserver.modules.infra.enums.InfErrorCodeConstants.JOB_HANDLER_EXISTS;
import static cn.iocoder.yudao.adminserver.modules.infra.enums.InfErrorCodeConstants.JOB_NOT_EXISTS;
import static cn.iocoder.yudao.adminserver.modules.infra.enums.InfErrorCodeConstants.JOB_UPDATE_ONLY_NORMAL_STATUS;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.quartz.SchedulerException;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import cn.iocoder.yudao.adminserver.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.quartz.core.scheduler.SchedulerManager;
import cn.iocoder.yudao.adminserver.modules.infra.controller.job.vo.job.InfJobCreateReqVO;
import cn.iocoder.yudao.adminserver.modules.infra.controller.job.vo.job.InfJobExportReqVO;
import cn.iocoder.yudao.adminserver.modules.infra.controller.job.vo.job.InfJobPageReqVO;
import cn.iocoder.yudao.adminserver.modules.infra.controller.job.vo.job.InfJobUpdateReqVO;
import cn.iocoder.yudao.adminserver.modules.infra.convert.job.InfJobConvert;
import cn.iocoder.yudao.adminserver.modules.infra.dal.dataobject.job.InfJobDO;
import cn.iocoder.yudao.adminserver.modules.infra.dal.mysql.job.InfJobMapper;
import cn.iocoder.yudao.adminserver.modules.infra.enums.job.InfJobStatusEnum;
import cn.iocoder.yudao.adminserver.modules.infra.service.job.impl.InfJobServiceImpl;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;

/**
 * {@link InfJobServiceImpl} ???????????????
 *
 * @author neilz
 */
@Import(InfJobServiceImpl.class)
public class InfJobServiceTest extends BaseDbUnitTest {

    @Resource
    private InfJobServiceImpl jobService;
    @Resource
    private InfJobMapper jobMapper;
    @MockBean
    private SchedulerManager schedulerManager;

    @Test
    public void testCreateJob_cronExpressionValid() {
        // ???????????????Cron ???????????? String ?????????????????????????????????
        InfJobCreateReqVO reqVO = randomPojo(InfJobCreateReqVO.class);
        // ????????????????????????
        assertServiceException(() -> jobService.createJob(reqVO), JOB_CRON_EXPRESSION_VALID);
    }

    @Test
    public void testCreateJob_jobHandlerExists() throws SchedulerException {
        // ???????????? ?????? Cron ?????????
        InfJobCreateReqVO reqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        // ??????
        jobService.createJob(reqVO);
        // ????????????????????????
        assertServiceException(() -> jobService.createJob(reqVO), JOB_HANDLER_EXISTS);
    }

    @Test
    public void testCreateJob_success() throws SchedulerException {
        // ???????????? ?????? Cron ?????????
        InfJobCreateReqVO reqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        // ??????
        Long jobId = jobService.createJob(reqVO);
        // ??????
        assertNotNull(jobId);
        // ?????????????????????????????????
        InfJobDO job = jobMapper.selectById(jobId);
        assertPojoEquals(reqVO, job);
        assertEquals(InfJobStatusEnum.NORMAL.getStatus(), job.getStatus());
        // ????????????
        verify(schedulerManager, times(1)).addJob(eq(job.getId()), eq(job.getHandlerName()), eq(job.getHandlerParam()), eq(job.getCronExpression()),
                eq(reqVO.getRetryCount()), eq(reqVO.getRetryInterval()));
    }

    @Test
    public void testUpdateJob_jobNotExists(){
        // ????????????
        InfJobUpdateReqVO reqVO = randomPojo(InfJobUpdateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        // ????????????????????????
        assertServiceException(() -> jobService.updateJob(reqVO), JOB_NOT_EXISTS);
    }

    @Test
    public void testUpdateJob_onlyNormalStatus(){
        // mock ??????
        InfJobCreateReqVO createReqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        InfJobDO job = InfJobConvert.INSTANCE.convert(createReqVO);
        job.setStatus(InfJobStatusEnum.INIT.getStatus());
        fillJobMonitorTimeoutEmpty(job);
        jobMapper.insert(job);
        // ????????????
        InfJobUpdateReqVO updateReqVO = randomPojo(InfJobUpdateReqVO.class, o -> {
            o.setId(job.getId());
            o.setName(createReqVO.getName());
            o.setCronExpression(createReqVO.getCronExpression());
        });
        // ????????????????????????
        assertServiceException(() -> jobService.updateJob(updateReqVO), JOB_UPDATE_ONLY_NORMAL_STATUS);
    }

    @Test
    public void testUpdateJob_success() throws SchedulerException {
        // mock ??????
        InfJobCreateReqVO createReqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        InfJobDO job = InfJobConvert.INSTANCE.convert(createReqVO);
        job.setStatus(InfJobStatusEnum.NORMAL.getStatus());
        fillJobMonitorTimeoutEmpty(job);
        jobMapper.insert(job);
        // ????????????
        InfJobUpdateReqVO updateReqVO = randomPojo(InfJobUpdateReqVO.class, o -> {
            o.setId(job.getId());
            o.setName(createReqVO.getName());
            o.setCronExpression(createReqVO.getCronExpression());
        });
        // ??????
        jobService.updateJob(updateReqVO);
        // ?????????????????????????????????
        InfJobDO updateJob = jobMapper.selectById(updateReqVO.getId());
        assertPojoEquals(updateReqVO, updateJob);
        // ????????????
        verify(schedulerManager, times(1)).updateJob(eq(job.getHandlerName()), eq(updateReqVO.getHandlerParam()), eq(updateReqVO.getCronExpression()),
                eq(updateReqVO.getRetryCount()), eq(updateReqVO.getRetryInterval()));
    }

    @Test
    public void testUpdateJobStatus_changeStatusInvalid() {
        // ????????????????????????
        assertServiceException(() -> jobService.updateJobStatus(1l, InfJobStatusEnum.INIT.getStatus()), JOB_CHANGE_STATUS_INVALID);
    }

    @Test
    public void testUpdateJobStatus_changeStatusEquals() {
        // mock ??????
        InfJobCreateReqVO createReqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        InfJobDO job = InfJobConvert.INSTANCE.convert(createReqVO);
        job.setStatus(InfJobStatusEnum.NORMAL.getStatus());
        fillJobMonitorTimeoutEmpty(job);
        jobMapper.insert(job);
        // ????????????????????????
        assertServiceException(() -> jobService.updateJobStatus(job.getId(), job.getStatus()), JOB_CHANGE_STATUS_EQUALS);
    }

    @Test
    public void testUpdateJobStatus_NormalToStop_success() throws SchedulerException {
        // mock ??????
        InfJobCreateReqVO createReqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        InfJobDO job = InfJobConvert.INSTANCE.convert(createReqVO);
        job.setStatus(InfJobStatusEnum.NORMAL.getStatus());
        fillJobMonitorTimeoutEmpty(job);
        jobMapper.insert(job);
        // ??????
        jobService.updateJobStatus(job.getId(), InfJobStatusEnum.STOP.getStatus());
        // ?????????????????????????????????
        InfJobDO updateJob = jobMapper.selectById(job.getId());
        assertEquals(InfJobStatusEnum.STOP.getStatus(), updateJob.getStatus());
        // ????????????
        verify(schedulerManager, times(1)).pauseJob(eq(job.getHandlerName()));
    }

    @Test
    public void testUpdateJobStatus_StopToNormal_success() throws SchedulerException {
        // mock ??????
        InfJobCreateReqVO createReqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        InfJobDO job = InfJobConvert.INSTANCE.convert(createReqVO);
        job.setStatus(InfJobStatusEnum.STOP.getStatus());
        fillJobMonitorTimeoutEmpty(job);
        jobMapper.insert(job);
        // ??????
        jobService.updateJobStatus(job.getId(), InfJobStatusEnum.NORMAL.getStatus());
        // ?????????????????????????????????
        InfJobDO updateJob = jobMapper.selectById(job.getId());
        assertEquals(InfJobStatusEnum.NORMAL.getStatus(), updateJob.getStatus());
        // ????????????
        verify(schedulerManager, times(1)).resumeJob(eq(job.getHandlerName()));
    }

    @Test
    public void testTriggerJob_success() throws SchedulerException {
        // mock ??????
        InfJobCreateReqVO createReqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        InfJobDO job = InfJobConvert.INSTANCE.convert(createReqVO);
        job.setStatus(InfJobStatusEnum.NORMAL.getStatus());
        fillJobMonitorTimeoutEmpty(job);
        jobMapper.insert(job);
        // ??????
        jobService.triggerJob(job.getId());
        // ????????????
        verify(schedulerManager, times(1)).triggerJob(eq(job.getId()), eq(job.getHandlerName()), eq(job.getHandlerParam()));
    }

    @Test
    public void testDeleteJob_success() throws SchedulerException {
        // mock ??????
        InfJobCreateReqVO createReqVO = randomPojo(InfJobCreateReqVO.class, o -> o.setCronExpression("0 0/1 * * * ? *"));
        InfJobDO job = InfJobConvert.INSTANCE.convert(createReqVO);
        job.setStatus(InfJobStatusEnum.NORMAL.getStatus());
        fillJobMonitorTimeoutEmpty(job);
        jobMapper.insert(job);
        // ?????? UPDATE inf_job SET deleted=1 WHERE id=? AND deleted=0
        jobService.deleteJob(job.getId());
        // ????????????????????????  WHERE id=? AND deleted=0 ??????????????????
        assertNull(jobMapper.selectById(job.getId()));
        // ????????????
        verify(schedulerManager, times(1)).deleteJob(eq(job.getHandlerName()));
    }

    @Test
    public void testGetJobListByIds_success() {
        // mock ??????
        InfJobDO dbJob = randomPojo(InfJobDO.class, o -> {
            o.setStatus(randomEle(InfJobStatusEnum.values()).getStatus()); // ?????? status ?????????
        });
        InfJobDO cloneJob = ObjectUtils.cloneIgnoreId(dbJob, o -> o.setHandlerName(randomString()));
        jobMapper.insert(dbJob);
        // ?????? handlerName ?????????
        jobMapper.insert(cloneJob);
        // ????????????
        ArrayList ids = new ArrayList<>();
        ids.add(dbJob.getId());
        ids.add(cloneJob.getId());
        // ??????
        List<InfJobDO> list = jobService.getJobList(ids);
        // ??????
        assertEquals(2, list.size());
        assertPojoEquals(dbJob, list.get(0));
    }

    @Test
    public void testGetJobPage_success() {
        // mock ??????
        InfJobDO dbJob = randomPojo(InfJobDO.class, o -> {
            o.setName("??????????????????");
            o.setHandlerName("handlerName ????????????");
            o.setStatus(InfJobStatusEnum.INIT.getStatus());
        });
        jobMapper.insert(dbJob);
        // ?????? name ?????????
        jobMapper.insert(ObjectUtils.cloneIgnoreId(dbJob, o -> o.setName("??????")));
        // ?????? status ?????????
        jobMapper.insert(ObjectUtils.cloneIgnoreId(dbJob, o -> o.setStatus(InfJobStatusEnum.NORMAL.getStatus())));
        // ?????? handlerName ?????????
        jobMapper.insert(ObjectUtils.cloneIgnoreId(dbJob, o -> o.setHandlerName(randomString())));
        // ????????????
        InfJobPageReqVO reqVo = new InfJobPageReqVO();
        reqVo.setName("??????");
        reqVo.setStatus(InfJobStatusEnum.INIT.getStatus());
        reqVo.setHandlerName("??????");
        // ??????
        PageResult<InfJobDO> pageResult = jobService.getJobPage(reqVo);
        // ??????
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbJob, pageResult.getList().get(0));
    }

    @Test
    public void testGetJobListForExport_success() {
        // mock ??????
        InfJobDO dbJob = randomPojo(InfJobDO.class, o -> {
            o.setName("??????????????????");
            o.setHandlerName("handlerName ????????????");
            o.setStatus(InfJobStatusEnum.INIT.getStatus());
        });
        jobMapper.insert(dbJob);
        // ?????? name ?????????
        jobMapper.insert(ObjectUtils.cloneIgnoreId(dbJob, o -> o.setName("??????")));
        // ?????? status ?????????
        jobMapper.insert(ObjectUtils.cloneIgnoreId(dbJob, o -> o.setStatus(InfJobStatusEnum.NORMAL.getStatus())));
        // ?????? handlerName ?????????
        jobMapper.insert(ObjectUtils.cloneIgnoreId(dbJob, o -> o.setHandlerName(randomString())));
        // ????????????
        InfJobExportReqVO reqVo = new InfJobExportReqVO();
        reqVo.setName("??????");
        reqVo.setStatus(InfJobStatusEnum.INIT.getStatus());
        reqVo.setHandlerName("??????");
        // ??????
        List<InfJobDO> list = jobService.getJobList(reqVo);
        // ??????
        assertEquals(1, list.size());
        assertPojoEquals(dbJob, list.get(0));
    }

    private static void fillJobMonitorTimeoutEmpty(InfJobDO job) {
        if (job.getMonitorTimeout() == null) {
            job.setMonitorTimeout(0);
        }
    }

}
