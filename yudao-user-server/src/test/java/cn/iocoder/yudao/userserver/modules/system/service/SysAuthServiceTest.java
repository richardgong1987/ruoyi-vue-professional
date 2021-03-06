package cn.iocoder.yudao.userserver.modules.system.service;

import cn.iocoder.yudao.coreservice.modules.member.dal.dataobject.user.MbrUserDO;
import cn.iocoder.yudao.coreservice.modules.system.service.auth.SysUserSessionCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.logger.SysLoginLogCoreService;
import cn.iocoder.yudao.coreservice.modules.system.service.social.SysSocialCoreService;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.collection.ArrayUtils;
import cn.iocoder.yudao.framework.redis.config.YudaoRedisAutoConfiguration;
import cn.iocoder.yudao.userserver.BaseDbAndRedisUnitTest;
import cn.iocoder.yudao.userserver.modules.member.dal.mysql.user.MbrUserMapper;
import cn.iocoder.yudao.userserver.modules.member.service.user.MbrUserService;
import cn.iocoder.yudao.userserver.modules.system.controller.auth.vo.MbrAuthResetPasswordReqVO;
import cn.iocoder.yudao.userserver.modules.system.controller.auth.vo.MbrAuthUpdatePasswordReqVO;
import cn.iocoder.yudao.userserver.modules.system.service.auth.SysAuthService;
import cn.iocoder.yudao.userserver.modules.system.service.auth.impl.SysAuthServiceImpl;
import cn.iocoder.yudao.userserver.modules.system.service.sms.SysSmsCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.hutool.core.util.RandomUtil.randomNumbers;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

// TODO @?????????????????? review??????????????????????????????
/**
 * {@link SysAuthService} ??????????????????
 *
 * @author ??????
 */
@Import({SysAuthServiceImpl.class, YudaoRedisAutoConfiguration.class})
public class SysAuthServiceTest extends BaseDbAndRedisUnitTest {

    @MockBean
    private AuthenticationManager authenticationManager;
    @MockBean
    private MbrUserService userService;
    @MockBean
    private SysSmsCodeService smsCodeService;
    @MockBean
    private SysLoginLogCoreService loginLogCoreService;
    @MockBean
    private SysUserSessionCoreService userSessionCoreService;
    @MockBean
    private SysSocialCoreService socialService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @Resource
    private MbrUserMapper mbrUserMapper;
    @Resource
    private SysAuthServiceImpl authService;

    @Test
    public void testUpdatePassword_success(){
        // ????????????
        MbrUserDO userDO = randomMbrUserDO();
        mbrUserMapper.insert(userDO);

        // ?????????
        String newPassword = randomString();

        // ????????????
        MbrAuthUpdatePasswordReqVO reqVO = MbrAuthUpdatePasswordReqVO.builder()
                .oldPassword(userDO.getPassword())
                .password(newPassword)
                .build();

        // ?????????
        // ??????????????????????????????ture????????????
        when(passwordEncoder.matches(reqVO.getOldPassword(),reqVO.getOldPassword())).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(newPassword);

        // ??????????????????
        authService.updatePassword(userDO.getId(),reqVO);
        assertEquals(mbrUserMapper.selectById(userDO.getId()).getPassword(),newPassword);
    }

    @Test
    public void testResetPassword_success(){
        // ????????????
        MbrUserDO userDO = randomMbrUserDO();
        mbrUserMapper.insert(userDO);

        // ????????????
        String password = randomNumbers(11);
        // ???????????????
        String code = randomNumbers(4);

        MbrAuthResetPasswordReqVO reqVO = MbrAuthResetPasswordReqVO.builder()
                .password(password)
                .code(code)
                .build();
        // ??????code+?????????
        stringRedisTemplate.opsForValue().set(code,userDO.getMobile(),10, TimeUnit.MINUTES);

        // mock
        when(passwordEncoder.encode(password)).thenReturn(password);

        // ??????????????????
        authService.resetPassword(reqVO);
        assertEquals(mbrUserMapper.selectById(userDO.getId()).getPassword(),password);
    }


    // ========== ???????????? ==========

    @SafeVarargs
    private static MbrUserDO randomMbrUserDO(Consumer<MbrUserDO>... consumers) {
        Consumer<MbrUserDO> consumer = (o) -> {
            o.setStatus(randomEle(CommonStatusEnum.values()).getStatus()); // ?????? status ?????????
            o.setPassword(randomString());
        };
        return randomPojo(MbrUserDO.class, ArrayUtils.append(consumer, consumers));
    }


}
