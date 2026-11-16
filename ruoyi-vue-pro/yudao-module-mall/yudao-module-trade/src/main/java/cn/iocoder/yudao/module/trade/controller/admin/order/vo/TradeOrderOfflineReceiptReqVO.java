package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class TradeOrderOfflineReceiptReqVO {
    @NotNull
    private Long id;
    /** 应收金额（分），管理员必须核对，后端与订单精确比较。 */
    @NotNull @Min(1)
    private Integer amount;
    @NotBlank @Pattern(regexp = "cash|scan", message = "请选择现金或现场扫码")
    private String method;
}
