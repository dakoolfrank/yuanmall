package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import lombok.Data;
import javax.validation.constraints.*;

/** 店主已在线下退回全额款项后的登记，不发起支付渠道退款。 */
@Data
public class TradeOrderOfflineRefundReqVO {
    @NotNull
    private Long id;
    @NotNull @Min(1)
    private Integer amount;
    @NotBlank @Pattern(regexp = "cash|scan", message = "请选择现金退回或微信转回")
    private String method;
}
