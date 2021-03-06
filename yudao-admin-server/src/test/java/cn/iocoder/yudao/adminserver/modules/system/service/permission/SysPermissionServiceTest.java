package cn.iocoder.yudao.adminserver.modules.system.service.permission;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.adminserver.BaseDbUnitTest;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.dept.SysDeptDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysRoleDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysRoleMenuDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysUserRoleDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.permission.SysRoleMenuMapper;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.permission.SysUserRoleMapper;
import cn.iocoder.yudao.adminserver.modules.system.mq.producer.permission.SysPermissionProducer;
import cn.iocoder.yudao.adminserver.modules.system.service.dept.SysDeptService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.impl.SysPermissionServiceImpl;
import cn.iocoder.yudao.framework.datapermission.core.dept.service.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.enums.DataScopeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(SysPermissionServiceImpl.class)
public class SysPermissionServiceTest extends BaseDbUnitTest {

    @Resource
    private SysPermissionServiceImpl permissionService;

    @Resource
    private SysRoleMenuMapper roleMenuMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;

    @MockBean
    private SysRoleService roleService;
    @MockBean
    private SysMenuService menuService;
    @MockBean
    private SysDeptService deptService;

    @MockBean
    private SysPermissionProducer permissionProducer;

    @Test
    public void testProcessRoleDeleted() {
        // ????????????
        Long roleId = randomLongId();
        // mock ?????? UserRole
        SysUserRoleDO userRoleDO01 = randomPojo(SysUserRoleDO.class, o -> o.setRoleId(roleId)); // ?????????
        userRoleMapper.insert(userRoleDO01);
        SysUserRoleDO userRoleDO02 = randomPojo(SysUserRoleDO.class); // ????????????
        userRoleMapper.insert(userRoleDO02);
        // mock ?????? RoleMenu
        SysRoleMenuDO roleMenuDO01 = randomPojo(SysRoleMenuDO.class, o -> o.setRoleId(roleId)); // ?????????
        roleMenuMapper.insert(roleMenuDO01);
        SysRoleMenuDO roleMenuDO02 = randomPojo(SysRoleMenuDO.class); // ????????????
        roleMenuMapper.insert(roleMenuDO02);

        // ??????
        permissionService.processRoleDeleted(roleId);
        // ???????????? RoleMenuDO
        List<SysRoleMenuDO> dbRoleMenus = roleMenuMapper.selectList();
        assertEquals(1, dbRoleMenus.size());
        assertPojoEquals(dbRoleMenus.get(0), roleMenuDO02);
        // ???????????? UserRoleDO
        List<SysUserRoleDO> dbUserRoles = userRoleMapper.selectList();
        assertEquals(1, dbUserRoles.size());
        assertPojoEquals(dbUserRoles.get(0), userRoleDO02);
        // ????????????
        verify(permissionProducer).sendRoleMenuRefreshMessage();
    }

    @Test
    public void testProcessMenuDeleted() {
        // ????????????
        Long menuId = randomLongId();
        // mock ??????
        SysRoleMenuDO roleMenuDO01 = randomPojo(SysRoleMenuDO.class, o -> o.setMenuId(menuId)); // ?????????
        roleMenuMapper.insert(roleMenuDO01);
        SysRoleMenuDO roleMenuDO02 = randomPojo(SysRoleMenuDO.class); // ????????????
        roleMenuMapper.insert(roleMenuDO02);

        // ??????
        permissionService.processMenuDeleted(menuId);
        // ????????????
        List<SysRoleMenuDO> dbRoleMenus = roleMenuMapper.selectList();
        assertEquals(1, dbRoleMenus.size());
        assertPojoEquals(dbRoleMenus.get(0), roleMenuDO02);
        // ????????????
        verify(permissionProducer).sendRoleMenuRefreshMessage();
    }

    @Test
    public void testProcessUserDeleted() {
        // ????????????
        Long userId = randomLongId();
        // mock ??????
        SysUserRoleDO userRoleDO01 = randomPojo(SysUserRoleDO.class, o -> o.setUserId(userId)); // ?????????
        userRoleMapper.insert(userRoleDO01);
        SysUserRoleDO userRoleDO02 = randomPojo(SysUserRoleDO.class); // ????????????
        userRoleMapper.insert(userRoleDO02);

        // ??????
        permissionService.processUserDeleted(userId);
        // ????????????
        List<SysUserRoleDO> dbUserRoles = userRoleMapper.selectList();
        assertEquals(1, dbUserRoles.size());
        assertPojoEquals(dbUserRoles.get(0), userRoleDO02);
    }

    @Test // ????????? context ???????????????
    public void testGetDeptDataPermission_fromContext() {
        // ????????????
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock ??????
        DeptDataPermissionRespDTO respDTO = new DeptDataPermissionRespDTO();
        loginUser.setContext(SysPermissionServiceImpl.CONTEXT_KEY, respDTO);

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(loginUser);
        // ??????
        assertSame(respDTO, result);
    }

    @Test
    public void testGetDeptDataPermission_All() {
        // ????????????
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock ??????
        SysRoleDO roleDO = randomPojo(SysRoleDO.class, o -> o.setDataScope(DataScopeEnum.ALL.getScope()));
        when(roleService.getRolesFromCache(same(loginUser.getRoleIds()))).thenReturn(singletonList(roleDO));

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(loginUser);
        // ??????
        assertTrue(result.getAll());
        assertFalse(result.getSelf());
        assertTrue(CollUtil.isEmpty(result.getDeptIds()));
        assertSame(result, loginUser.getContext(SysPermissionServiceImpl.CONTEXT_KEY, DeptDataPermissionRespDTO.class));
    }

    @Test
    public void testGetDeptDataPermission_DeptCustom() {
        // ????????????
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock ??????
        SysRoleDO roleDO = randomPojo(SysRoleDO.class, o -> o.setDataScope(DataScopeEnum.DEPT_CUSTOM.getScope()));
        when(roleService.getRolesFromCache(same(loginUser.getRoleIds()))).thenReturn(singletonList(roleDO));

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(loginUser);
        // ??????
        assertFalse(result.getAll());
        assertFalse(result.getSelf());
        assertEquals(roleDO.getDataScopeDeptIds().size() + 1, result.getDeptIds().size());
        assertTrue(CollUtil.containsAll(result.getDeptIds(), roleDO.getDataScopeDeptIds()));
        assertTrue(CollUtil.contains(result.getDeptIds(), loginUser.getDeptId()));
        assertSame(result, loginUser.getContext(SysPermissionServiceImpl.CONTEXT_KEY, DeptDataPermissionRespDTO.class));
    }

    @Test
    public void testGetDeptDataPermission_DeptOnly() {
        // ????????????
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock ??????
        SysRoleDO roleDO = randomPojo(SysRoleDO.class, o -> o.setDataScope(DataScopeEnum.DEPT_ONLY.getScope()));
        when(roleService.getRolesFromCache(same(loginUser.getRoleIds()))).thenReturn(singletonList(roleDO));

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(loginUser);
        // ??????
        assertFalse(result.getAll());
        assertFalse(result.getSelf());
        assertEquals(1, result.getDeptIds().size());
        assertTrue(CollUtil.contains(result.getDeptIds(), loginUser.getDeptId()));
        assertSame(result, loginUser.getContext(SysPermissionServiceImpl.CONTEXT_KEY, DeptDataPermissionRespDTO.class));
    }

    @Test
    public void testGetDeptDataPermission_DeptAndChild() {
        // ????????????
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock ??????????????????
        SysRoleDO roleDO = randomPojo(SysRoleDO.class, o -> o.setDataScope(DataScopeEnum.DEPT_AND_CHILD.getScope()));
        when(roleService.getRolesFromCache(same(loginUser.getRoleIds()))).thenReturn(singletonList(roleDO));
        // mock ??????????????????
        SysDeptDO deptDO = randomPojo(SysDeptDO.class);
        when(deptService.getDeptsByParentIdFromCache(eq(loginUser.getDeptId()), eq(true)))
                .thenReturn(singletonList(deptDO));

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(loginUser);
        // ??????
        assertFalse(result.getAll());
        assertFalse(result.getSelf());
        assertEquals(1, result.getDeptIds().size());
        assertTrue(CollUtil.contains(result.getDeptIds(), deptDO.getId()));
        assertSame(result, loginUser.getContext(SysPermissionServiceImpl.CONTEXT_KEY, DeptDataPermissionRespDTO.class));
    }

    @Test
    public void testGetDeptDataPermission_Self() {
        // ????????????
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock ??????
        SysRoleDO roleDO = randomPojo(SysRoleDO.class, o -> o.setDataScope(DataScopeEnum.SELF.getScope()));
        when(roleService.getRolesFromCache(same(loginUser.getRoleIds()))).thenReturn(singletonList(roleDO));

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(loginUser);
        // ??????
        assertFalse(result.getAll());
        assertTrue(result.getSelf());
        assertTrue(CollUtil.isEmpty(result.getDeptIds()));
        assertSame(result, loginUser.getContext(SysPermissionServiceImpl.CONTEXT_KEY, DeptDataPermissionRespDTO.class));
    }

}
