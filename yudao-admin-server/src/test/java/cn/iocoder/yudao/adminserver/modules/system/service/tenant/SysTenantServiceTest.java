package cn.iocoder.yudao.adminserver.modules.system.service.tenant;

import cn.iocoder.yudao.adminserver.BaseDbUnitTest;
import cn.iocoder.yudao.adminserver.modules.system.controller.tenant.vo.SysTenantCreateReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.tenant.vo.SysTenantExportReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.tenant.vo.SysTenantPageReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.tenant.vo.SysTenantUpdateReqVO;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.tenant.SysTenantMapper;
import cn.iocoder.yudao.adminserver.modules.system.service.tenant.impl.SysTenantServiceImpl;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.tenant.SysTenantDO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.adminserver.modules.system.enums.SysErrorCodeConstants.TENANT_NOT_EXISTS;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.buildTime;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.cloneIgnoreId;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SysTenantServiceImpl} 的单元测试类
 *
 * @author 芋道源码
 */
@Import(SysTenantServiceImpl.class)
public class SysTenantServiceTest extends BaseDbUnitTest {

    @Resource
    private SysTenantServiceImpl tenantService;

    @Resource
    private SysTenantMapper tenantMapper;

    @Test
    public void testCreateTenant_success() {
        // 准备参数
        SysTenantCreateReqVO reqVO = randomPojo(SysTenantCreateReqVO.class, o -> o.setStatus(randomCommonStatus()));

        // 调用
        Long tenantId = tenantService.createTenant(reqVO);
        // 断言
        assertNotNull(tenantId);
        // 校验记录的属性是否正确
        SysTenantDO tenant = tenantMapper.selectById(tenantId);
        assertPojoEquals(reqVO, tenant);
    }

    @Test
    public void testUpdateTenant_success() {
        // mock 数据
        SysTenantDO dbTenant = randomPojo(SysTenantDO.class, o -> o.setStatus(randomCommonStatus()));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        // 准备参数
        SysTenantUpdateReqVO reqVO = randomPojo(SysTenantUpdateReqVO.class, o -> {
            o.setId(dbTenant.getId()); // 设置更新的 ID
            o.setStatus(randomCommonStatus());
        });

        // 调用
        tenantService.updateTenant(reqVO);
        // 校验是否更新正确
        SysTenantDO tenant = tenantMapper.selectById(reqVO.getId()); // 获取最新的
        assertPojoEquals(reqVO, tenant);
    }

    @Test
    public void testUpdateTenant_notExists() {
        // 准备参数
        SysTenantUpdateReqVO reqVO = randomPojo(SysTenantUpdateReqVO.class);

        // 调用, 并断言异常
        assertServiceException(() -> tenantService.updateTenant(reqVO), TENANT_NOT_EXISTS);
    }

    @Test
    public void testDeleteTenant_success() {
        // mock 数据
        SysTenantDO dbTenant = randomPojo(SysTenantDO.class,
                o -> o.setStatus(randomCommonStatus()));
        tenantMapper.insert(dbTenant);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbTenant.getId();

        // 调用
        tenantService.deleteTenant(id);
        // 校验数据不存在了
        assertNull(tenantMapper.selectById(id));
    }

    @Test
    public void testDeleteTenant_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用, 并断言异常
        assertServiceException(() -> tenantService.deleteTenant(id), TENANT_NOT_EXISTS);
    }

    @Test
    public void testGetTenantPage() {
        // mock 数据
        SysTenantDO dbTenant = randomPojo(SysTenantDO.class, o -> { // 等会查询到
            o.setName("芋道源码");
            o.setContactName("芋艿");
            o.setContactMobile("15601691300");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setCreateTime(buildTime(2020, 12, 12));
        });
        tenantMapper.insert(dbTenant);
        // 测试 name 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setName(randomString())));
        // 测试 contactName 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setContactName(randomString())));
        // 测试 contactMobile 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setContactMobile(randomString())));
        // 测试 status 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus())));
        // 测试 createTime 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setCreateTime(buildTime(2021, 12, 12))));
        // 准备参数
        SysTenantPageReqVO reqVO = new SysTenantPageReqVO();
        reqVO.setName("芋道");
        reqVO.setContactName("艿");
        reqVO.setContactMobile("1560");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setBeginCreateTime(buildTime(2020, 12, 1));
        reqVO.setEndCreateTime(buildTime(2020, 12, 24));

        // 调用
        PageResult<SysTenantDO> pageResult = tenantService.getTenantPage(reqVO);
        // 断言
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbTenant, pageResult.getList().get(0));
    }

    @Test
    public void testGetTenantList() {
        // mock 数据
        SysTenantDO dbTenant = randomPojo(SysTenantDO.class, o -> { // 等会查询到
            o.setName("芋道源码");
            o.setContactName("芋艿");
            o.setContactMobile("15601691300");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setCreateTime(buildTime(2020, 12, 12));
        });
        tenantMapper.insert(dbTenant);
        // 测试 name 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setName(randomString())));
        // 测试 contactName 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setContactName(randomString())));
        // 测试 contactMobile 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setContactMobile(randomString())));
        // 测试 status 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus())));
        // 测试 createTime 不匹配
        tenantMapper.insert(cloneIgnoreId(dbTenant, o -> o.setCreateTime(buildTime(2021, 12, 12))));
        // 准备参数
        SysTenantExportReqVO reqVO = new SysTenantExportReqVO();
        reqVO.setName("芋道");
        reqVO.setContactName("艿");
        reqVO.setContactMobile("1560");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setBeginCreateTime(buildTime(2020, 12, 1));
        reqVO.setEndCreateTime(buildTime(2020, 12, 24));

        // 调用
        List<SysTenantDO> list = tenantService.getTenantList(reqVO);
        // 断言
        assertEquals(1, list.size());
        assertPojoEquals(dbTenant, list.get(0));
    }

}
