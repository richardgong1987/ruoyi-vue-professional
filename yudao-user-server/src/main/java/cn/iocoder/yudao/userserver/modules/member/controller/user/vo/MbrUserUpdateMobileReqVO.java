package cn.iocoder.yudao.userserver.modules.member.controller.user.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

@ApiModel("修改手机 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MbrUserUpdateMobileReqVO {

    @ApiModelProperty(value = "手机验证码", required = true, example = "1024")
    @NotEmpty(message = "手机验证码不能为空")
    @Length(min = 4, max = 6, message = "手机验证码长度为 4-6 位")
    @Pattern(regexp = "^[0-9]+$", message = "手机验证码必须都是数字")
    private String code;

    @ApiModelProperty(value = "手机号",required = true,example = "15823654487")
    @NotBlank(message = "手机号不能为空")
    // TODO @宋天：手机校验，直接使用 @Mobile 哈
    @Length(min = 8, max = 11, message = "手机号码长度为 8-11 位")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String mobile;

}
