package com.example.javadoc.module.order.service;

import com.example.javadoc.module.order.dto.request.DeleteOrderRequest;
import com.example.javadoc.module.order.mapper.OrderMapper;
import com.example.javadoc.module.order.repository.OrderRepository;
import com.example.javadoc.module.order.repository.OrderWriteRepository;
import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceConsistencyTests {

    @Test
    void doesNotReportSuccessWhenTheOrderDoesNotExist() {
        OrderWriteRepository writes = mock(OrderWriteRepository.class);
        when(writes.deleteById("missing")).thenReturn(DeleteResult.acknowledged(0));
        OrderService service = new OrderService(mock(OrderRepository.class), writes, mock(OrderMapper.class));

        assertThatIllegalArgumentException().isThrownBy(() -> service.deleteOrder(new DeleteOrderRequest("missing")))
                .withMessage("订单不存在");
    }
}
