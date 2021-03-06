package cn.iocoder.yudao.adminserver.modules.system.service.auth;

import cn.iocoder.yudao.adminserver.BaseDbUnitTest;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthLoginReqVO;
import cn.iocoder.yudao.adminserver.modules.system.enums.logger.SysLoginLogTypeEnum;
import cn.iocoder.yudao.adminserver.modules.system.enums.logger.SysLoginResultEnum;
import cn.iocoder.yudao.adminserver.modules.system.service.auth.impl.SysAuthServiceImpl;
import cn.iocoder.yudao.adminserver.modules.system.service.common.SysCaptchaService;
import cn.iocoder.yudao.adminserver.modules.system.service.dept.SysPostService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysPermissionService;
import cn.iocoder.yudao.adminserver.modules.system.service.user.SysUserService;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.coreservice.modules.system.service.auth.SysUserSessionCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.logger.SysLoginLogCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.social.SysSocialCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.user.SysUserCoreService;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.test.core.util.AssertUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import javax.annotation.Resource;
import java.util.Set;

import static cn.iocoder.yudao.adminserver.modules.system.enums.SysErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link SysAuthServiceImpl} ???????????????
 *
 * @author ????????????
 */
@Import(SysAuthServiceImpl.class)
public class SysAuthServiceImplTest extends BaseDbUnitTest {

    @Resource
    private SysAuthServiceImpl authService;

    @MockBean
    private SysUserService userService;
    @MockBean
    private SysUserCoreService userCoreService;
    @MockBean
    private SysPermissionService permissionService;
    @MockBean
    private AuthenticationManager authenticationManager;
    @MockBean
    private Authentication authentication;
    @MockBean
    private SysCaptchaService captchaService;
    @MockBean
    private SysLoginLogCoreService loginLogCoreService;
    @MockBean
    private SysUserSessionCoreService userSessionCoreService;
    @MockBean
    private SysSocialCoreService socialService;
    @MockBean
    private SysPostService postService;

    @BeforeEach
    public void setUp() {
        when(captchaService.isCaptchaEnable()).thenReturn(true);
    }

    @Test
    public void testLoadUserByUsername_success() {
        // ????????????
        String username = randomString();
        // mock ??????
        SysUserDO user = randomPojo(SysUserDO.class, o -> o.setUsername(username));
        when(userService.getUserByUsername(eq(username))).thenReturn(user);

        // ??????
        LoginUser loginUser = (LoginUser) authService.loadUserByUsername(username);
        // ??????
        AssertUtils.assertPojoEquals(user, loginUser, "updateTime");
    }

    @Test
    public void testLoadUserByUsername_userNotFound() {
        // ????????????
        String username = randomString();
        // mock ??????

        // ??????, ???????????????
        assertThrows(UsernameNotFoundException.class, // ?????? UsernameNotFoundException ??????
                () -> authService.loadUserByUsername(username),
                username); // ??????????????? username
    }

    @Test
    public void testMockLogin_success() {
        // ????????????
        Long userId = randomLongId();
        // mock ?????? 01
        SysUserDO user = randomPojo(SysUserDO.class, o -> o.setId(userId));
        when(userCoreService.getUser(eq(userId))).thenReturn(user);
        // mock ?????? 02
        Set<Long> roleIds = randomSet(Long.class);
        when(permissionService.getUserRoleIds(eq(userId), eq(singleton(CommonStatusEnum.ENABLE.getStatus()))))
                .thenReturn(roleIds);

        // ??????
        LoginUser loginUser = authService.mockLogin(userId);
        // ??????
        AssertUtils.assertPojoEquals(user, loginUser, "updateTime");
        assertEquals(roleIds, loginUser.getRoleIds());
    }

    @Test
    public void testMockLogin_userNotFound() {
        // ????????????
        Long userId = randomLongId();
        // mock ??????

        // ??????, ???????????????
        assertThrows(UsernameNotFoundException.class, // ?????? UsernameNotFoundException ??????
                () -> authService.mockLogin(userId),
                String.valueOf(userId)); // ??????????????? userId
    }

    @Test
    public void testLogin_captchaNotFound() {
        // ????????????
        SysAuthLoginReqVO reqVO = randomPojo(SysAuthLoginReqVO.class);
        String userIp = randomString();
        String userAgent = randomString();
        // ??????, ???????????????
        assertServiceException(() -> authService.login(reqVO, userIp, userAgent), AUTH_LOGIN_CAPTCHA_NOT_FOUND);
        // ??????????????????
        verify(loginLogCoreService, times(1)).createLoginLog(
            argThat(o -> o.getLogType().equals(SysLoginLogTypeEnum.LOGIN_USERNAME.getType())
                    && o.getResult().equals(SysLoginResultEnum.CAPTCHA_NOT_FOUND.getResult()))
        );
    }

    @Test
    public void testLogin_captchaCodeError() {
        // ????????????
        String userIp = randomString();
        String userAgent = randomString();
        String code = randomString();
        SysAuthLoginReqVO reqVO = randomPojo(SysAuthLoginReqVO.class);
        // mock ??????????????????
        when(captchaService.getCaptchaCode(reqVO.getUuid())).thenReturn(code);
        // ??????, ???????????????
        assertServiceException(() -> authService.login(reqVO, userIp, userAgent), AUTH_LOGIN_CAPTCHA_CODE_ERROR);
        // ??????????????????
        verify(loginLogCoreService, times(1)).createLoginLog(
            argThat(o -> o.getLogType().equals(SysLoginLogTypeEnum.LOGIN_USERNAME.getType())
                    && o.getResult().equals(SysLoginResultEnum.CAPTCHA_CODE_ERROR.getResult()))
        );
    }

    @Test
    public void testLogin_badCredentials() {
        // ????????????
        String userIp = randomString();
        String userAgent = randomString();
        SysAuthLoginReqVO reqVO = randomPojo(SysAuthLoginReqVO.class);
        // mock ???????????????
        when(captchaService.getCaptchaCode(reqVO.getUuid())).thenReturn(reqVO.getCode());
        // mock ????????????
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(reqVO.getUsername(), reqVO.getPassword())))
                .thenThrow(new BadCredentialsException("??????????????????????????????"));
        // ??????, ???????????????
        assertServiceException(() -> authService.login(reqVO, userIp, userAgent), AUTH_LOGIN_BAD_CREDENTIALS);
        // ??????????????????
        verify(captchaService, times(1)).deleteCaptchaCode(reqVO.getUuid());
        verify(loginLogCoreService, times(1)).createLoginLog(
            argThat(o -> o.getLogType().equals(SysLoginLogTypeEnum.LOGIN_USERNAME.getType())
                    && o.getResult().equals(SysLoginResultEnum.BAD_CREDENTIALS.getResult()))
        );
    }

    @Test
    public void testLogin_userDisabled() {
        // ????????????
        String userIp = randomString();
        String userAgent = randomString();
        SysAuthLoginReqVO reqVO = randomPojo(SysAuthLoginReqVO.class);
        // mock ???????????????
        when(captchaService.getCaptchaCode(reqVO.getUuid())).thenReturn(reqVO.getCode());
        // mock ????????????
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(reqVO.getUsername(), reqVO.getPassword())))
                .thenThrow(new DisabledException("?????????????????????"));
        // ??????, ???????????????
        assertServiceException(() -> authService.login(reqVO, userIp, userAgent), AUTH_LOGIN_USER_DISABLED);
        // ??????????????????
        verify(captchaService, times(1)).deleteCaptchaCode(reqVO.getUuid());
        verify(loginLogCoreService, times(1)).createLoginLog(
            argThat(o -> o.getLogType().equals(SysLoginLogTypeEnum.LOGIN_USERNAME.getType())
                    && o.getResult().equals(SysLoginResultEnum.USER_DISABLED.getResult()))
        );
    }

    @Test
    public void testLogin_unknownError() {
        // ????????????
        String userIp = randomString();
        String userAgent = randomString();
        SysAuthLoginReqVO reqVO = randomPojo(SysAuthLoginReqVO.class);
        // mock ???????????????
        when(captchaService.getCaptchaCode(reqVO.getUuid())).thenReturn(reqVO.getCode());
        // mock ????????????
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(reqVO.getUsername(), reqVO.getPassword())))
                .thenThrow(new AuthenticationException("??????????????????") {});
        // ??????, ???????????????
        assertServiceException(() -> authService.login(reqVO, userIp, userAgent), AUTH_LOGIN_FAIL_UNKNOWN);
        // ??????????????????
        verify(captchaService, times(1)).deleteCaptchaCode(reqVO.getUuid());
        verify(loginLogCoreService, times(1)).createLoginLog(
            argThat(o -> o.getLogType().equals(SysLoginLogTypeEnum.LOGIN_USERNAME.getType())
                    && o.getResult().equals(SysLoginResultEnum.UNKNOWN_ERROR.getResult()))
        );
    }

    @Test
    public void testLogin_success() {
        // ????????????
        String userIp = randomString();
        String userAgent = randomString();
        Long userId = randomLongId();
        Set<Long> userRoleIds = randomSet(Long.class);
        String sessionId = randomString();
        SysAuthLoginReqVO reqVO = randomPojo(SysAuthLoginReqVO.class);
        LoginUser loginUser = randomPojo(LoginUser.class, o -> {
            o.setId(userId);
            o.setRoleIds(userRoleIds);
        });
        // mock ???????????????
        when(captchaService.getCaptchaCode(reqVO.getUuid())).thenReturn(reqVO.getCode());
        // mock authentication
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(reqVO.getUsername(), reqVO.getPassword())))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        // mock ?????? User ???????????????????????????
        when(permissionService.getUserRoleIds(userId, singleton(CommonStatusEnum.ENABLE.getStatus()))).thenReturn(userRoleIds);
        // mock ????????????????????? Redis
        when(userSessionCoreService.createUserSession(loginUser, userIp, userAgent)).thenReturn(sessionId);
        // ??????, ???????????????
        String login = authService.login(reqVO, userIp, userAgent);
        assertEquals(sessionId, login);
        // ??????????????????
        verify(captchaService, times(1)).deleteCaptchaCode(reqVO.getUuid());
        verify(loginLogCoreService, times(1)).createLoginLog(
            argThat(o -> o.getLogType().equals(SysLoginLogTypeEnum.LOGIN_USERNAME.getType())
                    && o.getResult().equals(SysLoginResultEnum.SUCCESS.getResult()))
        );
    }

    @Test
    public void testLogout_success() {
        // ????????????
        String token = randomString();
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock
        when(userSessionCoreService.getLoginUser(token)).thenReturn(loginUser);
        // ??????
        authService.logout(token);
        // ??????????????????
        verify(userSessionCoreService, times(1)).deleteUserSession(token);
        verify(loginLogCoreService, times(1)).createLoginLog(
            argThat(o -> o.getLogType().equals(SysLoginLogTypeEnum.LOGOUT_SELF.getType())
                    && o.getResult().equals(SysLoginResultEnum.SUCCESS.getResult()))
        );
    }

}
