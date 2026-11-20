package cn.iocoder.yudao.module.trade.controller.admin.delivery;

import cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.pickup.DeliveryPickUpStoreCreateReqVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPickUpStoreDraftTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    @AfterAll static void closeFactory() { FACTORY.close(); }

    private DeliveryPickUpStoreCreateReqVO draft() {
        DeliveryPickUpStoreCreateReqVO request = new DeliveryPickUpStoreCreateReqVO();
        request.setName("测试自提点"); request.setAreaId(110113);
        request.setDetailAddress("单元测试地址"); request.setLogo("https://example.test/logo.png");
        request.setStatus(1); request.setPhone("");
        return request;
    }
    @Test void disabledDraftAcceptsMissingContactHoursAndLocation() {
        assertTrue(FACTORY.getValidator().validate(draft()).isEmpty());
    }
    @Test void enablingIncompleteDraftIsRejected() {
        DeliveryPickUpStoreCreateReqVO request = draft(); request.setStatus(0);
        assertTrue(FACTORY.getValidator().validate(request).stream()
                .anyMatch(v -> "readyToEnable".equals(v.getPropertyPath().toString())));
    }
    @Test void completedStoreCanBeEnabled() {
        DeliveryPickUpStoreCreateReqVO request = draft(); request.setStatus(0);
        request.setPhone("13800138000"); request.setOpeningTime(LocalTime.of(9, 0));
        request.setClosingTime(LocalTime.of(18, 0)); request.setLatitude(39.9); request.setLongitude(116.6);
        assertTrue(FACTORY.getValidator().validate(request).isEmpty());
    }
    @Test void draftDoesNotAcceptMalformedPhoneOrOutOfRangeCoordinates() {
        DeliveryPickUpStoreCreateReqVO request = draft(); request.setPhone("invalid");
        assertFalse(FACTORY.getValidator().validate(request).isEmpty());
        request.setPhone(""); request.setLatitude(91.0);
        assertFalse(FACTORY.getValidator().validate(request).isEmpty());
        request.setLatitude(null); request.setLongitude(-181.0);
        assertFalse(FACTORY.getValidator().validate(request).isEmpty());
    }
}
