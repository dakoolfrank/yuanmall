package cn.iocoder.yudao.module.trade.controller.admin.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.*;

@Data
@Schema(description = "店铺配送范围设置；仅更新配送中心及半径")
public class StoreDeliveryRangeSaveReqVO {
    @NotNull @Min(1)
    private Long pickUpStoreId;
    @NotNull @Min(1) @Max(100000)
    @Schema(description = "直线半径，单位米", example = "5000")
    private Integer radiusMeters;
}
