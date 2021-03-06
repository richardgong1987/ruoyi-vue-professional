package cn.iocoder.yudao.adminserver.modules.system.service.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.iocoder.yudao.adminserver.BaseDbUnitTest;
import cn.iocoder.yudao.adminserver.modules.system.controller.user.vo.profile.SysUserProfileUpdatePasswordReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.user.vo.profile.SysUserProfileUpdateReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.user.vo.user.*;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.dept.SysDeptDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.dept.SysPostDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.user.SysUserMapper;
import cn.iocoder.yudao.adminserver.modules.system.service.dept.SysDeptService;
import cn.iocoder.yudao.adminserver.modules.system.service.dept.SysPostService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysPermissionService;
import cn.iocoder.yudao.adminserver.modules.system.service.user.impl.SysUserServiceImpl;
import cn.iocoder.yudao.coreservice.modules.infra.service.file.InfFileCoreService;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.coreservice.modules.system.enums.common.SysSexEnum;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.ArrayUtils;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static cn.hutool.core.util.RandomUtil.randomBytes;
import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.adminserver.modules.system.enums.SysErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.buildTime;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static org.assertj.core.util.Lists.newArrayList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SysUserService} ??????????????????
 *
 * @author zxl
 */
@Import(SysUserServiceImpl.class)
public class SysUserServiceImplTest extends BaseDbUnitTest {

    @Resource
    private SysUserServiceImpl userService;

    @Resource
    private SysUserMapper userMapper;

    @MockBean
    private SysDeptService deptService;
    @MockBean
    private SysPostService postService;
    @MockBean
    private SysPermissionService permissionService;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private InfFileCoreService fileService;

    @Test
    public void testCreatUser_success() {
        // ????????????
        SysUserCreateReqVO reqVO = randomPojo(SysUserCreateReqVO.class, o -> {
            o.setSex(RandomUtil.randomEle(SysSexEnum.values()).getSex());
            o.setMobile(randomString());
        });
        // mock deptService ?????????
        SysDeptDO dept = randomPojo(SysDeptDO.class, o -> {
            o.setId(reqVO.getDeptId());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        when(deptService.getDept(eq(dept.getId()))).thenReturn(dept);
        // mock postService ?????????
        List<SysPostDO> posts = CollectionUtils.convertList(reqVO.getPostIds(), postId ->
                randomPojo(SysPostDO.class, o -> {
                    o.setId(postId);
                    o.setStatus(CommonStatusEnum.ENABLE.getStatus());
                }));
        when(postService.getPosts(eq(reqVO.getPostIds()), isNull())).thenReturn(posts);
        // mock passwordEncoder ?????????
        when(passwordEncoder.encode(eq(reqVO.getPassword()))).thenReturn("yudaoyuanma");

        // ??????
        Long userId = userService.createUser(reqVO);
        // ??????
        SysUserDO user = userMapper.selectById(userId);
        assertPojoEquals(reqVO, user, "password");
        assertEquals("yudaoyuanma", user.getPassword());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), user.getStatus());
    }

    @Test
    public void testUpdateUser_success() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO();
        userMapper.insert(dbUser);
        // ????????????
        SysUserUpdateReqVO reqVO = randomPojo(SysUserUpdateReqVO.class, o -> {
            o.setId(dbUser.getId());
            o.setSex(RandomUtil.randomEle(SysSexEnum.values()).getSex());
            o.setMobile(randomString());
        });
        // mock deptService ?????????
        SysDeptDO dept = randomPojo(SysDeptDO.class, o -> {
            o.setId(reqVO.getDeptId());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        when(deptService.getDept(eq(dept.getId()))).thenReturn(dept);
        // mock postService ?????????
        List<SysPostDO> posts = CollectionUtils.convertList(reqVO.getPostIds(), postId ->
                randomPojo(SysPostDO.class, o -> {
                    o.setId(postId);
                    o.setStatus(CommonStatusEnum.ENABLE.getStatus());
                }));
        when(postService.getPosts(eq(reqVO.getPostIds()), isNull())).thenReturn(posts);

        // ??????
        userService.updateUser(reqVO);
        // ??????
        SysUserDO user = userMapper.selectById(reqVO.getId());
        assertPojoEquals(reqVO, user);
    }

    @Test
    public void testUpdateUserProfile_success() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO();
        userMapper.insert(dbUser);
        // ????????????
        Long userId = dbUser.getId();
        SysUserProfileUpdateReqVO reqVO = randomPojo(SysUserProfileUpdateReqVO.class, o -> {
            o.setMobile(randomString());
            o.setSex(RandomUtil.randomEle(SysSexEnum.values()).getSex());
        });

        // ??????
        userService.updateUserProfile(userId, reqVO);
        // ??????
        SysUserDO user = userMapper.selectById(userId);
        assertPojoEquals(reqVO, user);
    }

    @Test
    public void testUpdateUserPassword_success() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO(o -> o.setPassword("encode:yudao"));
        userMapper.insert(dbUser);
        // ????????????
        Long userId = dbUser.getId();
        SysUserProfileUpdatePasswordReqVO reqVO = randomPojo(SysUserProfileUpdatePasswordReqVO.class, o -> {
            o.setOldPassword("yudao");
            o.setNewPassword("yuanma");
        });
        // mock ??????
        when(passwordEncoder.encode(anyString())).then(
                (Answer<String>) invocationOnMock -> "encode:" + invocationOnMock.getArgument(0));
        when(passwordEncoder.matches(eq(reqVO.getOldPassword()), eq(dbUser.getPassword()))).thenReturn(true);

        // ??????
        userService.updateUserPassword(userId, reqVO);
        // ??????
        SysUserDO user = userMapper.selectById(userId);
        assertEquals("encode:yuanma", user.getPassword());
    }

    @Test
    public void testUpdateUserAvatar_success() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO();
        userMapper.insert(dbUser);
        // ????????????
        Long userId = dbUser.getId();
        byte[] avatarFileBytes = randomBytes(10);
        ByteArrayInputStream avatarFile = new ByteArrayInputStream(avatarFileBytes);
        // mock ??????
        String avatar = randomString();
        when(fileService.createFile(anyString(), eq(avatarFileBytes))).thenReturn(avatar);

        // ??????
        userService.updateUserAvatar(userId, avatarFile);
        // ??????
        SysUserDO user = userMapper.selectById(userId);
        assertEquals(avatar, user.getAvatar());
    }

    @Test
    public void testUpdateUserPassword02_success() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO();
        userMapper.insert(dbUser);
        // ????????????
        Long userId = dbUser.getId();
        String password = "yudao";
        // mock ??????
        when(passwordEncoder.encode(anyString())).then(
                (Answer<String>) invocationOnMock -> "encode:" + invocationOnMock.getArgument(0));

        // ??????
        userService.updateUserPassword(userId, password);
        // ??????
        SysUserDO user = userMapper.selectById(userId);
        assertEquals("encode:" + password, user.getPassword());
    }

    @Test
    public void testUpdateUserStatus() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO();
        userMapper.insert(dbUser);
        // ????????????
        Long userId = dbUser.getId();
        Integer status = randomCommonStatus();

        // ??????
        userService.updateUserStatus(userId, status);
        // ??????
        SysUserDO user = userMapper.selectById(userId);
        assertEquals(status, user.getStatus());
    }

    @Test
    public void testDeleteUser_success(){
        // mock ??????
        SysUserDO dbUser = randomSysUserDO();
        userMapper.insert(dbUser);
        // ????????????
        Long userId = dbUser.getId();

        // ????????????
        userService.deleteUser(userId);
        // ????????????
        assertNull(userMapper.selectById(userId));
        // ??????????????????
        verify(permissionService, times(1)).processUserDeleted(eq(userId));
    }

    @Test
    public void testGetUserPage() {
        // mock ??????
        SysUserDO dbUser = initGetUserPageData();
        // ????????????
        SysUserPageReqVO reqVO = new SysUserPageReqVO();
        reqVO.setUsername("yudao");
        reqVO.setMobile("1560");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setBeginTime(buildTime(2020, 12, 1));
        reqVO.setEndTime(buildTime(2020, 12, 24));
        reqVO.setDeptId(1L); // ?????????1L ??? 2L ????????????
        // mock ??????
        List<SysDeptDO> deptList = newArrayList(randomPojo(SysDeptDO.class, o -> o.setId(2L)));
        when(deptService.getDeptsByParentIdFromCache(eq(reqVO.getDeptId()), eq(true))).thenReturn(deptList);

        // ??????
        PageResult<SysUserDO> pageResult = userService.getUserPage(reqVO);
        // ??????
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbUser, pageResult.getList().get(0));
    }

    @Test
    public void testGetUsers() {
        // mock ??????
        SysUserDO dbUser = initGetUserPageData();
        // ????????????
        SysUserExportReqVO reqVO = new SysUserExportReqVO();
        reqVO.setUsername("yudao");
        reqVO.setMobile("1560");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setBeginTime(buildTime(2020, 12, 1));
        reqVO.setEndTime(buildTime(2020, 12, 24));
        reqVO.setDeptId(1L); // ?????????1L ??? 2L ????????????
        // mock ??????
        List<SysDeptDO> deptList = newArrayList(randomPojo(SysDeptDO.class, o -> o.setId(2L)));
        when(deptService.getDeptsByParentIdFromCache(eq(reqVO.getDeptId()), eq(true))).thenReturn(deptList);

        // ??????
        List<SysUserDO> list = userService.getUsers(reqVO);
        // ??????
        assertEquals(1, list.size());
        assertPojoEquals(dbUser, list.get(0));
    }

    /**
     * ????????? getUserPage ?????????????????????
     */
    private SysUserDO initGetUserPageData() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO(o -> { // ???????????????
            o.setUsername("yudaoyuanma");
            o.setMobile("15601691300");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setCreateTime(buildTime(2020, 12, 12));
            o.setDeptId(2L);
        });
        userMapper.insert(dbUser);
        // ?????? username ?????????
        userMapper.insert(ObjectUtils.cloneIgnoreId(dbUser, o -> o.setUsername("yuanma")));
        // ?????? mobile ?????????
        userMapper.insert(ObjectUtils.cloneIgnoreId(dbUser, o -> o.setMobile("18818260888")));
        // ?????? status ?????????
        userMapper.insert(ObjectUtils.cloneIgnoreId(dbUser, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus())));
        // ?????? createTime ?????????
        userMapper.insert(ObjectUtils.cloneIgnoreId(dbUser, o -> o.setCreateTime(buildTime(2020, 11, 11))));
        // ?????? dept ?????????
        userMapper.insert(ObjectUtils.cloneIgnoreId(dbUser, o -> o.setDeptId(0L)));
        return dbUser;
    }

    /**
     * ????????????????????????????????????????????????
     */
    @Test
    public void testImportUsers_01() {
        // ????????????
        SysUserImportExcelVO importUser = randomPojo(SysUserImportExcelVO.class);
        // mock ??????

        // ??????
        SysUserImportRespVO respVO = userService.importUsers(newArrayList(importUser), true);
        // ??????
        assertEquals(0, respVO.getCreateUsernames().size());
        assertEquals(0, respVO.getUpdateUsernames().size());
        assertEquals(1, respVO.getFailureUsernames().size());
        assertEquals(DEPT_NOT_FOUND.getMsg(), respVO.getFailureUsernames().get(importUser.getUsername()));
    }

    /**
     * ????????????????????????????????????
     */
    @Test
    public void testImportUsers_02() {
        // ????????????
        SysUserImportExcelVO importUser = randomPojo(SysUserImportExcelVO.class, o -> {
            o.setStatus(randomEle(CommonStatusEnum.values()).getStatus()); // ?????? status ?????????
            o.setSex(randomEle(SysSexEnum.values()).getSex()); // ?????? sex ?????????
        });
        // mock deptService ?????????
        SysDeptDO dept = randomPojo(SysDeptDO.class, o -> {
            o.setId(importUser.getDeptId());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        when(deptService.getDept(eq(dept.getId()))).thenReturn(dept);
        // mock passwordEncoder ?????????
        when(passwordEncoder.encode(eq("yudaoyuanma"))).thenReturn("java");

        // ??????
        SysUserImportRespVO respVO = userService.importUsers(newArrayList(importUser), true);
        // ??????
        assertEquals(1, respVO.getCreateUsernames().size());
        SysUserDO user = userMapper.selectByUsername(respVO.getCreateUsernames().get(0));
        assertPojoEquals(importUser, user);
        assertEquals("java", user.getPassword());
        assertEquals(0, respVO.getUpdateUsernames().size());
        assertEquals(0, respVO.getFailureUsernames().size());
    }

    /**
     * ??????????????????????????????????????????
     */
    @Test
    public void testImportUsers_03() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO();
        userMapper.insert(dbUser);
        // ????????????
        SysUserImportExcelVO importUser = randomPojo(SysUserImportExcelVO.class, o -> {
            o.setStatus(randomEle(CommonStatusEnum.values()).getStatus()); // ?????? status ?????????
            o.setSex(randomEle(SysSexEnum.values()).getSex()); // ?????? sex ?????????
            o.setUsername(dbUser.getUsername());
        });
        // mock deptService ?????????
        SysDeptDO dept = randomPojo(SysDeptDO.class, o -> {
            o.setId(importUser.getDeptId());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        when(deptService.getDept(eq(dept.getId()))).thenReturn(dept);

        // ??????
        SysUserImportRespVO respVO = userService.importUsers(newArrayList(importUser), false);
        // ??????
        assertEquals(0, respVO.getCreateUsernames().size());
        assertEquals(0, respVO.getUpdateUsernames().size());
        assertEquals(1, respVO.getFailureUsernames().size());
        assertEquals(USER_USERNAME_EXISTS.getMsg(), respVO.getFailureUsernames().get(importUser.getUsername()));
    }

    /**
     * ?????????????????????????????????
     */
    @Test
    public void testImportUsers_04() {
        // mock ??????
        SysUserDO dbUser = randomSysUserDO();
        userMapper.insert(dbUser);
        // ????????????
        SysUserImportExcelVO importUser = randomPojo(SysUserImportExcelVO.class, o -> {
            o.setStatus(randomEle(CommonStatusEnum.values()).getStatus()); // ?????? status ?????????
            o.setSex(randomEle(SysSexEnum.values()).getSex()); // ?????? sex ?????????
            o.setUsername(dbUser.getUsername());
        });
        // mock deptService ?????????
        SysDeptDO dept = randomPojo(SysDeptDO.class, o -> {
            o.setId(importUser.getDeptId());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        when(deptService.getDept(eq(dept.getId()))).thenReturn(dept);

        // ??????
        SysUserImportRespVO respVO = userService.importUsers(newArrayList(importUser), true);
        // ??????
        assertEquals(0, respVO.getCreateUsernames().size());
        assertEquals(1, respVO.getUpdateUsernames().size());
        SysUserDO user = userMapper.selectByUsername(respVO.getUpdateUsernames().get(0));
        assertPojoEquals(importUser, user);
        assertEquals(0, respVO.getFailureUsernames().size());
    }

    @Test
    public void testCheckUserExists_notExists() {
        assertServiceException(() -> userService.checkUserExists(randomLongId()), USER_NOT_EXISTS);
    }

    @Test
    public void testCheckUsernameUnique_usernameExistsForCreate() {
        // ????????????
        String username = randomString();
        // mock ??????
        userMapper.insert(randomSysUserDO(o -> o.setUsername(username)));

        // ?????????????????????
        assertServiceException(() -> userService.checkUsernameUnique(null, username),
                USER_USERNAME_EXISTS);
    }

    @Test
    public void testCheckUsernameUnique_usernameExistsForUpdate() {
        // ????????????
        Long id = randomLongId();
        String username = randomString();
        // mock ??????
        userMapper.insert(randomSysUserDO(o -> o.setUsername(username)));

        // ?????????????????????
        assertServiceException(() -> userService.checkUsernameUnique(id, username),
                USER_USERNAME_EXISTS);
    }

    @Test
    public void testCheckEmailUnique_emailExistsForCreate() {
        // ????????????
        String email = randomString();
        // mock ??????
        userMapper.insert(randomSysUserDO(o -> o.setEmail(email)));

        // ?????????????????????
        assertServiceException(() -> userService.checkEmailUnique(null, email),
                USER_EMAIL_EXISTS);
    }

    @Test
    public void testCheckEmailUnique_emailExistsForUpdate() {
        // ????????????
        Long id = randomLongId();
        String email = randomString();
        // mock ??????
        userMapper.insert(randomSysUserDO(o -> o.setEmail(email)));

        // ?????????????????????
        assertServiceException(() -> userService.checkEmailUnique(id, email),
                USER_EMAIL_EXISTS);
    }

    @Test
    public void testCheckMobileUnique_mobileExistsForCreate() {
        // ????????????
        String mobile = randomString();
        // mock ??????
        userMapper.insert(randomSysUserDO(o -> o.setMobile(mobile)));

        // ?????????????????????
        assertServiceException(() -> userService.checkMobileUnique(null, mobile),
                USER_MOBILE_EXISTS);
    }

    @Test
    public void testCheckMobileUnique_mobileExistsForUpdate() {
        // ????????????
        Long id = randomLongId();
        String mobile = randomString();
        // mock ??????
        userMapper.insert(randomSysUserDO(o -> o.setMobile(mobile)));

        // ?????????????????????
        assertServiceException(() -> userService.checkMobileUnique(id, mobile),
                USER_MOBILE_EXISTS);
    }

    @Test
    public void testCheckDeptEnable_notFound() {
        assertServiceException(() -> userService.checkDeptEnable(randomLongId()),
                DEPT_NOT_FOUND);
    }

    @Test
    public void testCheckDeptEnable_notEnable() {
        // ????????????
        Long deptId = randomLongId();
        // mock deptService ?????????
        SysDeptDO dept = randomPojo(SysDeptDO.class, o -> {
            o.setId(deptId);
            o.setStatus(CommonStatusEnum.DISABLE.getStatus());
        });
        when(deptService.getDept(eq(dept.getId()))).thenReturn(dept);

        // ?????????????????????
        assertServiceException(() -> userService.checkDeptEnable(deptId),
                DEPT_NOT_ENABLE);
    }

    @Test
    public void testCheckPostEnable_notFound() {
        assertServiceException(() -> userService.checkPostEnable(randomSet(Long.class)),
                POST_NOT_FOUND);
    }

    @Test
    public void testCheckPostEnable_notEnable() {
        // ????????????
        Set<Long> postIds = randomSet(Long.class);
        // mock postService ?????????
        List<SysPostDO> posts = CollectionUtils.convertList(postIds, postId ->
                randomPojo(SysPostDO.class, o -> {
                    o.setId(postId);
                    o.setStatus(CommonStatusEnum.DISABLE.getStatus());
                }));
        when(postService.getPosts(eq(postIds), isNull())).thenReturn(posts);

        // ?????????????????????
        assertServiceException(() -> userService.checkPostEnable(postIds),
                POST_NOT_ENABLE, CollUtil.getFirst(posts).getName());
    }

    @Test
    public void testCheckOldPassword_notExists() {
        assertServiceException(() -> userService.checkOldPassword(randomLongId(), randomString()),
                USER_NOT_EXISTS);
    }

    @Test
    public void testCheckOldPassword_passwordFailed() {
        // mock ??????
        SysUserDO user = randomSysUserDO();
        userMapper.insert(user);
        // ????????????
        Long id = user.getId();
        String oldPassword = user.getPassword();

        // ?????????????????????
        assertServiceException(() -> userService.checkOldPassword(id, oldPassword),
                USER_PASSWORD_FAILED);
        // ????????????
        verify(passwordEncoder, times(1)).matches(eq(oldPassword), eq(user.getPassword()));
    }

    // ========== ???????????? ==========

    @SafeVarargs
    private static SysUserDO randomSysUserDO(Consumer<SysUserDO>... consumers) {
        Consumer<SysUserDO> consumer = (o) -> {
            o.setStatus(randomEle(CommonStatusEnum.values()).getStatus()); // ?????? status ?????????
            o.setSex(randomEle(SysSexEnum.values()).getSex()); // ?????? sex ?????????
        };
        return randomPojo(SysUserDO.class, ArrayUtils.append(consumer, consumers));
    }

}
