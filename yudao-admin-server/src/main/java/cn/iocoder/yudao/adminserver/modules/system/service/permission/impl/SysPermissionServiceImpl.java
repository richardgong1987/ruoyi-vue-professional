package cn.iocoder.yudao.adminserver.modules.system.service.permission.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.dept.SysDeptDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysMenuDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysRoleDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysRoleMenuDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysUserRoleDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.permission.SysRoleMenuMapper;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.permission.SysUserRoleMapper;
import cn.iocoder.yudao.adminserver.modules.system.mq.producer.permission.SysPermissionProducer;
import cn.iocoder.yudao.adminserver.modules.system.service.dept.SysDeptService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysMenuService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysPermissionService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysRoleService;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.dept.service.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.enums.DataScopeEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;

/**
 * ?????? Service ?????????
 *
 * @author ????????????
 */
@Service("ss") // ?????? Spring Security ????????????????????????
@Slf4j
public class SysPermissionServiceImpl implements SysPermissionService {

    /**
     * LoginUser ??? Context ?????? Key
     */
    public static final String CONTEXT_KEY = SysPermissionServiceImpl.class.getSimpleName();

    /**
     * ???????????? {@link #schedulePeriodicRefresh()} ?????????
     * ?????????????????? Redis Pub/Sub ?????????????????????????????????
     */
    private static final long SCHEDULER_PERIOD = 5 * 60 * 1000L;

    /**
     * ??????????????????????????????????????????
     * key???????????????
     * value????????????????????????
     *
     * ???????????? volatile ?????????????????????????????????????????????????????????
     */
    private volatile Multimap<Long, Long> roleMenuCache;
    /**
     * ??????????????????????????????????????????
     * key???????????????
     * value????????????????????????
     *
     * ???????????? volatile ?????????????????????????????????????????????????????????
     */
    private volatile Multimap<Long, Long> menuRoleCache;
    /**
     * ???????????????????????????????????????????????????????????????????????????????????????
     */
    private volatile Date maxUpdateTime;

    @Resource
    private SysRoleMenuMapper roleMenuMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;

    @Resource
    private SysRoleService roleService;
    @Resource
    private SysMenuService menuService;
    @Resource
    private SysDeptService deptService;

    @Resource
    private SysPermissionProducer permissionProducer;

    /**
     * ????????? {@link #roleMenuCache} ??? {@link #menuRoleCache} ??????
     */
    @Override
    @PostConstruct
    public void initLocalCache() {
        Date now = new Date();
        // ??????????????????????????????????????????????????????
        List<SysRoleMenuDO> roleMenuList = this.loadRoleMenuIfUpdate(maxUpdateTime);
        if (CollUtil.isEmpty(roleMenuList)) {
            return;
        }

        // ????????? roleMenuCache ??? menuRoleCache ??????
        ImmutableMultimap.Builder<Long, Long> roleMenuCacheBuilder = ImmutableMultimap.builder();
        ImmutableMultimap.Builder<Long, Long> menuRoleCacheBuilder = ImmutableMultimap.builder();
        roleMenuList.forEach(roleMenuDO -> {
            roleMenuCacheBuilder.put(roleMenuDO.getRoleId(), roleMenuDO.getMenuId());
            menuRoleCacheBuilder.put(roleMenuDO.getMenuId(), roleMenuDO.getRoleId());
        });
        roleMenuCache = roleMenuCacheBuilder.build();
        menuRoleCache = menuRoleCacheBuilder.build();
        assert roleMenuList.size() > 0; // ?????????????????????
        maxUpdateTime = now;
        log.info("[initLocalCache][?????????????????????????????????????????? {}]", roleMenuList.size());
    }

    @Scheduled(fixedDelay = SCHEDULER_PERIOD, initialDelay = SCHEDULER_PERIOD)
    public void schedulePeriodicRefresh() {
        initLocalCache();
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * ????????????????????????????????????
     *
     * @param maxUpdateTime ???????????????????????????????????????????????????
     * @return ??????????????????????????????
     */
    private List<SysRoleMenuDO> loadRoleMenuIfUpdate(Date maxUpdateTime) {
        // ????????????????????????????????????
        if (maxUpdateTime == null) { // ????????????????????????????????? DB ??????????????????
            log.info("[loadRoleMenuIfUpdate][??????????????????????????????????????????]");
        } else { // ????????????????????????????????????????????????????????????
            if (Objects.isNull(roleMenuMapper.selectExistsByUpdateTimeAfter(maxUpdateTime))) {
                return null;
            }
            log.info("[loadRoleMenuIfUpdate][??????????????????????????????????????????]");
        }
        // ?????????????????????????????????????????????????????????????????????????????????
        return roleMenuMapper.selectList();
    }

    @Override
    public List<SysMenuDO> getRoleMenusFromCache(Collection<Long> roleIds, Collection<Integer> menuTypes,
                                                 Collection<Integer> menusStatuses) {
        // ???????????????????????????????????????????????????
        if (CollectionUtils.isAnyEmpty(roleIds, menusStatuses, menusStatuses)) {
            return Collections.emptyList();
        }
        // ?????????????????????????????????
        List<SysRoleDO> roleList = roleService.getRolesFromCache(roleIds);
        boolean hasAdmin = roleService.hasAnyAdmin(roleList);
        // ?????????????????????????????????
        if (hasAdmin) { // ???????????????????????????
            return menuService.listMenusFromCache(menuTypes, menusStatuses);
        }
        List<Long> menuIds = MapUtils.getList(roleMenuCache, roleIds);
        return menuService.listMenusFromCache(menuIds, menuTypes, menusStatuses);
    }

    @Override
    public Set<Long> getUserRoleIds(Long userId, Collection<Integer> roleStatuses) {
        List<SysUserRoleDO> userRoleList = userRoleMapper.selectListByUserId(userId);
        // ??????????????????
        if (CollectionUtil.isNotEmpty(roleStatuses)) {
            userRoleList.removeIf(userRoleDO -> {
                SysRoleDO role = roleService.getRoleFromCache(userRoleDO.getRoleId());
                return role == null || !roleStatuses.contains(role.getStatus());
            });
        }
        return CollectionUtils.convertSet(userRoleList, SysUserRoleDO::getRoleId);
    }

    @Override
    public Set<Long> listRoleMenuIds(Long roleId) {
        // ?????????????????????????????????????????????????????????
        SysRoleDO role = roleService.getRole(roleId);
        if (roleService.hasAnyAdmin(Collections.singletonList(role))) {
            return CollectionUtils.convertSet(menuService.getMenus(), SysMenuDO::getId);
        }
        // ???????????????????????????????????????????????????????????????
        return CollectionUtils.convertSet(roleMenuMapper.selectListByRoleId(roleId),
                SysRoleMenuDO::getMenuId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoleMenu(Long roleId, Set<Long> menuIds) {
        // ??????????????????????????????
        Set<Long> dbMenuIds = CollectionUtils.convertSet(roleMenuMapper.selectListByRoleId(roleId),
                SysRoleMenuDO::getMenuId);
        // ????????????????????????????????????
        Collection<Long> createMenuIds = CollUtil.subtract(menuIds, dbMenuIds);
        Collection<Long> deleteMenuIds = CollUtil.subtract(dbMenuIds, menuIds);
        // ???????????????????????????????????????????????????????????????????????????
        if (!CollectionUtil.isEmpty(createMenuIds)) {
            roleMenuMapper.insertList(roleId, createMenuIds);
        }
        if (!CollectionUtil.isEmpty(deleteMenuIds)) {
            roleMenuMapper.deleteListByRoleIdAndMenuIds(roleId, deleteMenuIds);
        }
        // ??????????????????. ????????????????????????????????????????????????????????????????????? db ???????????????????????????????????????
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                permissionProducer.sendRoleMenuRefreshMessage();
            }

        });
    }

    @Override
    public Set<Long> listUserRoleIs(Long userId) {
        return CollectionUtils.convertSet(userRoleMapper.selectListByUserId(userId),
                SysUserRoleDO::getRoleId);
    }

    @Override
    public void assignUserRole(Long userId, Set<Long> roleIds) {
        // ??????????????????????????????
        Set<Long> dbRoleIds = CollectionUtils.convertSet(userRoleMapper.selectListByUserId(userId),
                SysUserRoleDO::getRoleId);
        // ????????????????????????????????????
        Collection<Long> createRoleIds = CollUtil.subtract(roleIds, dbRoleIds);
        Collection<Long> deleteMenuIds = CollUtil.subtract(dbRoleIds, roleIds);
        // ???????????????????????????????????????????????????????????????????????????
        if (!CollectionUtil.isEmpty(createRoleIds)) {
            userRoleMapper.insertList(userId, createRoleIds);
        }
        if (!CollectionUtil.isEmpty(deleteMenuIds)) {
            userRoleMapper.deleteListByUserIdAndRoleIdIds(userId, deleteMenuIds);
        }
    }

    @Override
    public void assignRoleDataScope(Long roleId, Integer dataScope, Set<Long> dataScopeDeptIds) {
        roleService.updateRoleDataScope(roleId, dataScope, dataScopeDeptIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processRoleDeleted(Long roleId) {
        // ???????????? UserRole
        userRoleMapper.deleteListByRoleId(roleId);
        // ???????????? RoleMenu
        roleMenuMapper.deleteListByRoleId(roleId);
        // ??????????????????. ????????????????????????????????????????????????????????????????????? db ???????????????????????????????????????
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                permissionProducer.sendRoleMenuRefreshMessage();
            }

        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processMenuDeleted(Long menuId) {
        roleMenuMapper.deleteListByMenuId(menuId);
        // ??????????????????. ????????????????????????????????????????????????????????????????????? db ???????????????????????????????????????
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                permissionProducer.sendRoleMenuRefreshMessage();
            }

        });
    }

    @Override
    public void processUserDeleted(Long userId) {
        userRoleMapper.deleteListByUserId(userId);
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasAnyPermissions(permission);
    }

    @Override
    public boolean hasAnyPermissions(String... permissions) {
        // ????????????????????????????????????
        if (ArrayUtil.isEmpty(permissions)) {
            return true;
        }

        // ???????????????????????????????????????????????????????????????
        Set<Long> roleIds = SecurityFrameworkUtils.getLoginUserRoleIds();
        if (CollUtil.isEmpty(roleIds)) {
            return false;
        }
        // ??????????????????????????????????????????????????????
        if (roleService.hasAnyAdmin(roleIds)) {
            return true;
        }

        // ??????????????????????????????????????????
        return Arrays.stream(permissions).anyMatch(permission -> {
            List<SysMenuDO> menuList = menuService.getMenuListByPermissionFromCache(permission);
            // ??????????????????????????????????????????????????? Menu ???????????????
            if (CollUtil.isEmpty(menuList)) {
                return false;
            }
            // ??????????????????????????????????????????
            return menuList.stream().anyMatch(menu -> CollUtil.containsAny(roleIds,
                    menuRoleCache.get(menu.getId())));
        });
    }

    @Override
    public boolean hasRole(String role) {
        return hasAnyRoles(role);
    }

    @Override
    public boolean hasAnyRoles(String... roles) {
        // ????????????????????????????????????
        if (ArrayUtil.isEmpty(roles)) {
            return true;
        }

        // ???????????????????????????????????????????????????????????????
        Set<Long> roleIds = SecurityFrameworkUtils.getLoginUserRoleIds();
        if (CollUtil.isEmpty(roleIds)) {
            return false;
        }
        // ??????????????????????????????????????????????????????
        if (roleService.hasAnyAdmin(roleIds)) {
            return true;
        }
        Set<String> userRoles = CollectionUtils.convertSet(roleService.getRolesFromCache(roleIds),
                SysRoleDO::getCode);
        return CollUtil.containsAny(userRoles, Sets.newHashSet(roles));
    }

    @Override
    public DeptDataPermissionRespDTO getDeptDataPermission(LoginUser loginUser) {
        // ???????????? context ????????????
        DeptDataPermissionRespDTO result = loginUser.getContext(CONTEXT_KEY, DeptDataPermissionRespDTO.class);
        if (result != null) {
            return result;
        }

        // ?????? DeptDataPermissionRespDTO ??????
        result = new DeptDataPermissionRespDTO();
        List<SysRoleDO> roles = roleService.getRolesFromCache(loginUser.getRoleIds());
        for (SysRoleDO role : roles) {
            // ??????????????????
            if (role.getDataScope() == null) {
                continue;
            }
            // ????????????ALL
            if (Objects.equals(role.getDataScope(), DataScopeEnum.ALL.getScope())) {
                result.setAll(true);
                continue;
            }
            // ????????????DEPT_CUSTOM
            if (Objects.equals(role.getDataScope(), DataScopeEnum.DEPT_CUSTOM.getScope())) {
                CollUtil.addAll(result.getDeptIds(), role.getDataScopeDeptIds());
                // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                // ?????????????????????????????? t_user ??? username ?????????????????? dept_id ?????????
                CollUtil.addAll(result.getDeptIds(), loginUser.getDeptId());
                continue;
            }
            // ????????????DEPT_ONLY
            if (Objects.equals(role.getDataScope(), DataScopeEnum.DEPT_ONLY.getScope())) {
                CollectionUtils.addIfNotNull(result.getDeptIds(), loginUser.getDeptId());
                continue;
            }
            // ????????????DEPT_DEPT_AND_CHILD
            if (Objects.equals(role.getDataScope(), DataScopeEnum.DEPT_AND_CHILD.getScope())) {
                List<SysDeptDO> depts = deptService.getDeptsByParentIdFromCache(loginUser.getDeptId(), true);
                CollUtil.addAll(result.getDeptIds(), CollectionUtils.convertList(depts, SysDeptDO::getId));
                continue;
            }
            // ????????????SELF
            if (Objects.equals(role.getDataScope(), DataScopeEnum.SELF.getScope())) {
                result.setSelf(true);
                continue;
            }
            // ???????????????error log ??????
            log.error("[getDeptDataPermission][LoginUser({}) role({}) ????????????]", loginUser.getId(), JsonUtils.toJsonString(result));
        }

        // ???????????????????????????
        loginUser.setContext(CONTEXT_KEY, result);
        return result;
    }

}
