package cn.iocoder.yudao.module.trade.dal.dataobject.delivery;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
@TableName("trade_delivery_wechat_config")
public class DeliveryWechatConfigDO extends BaseDO {
    private Long id;
    private String senderName;
    private String senderMobile;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String senderAddress;
    private String deliveryId;
    private String bizId;
    private Integer serviceType;
}
