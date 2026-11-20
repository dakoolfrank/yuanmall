package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressCreateReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.address.MemberAddressDO;
import cn.iocoder.yudao.module.member.dal.mysql.address.MemberAddressMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.ADDRESS_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AddressServiceImpl} 的单元测试类
 *
 * @author 芋道源码
 */
@Import(AddressServiceImpl.class)
public class MemberAddressServiceImplTest extends BaseDbUnitTest {

    @Resource
    private AddressServiceImpl addressService;

    @Resource
    private MemberAddressMapper addressMapper;

    @Test
    void coordinatesPersistAndRoundTrip() {
        AppAddressCreateReqVO request = randomPojo(AppAddressCreateReqVO.class,
                x -> x.setLatitude(40.137651).setLongitude(116.610765));
        Long id = addressService.createAddress(298L, request);
        MemberAddressDO saved = addressService.getAddress(298L, id);
        assertEquals(40.137651, saved.getLatitude());
        assertEquals(116.610765, cn.iocoder.yudao.module.member.convert.address.AddressConvert.INSTANCE
                .convert02(saved).getLongitude());
        assertEquals(40.137651, cn.iocoder.yudao.module.member.convert.address.AddressConvert.INSTANCE
                .convert(saved).getLatitude());
        assertNull(addressService.getAddress(299L, id));
    }

    @Test
    void legacyAddressEditClearsStaleCoordinates() {
        MemberAddressDO original = randomPojo(MemberAddressDO.class,
                x -> x.setUserId(298L).setLatitude(40D).setLongitude(116D));
        addressMapper.insert(original);
        AppAddressUpdateReqVO request = cn.iocoder.yudao.framework.common.util.object.BeanUtils
                .toBean(original, AppAddressUpdateReqVO.class);
        request.setDetailAddress("新的收货地址").setLatitude(null).setLongitude(null);
        addressService.updateAddress(298L, request);
        MemberAddressDO saved = addressService.getAddress(298L, original.getId());
        assertNull(saved.getLatitude());
        assertNull(saved.getLongitude());
    }

    @Test
    void contactOnlyEditKeepsCoordinates() {
        MemberAddressDO original = randomPojo(MemberAddressDO.class,
                x -> x.setUserId(298L).setLatitude(40D).setLongitude(116D));
        addressMapper.insert(original);
        AppAddressUpdateReqVO request = cn.iocoder.yudao.framework.common.util.object.BeanUtils
                .toBean(original, AppAddressUpdateReqVO.class);
        request.setName("新联系人").setLatitude(null).setLongitude(null);
        addressService.updateAddress(298L, request);
        assertEquals(40D, addressService.getAddress(298L, original.getId()).getLatitude());
        assertEquals(116D, addressService.getAddress(298L, original.getId()).getLongitude());
    }

    @Test
    void locationValidationSupportsLegacyButRejectsPartialOrInvalidPoint() {
        try (javax.validation.ValidatorFactory factory = javax.validation.Validation.buildDefaultValidatorFactory()) {
            AppAddressCreateReqVO request = new AppAddressCreateReqVO();
            request.setName("联系人").setMobile("13800000000").setAreaId(110113L)
                    .setDetailAddress("收货地址").setDefaultStatus(false);
            assertTrue(factory.getValidator().validate(request).isEmpty());
            request.setLatitude(40D);
            assertFalse(factory.getValidator().validate(request).isEmpty());
            request.setLongitude(116D);
            assertTrue(factory.getValidator().validate(request).isEmpty());
            request.setLatitude(91D);
            assertFalse(factory.getValidator().validate(request).isEmpty());
            request.setLatitude(Double.NaN);
            assertFalse(factory.getValidator().validate(request).isEmpty());
        }
    }

    @Test
    public void testCreateAddress_success() {
        // 准备参数
        AppAddressCreateReqVO reqVO = randomPojo(AppAddressCreateReqVO.class);

        // 调用
        Long addressId = addressService.createAddress(randomLongId(), reqVO);
        // 断言
        assertNotNull(addressId);
        // 校验记录的属性是否正确
        MemberAddressDO address = addressMapper.selectById(addressId);
        assertPojoEquals(reqVO, address);
    }

    @Test
    public void testUpdateAddress_success() {
        // mock 数据
        MemberAddressDO dbAddress = randomPojo(MemberAddressDO.class);
        addressMapper.insert(dbAddress);// @Sql: 先插入出一条存在的数据
        // 准备参数
        AppAddressUpdateReqVO reqVO = randomPojo(AppAddressUpdateReqVO.class, o -> {
            o.setId(dbAddress.getId()); // 设置更新的 ID
        });

        // 调用
        addressService.updateAddress(dbAddress.getUserId(), reqVO);
        // 校验是否更新正确
        MemberAddressDO address = addressMapper.selectById(reqVO.getId()); // 获取最新的
        assertPojoEquals(reqVO, address);
    }

    @Test
    public void testUpdateAddress_notExists() {
        // 准备参数
        AppAddressUpdateReqVO reqVO = randomPojo(AppAddressUpdateReqVO.class);

        // 调用, 并断言异常
        assertServiceException(() -> addressService.updateAddress(randomLongId(), reqVO), ADDRESS_NOT_EXISTS);
    }

    @Test
    public void testDeleteAddress_success() {
        // mock 数据
        MemberAddressDO dbAddress = randomPojo(MemberAddressDO.class);
        addressMapper.insert(dbAddress);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbAddress.getId();

        // 调用
        addressService.deleteAddress(dbAddress.getUserId(), id);
        // 校验数据不存在了
        assertNull(addressMapper.selectById(id));
    }

    @Test
    public void testDeleteAddress_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用, 并断言异常
        assertServiceException(() -> addressService.deleteAddress(randomLongId(), id), ADDRESS_NOT_EXISTS);
    }

}
