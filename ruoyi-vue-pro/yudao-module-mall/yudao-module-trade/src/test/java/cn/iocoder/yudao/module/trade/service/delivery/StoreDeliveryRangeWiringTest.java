package cn.iocoder.yudao.module.trade.service.delivery;

import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.trade.dal.mysql.config.TradeConfigMapper;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StoreDeliveryRangeWiringTest {
    @Test void infrastructureConfigMapperCannotShadowTradeMapper() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("configMapper", Object.class, Object::new);
            context.registerBean("tradeConfigMapper", TradeConfigMapper.class, () -> mock(TradeConfigMapper.class));
            context.registerBean("tradeConfigService", TradeConfigService.class, () -> mock(TradeConfigService.class));
            context.registerBean("deliveryPickUpStoreService", DeliveryPickUpStoreService.class, () -> mock(DeliveryPickUpStoreService.class));
            context.registerBean("addressApi", MemberAddressApi.class, () -> mock(MemberAddressApi.class));
            context.registerBean("orderProperties", TradeOrderProperties.class, TradeOrderProperties::new);
            context.register(StoreDeliveryRangeService.class);
            context.refresh();
            assertFalse(context.getBean(StoreDeliveryRangeService.class).getConfig().getEnabled());
        }
    }
}
