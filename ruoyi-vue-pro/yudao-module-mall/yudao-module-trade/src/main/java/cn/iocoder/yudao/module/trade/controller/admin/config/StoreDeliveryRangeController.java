package cn.iocoder.yudao.module.trade.controller.admin.config;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.admin.config.vo.StoreDeliveryRangeSaveReqVO;
import cn.iocoder.yudao.module.trade.service.delivery.StoreDeliveryRangeService;
import cn.iocoder.yudao.module.trade.service.delivery.bo.StoreDeliveryRangeConfigBO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.validation.Valid;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/trade/config/store-delivery-range")
public class StoreDeliveryRangeController {
    @Resource private StoreDeliveryRangeService rangeService;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('trade:config:query')")
    public CommonResult<StoreDeliveryRangeConfigBO> get() { return success(rangeService.getConfig()); }
    @PutMapping
    @PreAuthorize("@ss.hasPermission('trade:config:save')")
    public CommonResult<Boolean> save(@Valid @RequestBody StoreDeliveryRangeSaveReqVO request) {
        rangeService.saveConfig(request);
        return success(true);
    }
}
