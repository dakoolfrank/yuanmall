package cn.iocoder.yudao.module.product.service.category;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

class ProductCategoryFlatTest extends BaseMockitoUnitTest {
    @InjectMocks private ProductCategoryServiceImpl service;
    @Mock private ProductCategoryMapper mapper;
    @Test void categoryImageIsOptional() {
        ProductCategorySaveReqVO req = new ProductCategorySaveReqVO().setName("零食饮品").setParentId(0L).setStatus(0).setSort(0);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(req).isEmpty());
        }
    }
    @Test void firstLevelLeafCanBindProduct() {
        ProductCategoryDO category = new ProductCategoryDO().setId(5L).setParentId(0L).setName("零食饮品").setStatus(0);
        when(mapper.selectByIds(Collections.singleton(5L))).thenReturn(Collections.singletonList(category));
        when(mapper.selectCountByParentId(5L)).thenReturn(0L);
        assertDoesNotThrow(() -> service.validateCategoryList(Collections.singleton(5L)));
    }
    @Test void parentWithChildrenCannotBindProduct() {
        ProductCategoryDO category = new ProductCategoryDO().setId(5L).setParentId(0L).setName("历史分类").setStatus(0);
        when(mapper.selectByIds(Collections.singleton(5L))).thenReturn(Collections.singletonList(category));
        when(mapper.selectCountByParentId(5L)).thenReturn(2L);
        assertServiceException(() -> service.validateCategoryList(Collections.singleton(5L)), SPU_SAVE_FAIL_CATEGORY_LEVEL_ERROR);
    }
    @Test void disabledLeafIsStillRejected() {
        ProductCategoryDO category = new ProductCategoryDO().setId(5L).setParentId(0L).setName("停用类别").setStatus(1);
        when(mapper.selectByIds(Collections.singleton(5L))).thenReturn(Collections.singletonList(category));
        assertServiceException(() -> service.validateCategoryList(Collections.singleton(5L)), CATEGORY_DISABLED, "停用类别");
    }
}
