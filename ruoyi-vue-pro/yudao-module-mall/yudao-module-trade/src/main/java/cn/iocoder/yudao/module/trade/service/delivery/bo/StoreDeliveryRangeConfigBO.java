package cn.iocoder.yudao.module.trade.service.delivery.bo;

import lombok.Data;

@Data
public class StoreDeliveryRangeConfigBO {
    private Long pickUpStoreId;
    private String storeName;
    private Integer radiusMeters;
    private Double centerLatitude;
    private Double centerLongitude;
    /** 全局店铺配送已开放且中心有效。 */
    private Boolean enabled;
}
