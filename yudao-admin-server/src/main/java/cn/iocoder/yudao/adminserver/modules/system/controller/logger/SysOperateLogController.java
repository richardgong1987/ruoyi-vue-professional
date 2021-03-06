package cn.iocoder.yudao.adminserver.modules.system.controller.logger;

import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.operatelog.SysOperateLogExcelVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.operatelog.SysOperateLogExportReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.operatelog.SysOperateLogPageReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.operatelog.SysOperateLogRespVO;
import cn.iocoder.yudao.adminserver.modules.system.convert.logger.SysOperateLogConvert;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.logger.SysOperateLogDO;
import cn.iocoder.yudao.adminserver.modules.system.service.logger.SysOperateLogService;
import cn.iocoder.yudao.adminserver.modules.system.service.user.SysUserService;
import cn.iocoder.yudao.coreservice.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.operatelog.core.annotations.OperateLog;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.operatelog.core.enums.OperateTypeEnum.EXPORT;

@Api(tags = "????????????")
@RestController
@RequestMapping("/system/operate-log")
@Validated
public class SysOperateLogController {

    @Resource
    private SysOperateLogService operateLogService;
    @Resource
    private SysUserService userService;

    @GetMapping("/page")
    @ApiOperation("??????????????????????????????")
    @PreAuthorize("@ss.hasPermission('system:operate-log:query')")
    public CommonResult<PageResult<SysOperateLogRespVO>> pageOperateLog(@Valid SysOperateLogPageReqVO reqVO) {
        PageResult<SysOperateLogDO> pageResult = operateLogService.getOperateLogPage(reqVO);

        // ???????????????????????????
        Collection<Long> userIds = CollectionUtils.convertList(pageResult.getList(), SysOperateLogDO::getUserId);
        Map<Long, SysUserDO> userMap = userService.getUserMap(userIds);
        // ????????????
        List<SysOperateLogRespVO> list = new ArrayList<>(pageResult.getList().size());
        pageResult.getList().forEach(operateLog -> {
            SysOperateLogRespVO respVO = SysOperateLogConvert.INSTANCE.convert(operateLog);
            list.add(respVO);
            // ??????????????????
            MapUtils.findAndThen(userMap, operateLog.getUserId(), user -> respVO.setUserNickname(user.getNickname()));
        });
        return success(new PageResult<>(list, pageResult.getTotal()));
    }

    @ApiOperation("??????????????????")
    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermission('system:operate-log:export')")
    @OperateLog(type = EXPORT)
    public void exportOperateLog(HttpServletResponse response, @Valid SysOperateLogExportReqVO reqVO) throws IOException {
        List<SysOperateLogDO> list = operateLogService.getOperateLogs(reqVO);

        // ???????????????????????????
        Collection<Long> userIds = CollectionUtils.convertList(list, SysOperateLogDO::getUserId);
        Map<Long, SysUserDO> userMap = userService.getUserMap(userIds);
        // ????????????
        List<SysOperateLogExcelVO> excelDataList = SysOperateLogConvert.INSTANCE.convertList(list, userMap);
        // ??????
        ExcelUtils.write(response, "????????????.xls", "????????????", SysOperateLogExcelVO.class, excelDataList);
    }

}
