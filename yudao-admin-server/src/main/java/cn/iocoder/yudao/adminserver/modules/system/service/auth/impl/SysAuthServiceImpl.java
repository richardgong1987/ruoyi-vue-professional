package cn.iocoder.yudao.adminserver.modules.system.service.auth.impl;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthLoginReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthSocialBindReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthSocialLogin2ReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthSocialLoginReqVO;
import cn.iocoder.yudao.adminserver.modules.system.convert.auth.SysAuthConvert;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.dept.SysPostDO;
import cn.iocoder.yudao.adminserver.modules.system.enums.logger.SysLoginLogTypeEnum;
import cn.iocoder.yudao.adminserver.modules.system.enums.logger.SysLoginResultEnum;
import cn.iocoder.yudao.adminserver.modules.system.service.auth.SysAuthService;
import cn.iocoder.yudao.adminserver.modules.system.service.common.SysCaptchaService;
import cn.iocoder.yudao.adminserver.modules.system.service.dept.SysPostService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysPermissionService;
import cn.iocoder.yudao.adminserver.modules.system.service.user.SysUserService;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.social.SysSocialUserDO;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.coreservice.modules.system.service.auth.SysUserSessionCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.logger.SysLoginLogCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.logger.dto.SysLoginLogCreateReqDTO;
import cn.iocoder.yudao.coreservice.modules.system.service.social.SysSocialCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.user.SysUserCoreService;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.adminserver.modules.system.enums.SysErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static java.util.Collections.singleton;

/**
 * Auth Service ?????????
 *
 * @author ????????????
 */
@Service
@Slf4j
public class SysAuthServiceImpl implements SysAuthService {

    private static final UserTypeEnum USER_TYPE_ENUM = UserTypeEnum.ADMIN;

    @Resource
    @Lazy // ????????????????????????????????????????????????
    private AuthenticationManager authenticationManager;

    @Resource
    private SysUserService userService;
    @Resource
    private SysUserCoreService userCoreService;
    @Resource
    private SysPermissionService permissionService;
    @Resource
    private SysCaptchaService captchaService;
    @Resource
    private SysLoginLogCoreService loginLogCoreService;
    @Resource
    private SysUserSessionCoreService userSessionCoreService;
    @Resource
    private SysPostService postService;
    @Resource
    private SysSocialCoreService socialService;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ?????? username ????????? SysUserDO
        SysUserDO user = userService.getUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException(username);
        }
        // ?????? LoginUser ??????
        return this.buildLoginUser(user);
    }

    @Override
    public LoginUser mockLogin(Long userId) {
        // ??????????????????????????? SysUserDO
        SysUserDO user = userCoreService.getUser(userId);
        if (user == null) {
            throw new UsernameNotFoundException(String.valueOf(userId));
        }
        this.createLoginLog(user.getUsername(), SysLoginLogTypeEnum.LOGIN_MOCK, SysLoginResultEnum.SUCCESS);

        // ?????? LoginUser ??????
        return this.buildLoginUser(user);
    }

    @Override
    public String login(SysAuthLoginReqVO reqVO, String userIp, String userAgent) {
        // ???????????????????????????
        this.verifyCaptcha(reqVO.getUsername(), reqVO.getUuid(), reqVO.getCode());

        // ?????????????????????????????????
        LoginUser loginUser = this.login0(reqVO.getUsername(), reqVO.getPassword());

        // ????????????????????? Redis ???????????? sessionId ??????
        return userSessionCoreService.createUserSession(loginUser, userIp, userAgent);
    }

    private List<String> getUserPosts(Set<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyList();
        }
        return convertList(postService.getPosts(postIds), SysPostDO::getCode);
    }

    private void verifyCaptcha(String username, String captchaUUID, String captchaCode) {
        // ??????????????????????????????????????????
        if (!captchaService.isCaptchaEnable()) {
            return;
        }
        // ??????????????????
        final SysLoginLogTypeEnum logTypeEnum = SysLoginLogTypeEnum.LOGIN_USERNAME;
        String code = captchaService.getCaptchaCode(captchaUUID);
        if (code == null) {
            // ????????????????????????????????????????????????
            this.createLoginLog(username, logTypeEnum, SysLoginResultEnum.CAPTCHA_NOT_FOUND);
            throw exception(AUTH_LOGIN_CAPTCHA_NOT_FOUND);
        }
        // ??????????????????
        if (!code.equals(captchaCode)) {
            // ?????????????????????????????????????????????)
            this.createLoginLog(username, logTypeEnum, SysLoginResultEnum.CAPTCHA_CODE_ERROR);
            throw exception(AUTH_LOGIN_CAPTCHA_CODE_ERROR);
        }
        // ????????????????????????????????????
        captchaService.deleteCaptchaCode(captchaUUID);
    }

    private LoginUser login0(String username, String password) {
        final SysLoginLogTypeEnum logTypeEnum = SysLoginLogTypeEnum.LOGIN_USERNAME;
        // ????????????
        Authentication authentication;
        try {
            // ?????? Spring Security ??? AuthenticationManager#authenticate(...) ???????????????????????????????????????
            // ??????????????????????????? loadUserByUsername ??????????????? User ??????
            authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
           //  org.activiti.engine.impl.identity.Authentication.setAuthenticatedUserId(username);
        } catch (BadCredentialsException badCredentialsException) {
            this.createLoginLog(username, logTypeEnum, SysLoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        } catch (DisabledException disabledException) {
            this.createLoginLog(username, logTypeEnum, SysLoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        } catch (AuthenticationException authenticationException) {
            log.error("[login0][username({}) ??????????????????]", username, authenticationException);
            this.createLoginLog(username, logTypeEnum, SysLoginResultEnum.UNKNOWN_ERROR);
            throw exception(AUTH_LOGIN_FAIL_UNKNOWN);
        }
        // ?????????????????????
        Assert.notNull(authentication.getPrincipal(), "Principal ????????????");
        this.createLoginLog(username, logTypeEnum, SysLoginResultEnum.SUCCESS);
        return (LoginUser) authentication.getPrincipal();
    }

    private void createLoginLog(String username, SysLoginLogTypeEnum logTypeEnum, SysLoginResultEnum loginResult) {
        // ????????????
        SysUserDO user = userService.getUserByUsername(username);
        // ??????????????????
        SysLoginLogCreateReqDTO reqDTO = new SysLoginLogCreateReqDTO();
        reqDTO.setLogType(logTypeEnum.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        if (user != null) {
            reqDTO.setUserId(user.getId());
        }
        reqDTO.setUserType(UserTypeEnum.ADMIN.getValue());
        reqDTO.setUsername(username);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(loginResult.getResult());
        loginLogCoreService.createLoginLog(reqDTO);
        // ????????????????????????
        if (user != null && Objects.equals(SysLoginResultEnum.SUCCESS.getResult(), loginResult.getResult())) {
            userService.updateUserLogin(user.getId(), ServletUtils.getClientIP());
        }
    }

    /**
     * ?????? User ???????????????????????????
     *
     * @param userId ????????????
     * @return ??????????????????
     */
    private Set<Long> getUserRoleIds(Long userId) {
        return permissionService.getUserRoleIds(userId, singleton(CommonStatusEnum.ENABLE.getStatus()));
    }

    @Override
    public String socialLogin(SysAuthSocialLoginReqVO reqVO, String userIp, String userAgent) {
        // ?????? code ????????????????????????
        AuthUser authUser = socialService.getAuthUser(reqVO.getType(), reqVO.getCode(), reqVO.getState());
        Assert.notNull(authUser, "?????????????????????");

        // ??????????????? SysSocialUserDO ?????????????????????????????????????????????
        String unionId = socialService.getAuthUserUnionId(authUser);
        List<SysSocialUserDO> socialUsers = socialService.getAllSocialUserList(reqVO.getType(), unionId, USER_TYPE_ENUM);
        if (CollUtil.isEmpty(socialUsers)) {
            throw exception(AUTH_THIRD_LOGIN_NOT_BIND);
        }

        // ????????????
        SysUserDO user = userCoreService.getUser(socialUsers.get(0).getUserId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        this.createLoginLog(user.getUsername(), SysLoginLogTypeEnum.LOGIN_SOCIAL, SysLoginResultEnum.SUCCESS);

        // ?????? LoginUser ??????
        LoginUser loginUser = this.buildLoginUser(user);

        // ??????????????????????????????
        socialService.bindSocialUser(loginUser.getId(), reqVO.getType(), authUser, USER_TYPE_ENUM);

        // ????????????????????? Redis ???????????? sessionId ??????
        return userSessionCoreService.createUserSession(loginUser, userIp, userAgent);
    }

    @Override
    public String socialLogin2(SysAuthSocialLogin2ReqVO reqVO, String userIp, String userAgent) {
        // ?????? code ????????????????????????
        AuthUser authUser = socialService.getAuthUser(reqVO.getType(), reqVO.getCode(), reqVO.getState());
        Assert.notNull(authUser, "?????????????????????");

        // ????????????????????????????????????
        LoginUser loginUser = this.login0(reqVO.getUsername(), reqVO.getPassword());

        // ??????????????????????????????
        socialService.bindSocialUser(loginUser.getId(), reqVO.getType(), authUser, USER_TYPE_ENUM);

        // ????????????????????? Redis ???????????? sessionId ??????
        return userSessionCoreService.createUserSession(loginUser, userIp, userAgent);
    }

    @Override
    public void socialBind(Long userId, SysAuthSocialBindReqVO reqVO) {
        // ?????? code ????????????????????????
        AuthUser authUser = socialService.getAuthUser(reqVO.getType(), reqVO.getCode(), reqVO.getState());
        Assert.notNull(authUser, "?????????????????????");

        // ??????????????????????????????
        socialService.bindSocialUser(userId, reqVO.getType(), authUser, USER_TYPE_ENUM);
    }

    @Override
    public void logout(String token) {
        // ??????????????????
        LoginUser loginUser = userSessionCoreService.getLoginUser(token);
        if (loginUser == null) {
            return;
        }
        // ?????? session
        userSessionCoreService.deleteUserSession(token);
        // ??????????????????
        this.createLogoutLog(loginUser.getId(), loginUser.getUsername());
    }

    private void createLogoutLog(Long userId, String username) {
        SysLoginLogCreateReqDTO reqDTO = new SysLoginLogCreateReqDTO();
        reqDTO.setLogType(SysLoginLogTypeEnum.LOGOUT_SELF.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(USER_TYPE_ENUM.getValue());
        reqDTO.setUsername(username);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(SysLoginResultEnum.SUCCESS.getResult());
        loginLogCoreService.createLoginLog(reqDTO);
    }

    @Override
    public LoginUser verifyTokenAndRefresh(String token) {
        // ?????? LoginUser
        LoginUser loginUser = userSessionCoreService.getLoginUser(token);
        if (loginUser == null) {
            return null;
        }
        // ?????? LoginUser ??????
        return this.refreshLoginUserCache(token, loginUser);
    }

    private LoginUser refreshLoginUserCache(String token, LoginUser loginUser) {
        // ??? 1/3 ??? Session ????????????????????? LoginUser ??????
        if (System.currentTimeMillis() - loginUser.getUpdateTime().getTime() <
                userSessionCoreService.getSessionTimeoutMillis() / 3) {
            return loginUser;
        }

        // ???????????? SysUserDO ??????
        SysUserDO user = userCoreService.getUser(loginUser.getId());
        if (user == null || CommonStatusEnum.DISABLE.getStatus().equals(user.getStatus())) {
            throw exception(AUTH_TOKEN_EXPIRED); // ?????? token ????????????????????????????????????????????? token ??????????????????????????????????????????
        }

        // ?????? LoginUser ??????
        LoginUser newLoginUser= this.buildLoginUser(user);
        userSessionCoreService.refreshUserSession(token, newLoginUser);
        return newLoginUser;
    }

    private LoginUser buildLoginUser(SysUserDO user) {
        LoginUser loginUser = SysAuthConvert.INSTANCE.convert(user);
        // ????????????
        loginUser.setDeptId(user.getDeptId());
        loginUser.setRoleIds(this.getUserRoleIds(loginUser.getId()));
        loginUser.setGroups(this.getUserPosts(user.getPostIds()));
        return loginUser;
    }

}
