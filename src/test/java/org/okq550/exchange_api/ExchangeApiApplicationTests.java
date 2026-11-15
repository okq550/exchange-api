package org.okq550.exchange_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExchangeApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
