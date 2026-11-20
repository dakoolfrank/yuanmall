package cn.iocoder.yudao.module.trade.controller.app.delivery;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.service.delivery.StoreDeliveryRangeService;
import cn.iocoder.yudao.module.trade.service.delivery.bo.StoreDeliveryRangeCheckBO;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/trade/delivery/store-range")
public class AppStoreDeliveryRangeController {
    @Resource private StoreDeliveryRangeService rangeService;
    @GetMapping
    public CommonResult<StoreDeliveryRangeCheckBO> check(@RequestParam(required = false) Long addressId) {
        return success(rangeService.check(getLoginUserId(), addressId));
    }
}
