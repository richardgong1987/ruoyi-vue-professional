package cn.iocoder.yudao.adminserver.modules.system.service.sms;

import cn.hutool.core.bean.BeanUtil;
import cn.iocoder.yudao.adminserver.BaseDbUnitTest;
import cn.iocoder.yudao.adminserver.modules.system.controller.sms.vo.channel.SysSmsChannelCreateReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.sms.vo.channel.SysSmsChannelPageReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.sms.vo.channel.SysSmsChannelUpdateReqVO;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.sms.SysSmsChannelMapper;
import cn.iocoder.yudao.adminserver.modules.system.mq.producer.sms.SysSmsProducer;
import cn.iocoder.yudao.adminserver.modules.system.service.sms.impl.SysSmsChannelServiceImpl;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.sms.SysSmsChannelDO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.ArrayUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.sms.core.client.SmsClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.Date;
import java.util.function.Consumer;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.adminserver.modules.system.enums.SysErrorCodeConstants.SMS_CHANNEL_HAS_CHILDREN;
import static cn.iocoder.yudao.adminserver.modules.system.enums.SysErrorCodeConstants.SMS_CHANNEL_NOT_EXISTS;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.buildTime;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.max;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.*;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
* {@link SysSmsChannelServiceImpl} ??????????????????
*
* @author ????????????
*/
@Import(SysSmsChannelServiceImpl.class)
public class SysSmsChannelServiceTest extends BaseDbUnitTest {

    @Resource
    private SysSmsChannelServiceImpl smsChannelService;

    @Resource
    private SysSmsChannelMapper smsChannelMapper;

    @MockBean
    private SmsClientFactory smsClientFactory;
    @MockBean
    private SysSmsTemplateService smsTemplateService;
    @MockBean
    private SysSmsProducer smsProducer;

    @Test
    public void testInitLocalCache_success() {
        // mock ??????
        SysSmsChannelDO smsChannelDO01 = randomSmsChannelDO();
        smsChannelMapper.insert(smsChannelDO01);
        SysSmsChannelDO smsChannelDO02 = randomSmsChannelDO();
        smsChannelMapper.insert(smsChannelDO02);

        // ??????
        smsChannelService.initSmsClients();
        // ?????? maxUpdateTime ??????
        Date maxUpdateTime = (Date) BeanUtil.getFieldValue(smsChannelService, "maxUpdateTime");
        assertEquals(max(smsChannelDO01.getUpdateTime(), smsChannelDO02.getUpdateTime()), maxUpdateTime);
        // ????????????
        verify(smsClientFactory, times(1)).createOrUpdateSmsClient(
                argThat(properties -> isPojoEquals(smsChannelDO01, properties)));
        verify(smsClientFactory, times(1)).createOrUpdateSmsClient(
                argThat(properties -> isPojoEquals(smsChannelDO02, properties)));
    }

    @Test
    public void testCreateSmsChannel_success() {
        // ????????????
        SysSmsChannelCreateReqVO reqVO = randomPojo(SysSmsChannelCreateReqVO.class, o -> o.setStatus(randomCommonStatus()));

        // ??????
        Long smsChannelId = smsChannelService.createSmsChannel(reqVO);
        // ??????
        assertNotNull(smsChannelId);
        // ?????????????????????????????????
        SysSmsChannelDO smsChannel = smsChannelMapper.selectById(smsChannelId);
        assertPojoEquals(reqVO, smsChannel);
        // ????????????
        verify(smsProducer, times(1)).sendSmsChannelRefreshMessage();
    }

    @Test
    public void testUpdateSmsChannel_success() {
        // mock ??????
        SysSmsChannelDO dbSmsChannel = randomSmsChannelDO();
        smsChannelMapper.insert(dbSmsChannel);// @Sql: ?????????????????????????????????
        // ????????????
        SysSmsChannelUpdateReqVO reqVO = randomPojo(SysSmsChannelUpdateReqVO.class, o -> {
            o.setId(dbSmsChannel.getId()); // ??????????????? ID
            o.setStatus(randomCommonStatus());
            o.setCallbackUrl(randomString());
        });

        // ??????
        smsChannelService.updateSmsChannel(reqVO);
        // ????????????????????????
        SysSmsChannelDO smsChannel = smsChannelMapper.selectById(reqVO.getId()); // ???????????????
        assertPojoEquals(reqVO, smsChannel);
        // ????????????
        verify(smsProducer, times(1)).sendSmsChannelRefreshMessage();
    }

    @Test
    public void testUpdateSmsChannel_notExists() {
        // ????????????
        SysSmsChannelUpdateReqVO reqVO = randomPojo(SysSmsChannelUpdateReqVO.class);

        // ??????, ???????????????
        assertServiceException(() -> smsChannelService.updateSmsChannel(reqVO), SMS_CHANNEL_NOT_EXISTS);
    }

    @Test
    public void testDeleteSmsChannel_success() {
        // mock ??????
        SysSmsChannelDO dbSmsChannel = randomSmsChannelDO();
        smsChannelMapper.insert(dbSmsChannel);// @Sql: ?????????????????????????????????
        // ????????????
        Long id = dbSmsChannel.getId();

        // ??????
        smsChannelService.deleteSmsChannel(id);
       // ????????????????????????
       assertNull(smsChannelMapper.selectById(id));
        // ????????????
        verify(smsProducer, times(1)).sendSmsChannelRefreshMessage();
    }

    @Test
    public void testDeleteSmsChannel_notExists() {
        // ????????????
        Long id = randomLongId();

        // ??????, ???????????????
        assertServiceException(() -> smsChannelService.deleteSmsChannel(id), SMS_CHANNEL_NOT_EXISTS);
    }

    @Test
    public void testDeleteSmsChannel_hasChildren() {
        // mock ??????
        SysSmsChannelDO dbSmsChannel = randomSmsChannelDO();
        smsChannelMapper.insert(dbSmsChannel);// @Sql: ?????????????????????????????????
        // ????????????
        Long id = dbSmsChannel.getId();
        // mock ??????
        when(smsTemplateService.countByChannelId(eq(id))).thenReturn(10);

        // ??????, ???????????????
        assertServiceException(() -> smsChannelService.deleteSmsChannel(id), SMS_CHANNEL_HAS_CHILDREN);
    }

    @Test
    public void testGetSmsChannelPage() {
       // mock ??????
       SysSmsChannelDO dbSmsChannel = randomPojo(SysSmsChannelDO.class, o -> { // ???????????????
           o.setSignature("????????????");
           o.setStatus(CommonStatusEnum.ENABLE.getStatus());
           o.setCreateTime(buildTime(2020, 12, 12));
       });
       smsChannelMapper.insert(dbSmsChannel);
       // ?????? signature ?????????
       smsChannelMapper.insert(ObjectUtils.cloneIgnoreId(dbSmsChannel, o -> o.setSignature("??????")));
       // ?????? status ?????????
       smsChannelMapper.insert(ObjectUtils.cloneIgnoreId(dbSmsChannel, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus())));
       // ?????? createTime ?????????
       smsChannelMapper.insert(ObjectUtils.cloneIgnoreId(dbSmsChannel, o -> o.setCreateTime(buildTime(2020, 11, 11))));
       // ????????????
       SysSmsChannelPageReqVO reqVO = new SysSmsChannelPageReqVO();
       reqVO.setSignature("??????");
       reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
       reqVO.setBeginCreateTime(buildTime(2020, 12, 1));
       reqVO.setEndCreateTime(buildTime(2020, 12, 24));

       // ??????
       PageResult<SysSmsChannelDO> pageResult = smsChannelService.getSmsChannelPage(reqVO);
       // ??????
       assertEquals(1, pageResult.getTotal());
       assertEquals(1, pageResult.getList().size());
       assertPojoEquals(dbSmsChannel, pageResult.getList().get(0));
    }

    // ========== ???????????? ==========

    @SafeVarargs
    private static SysSmsChannelDO randomSmsChannelDO(Consumer<SysSmsChannelDO>... consumers) {
        Consumer<SysSmsChannelDO> consumer = (o) -> {
            o.setStatus(randomEle(CommonStatusEnum.values()).getStatus()); // ?????? status ?????????
        };
        return randomPojo(SysSmsChannelDO.class, ArrayUtils.append(consumer, consumers));
    }

}
