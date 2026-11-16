package cn.iocoder.yudao.module.trade.controller.admin.delivery;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.social.SocialClientServiceImpl;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryWechatConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.delivery.DeliveryWechatConfigMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import javax.annotation.Resource;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@RestController
@RequestMapping("/trade/delivery/wechat")
public class DeliveryWechatController {
    @Resource private DeliveryWechatConfigMapper deliveryWechatConfigMapper;
    @Resource private SocialClientServiceImpl socialClientService;

    @GetMapping("/config")
    @PreAuthorize("@ss.hasPermission('trade:delivery:express-template:query')")
    public CommonResult<DeliveryWechatConfigDO> getConfig() {
        DeliveryWechatConfigDO config = deliveryWechatConfigMapper.selectById(TenantContextHolder.getRequiredTenantId());
        return success(config == null ? new DeliveryWechatConfigDO() : config);
    }

    @PutMapping("/config")
    @PreAuthorize("@ss.hasPermission('trade:delivery:express-template:update')")
    public CommonResult<Boolean> saveConfig(@RequestBody DeliveryWechatConfigDO body) {
        // 白名单映射，禁止客户端写入审计信息及任意租户/记录。
        DeliveryWechatConfigDO row = new DeliveryWechatConfigDO();
        row.setId(TenantContextHolder.getRequiredTenantId());
        row.setSenderName(limit(body.getSenderName(), 40));
        row.setSenderMobile(limit(body.getSenderMobile(), 30));
        row.setSenderProvince(limit(body.getSenderProvince(), 50));
        row.setSenderCity(limit(body.getSenderCity(), 50));
        row.setSenderDistrict(limit(body.getSenderDistrict(), 50));
        row.setSenderAddress(limit(body.getSenderAddress(), 255));
        row.setDeliveryId(limit(body.getDeliveryId(), 30));
        row.setBizId(limit(body.getBizId(), 100));
        if (body.getServiceType() != null && body.getServiceType() < 0) throw new IllegalArgumentException("服务类型不能为负数");
        row.setServiceType(body.getServiceType());
        if (deliveryWechatConfigMapper.selectById(row.getId()) == null) deliveryWechatConfigMapper.insert(row);
        else deliveryWechatConfigMapper.updateById(row);
        return success(true);
    }
    private String limit(String value, int max) {
        if (value != null && value.length() > max) throw new IllegalArgumentException("设置内容过长");
        return value == null ? "" : value.trim();
    }

    @GetMapping("/accounts")
    @PreAuthorize("@ss.hasPermission('trade:delivery:express-template:query')")
    public CommonResult<List<Map<String,Object>>> getAccounts() {
        try {
            List<Map<String,Object>> result = new ArrayList<>();
            socialClientService.getWxMaService(UserTypeEnum.MEMBER.getValue()).getExpressService().getAllAccount().forEach(account -> {
                Map<String,Object> item = new HashMap<>();
                item.put("deliveryId", account.getDeliveryId());
                item.put("bizId", account.getBizId());
                item.put("alias", account.getAlias());
                item.put("statusCode", account.getStatusCode());
                item.put("serviceTypes", account.getServiceType());
                result.add(item);
            });
            return success(result);
        } catch (Exception ex) {
            // 不将微信请求内容（含 Token/账号）写入响应或日志。
            throw exception(new ErrorCode(1_011_004_090, "无法同步微信物流账号，请核对顾客小程序配置、物流助手开通及快递账号绑定"));
        }
    }
}
