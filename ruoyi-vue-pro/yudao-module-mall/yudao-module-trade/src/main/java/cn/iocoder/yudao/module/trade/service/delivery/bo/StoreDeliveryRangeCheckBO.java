package cn.iocoder.yudao.module.trade.service.delivery.bo;

import lombok.Data;

@Data
public class StoreDeliveryRangeCheckBO {
    private Boolean available;
    private Long pickUpStoreId;
    private Integer radiusMeters;
    /** 向上取整的直线距离，单位米；资格使用未取整的距离判定。 */
    private Integer distanceMeters;
    private Integer reasonCode;
    private String reason;
}
