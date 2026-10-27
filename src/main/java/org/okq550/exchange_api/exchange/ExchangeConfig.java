package org.okq550.exchange_api.exchange;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExchangeConfig {

    // A @Bean method is how you hand Spring an object it can’t create on its own. destroyMethod = "stop" calls
    // exchange.stop() at shutdown, replacing your @PreDestroy
    @Bean(destroyMethod = "stop")
    public Exchange exchange() {
        Exchange exchange = new Exchange();
        exchange.start();
        return exchange;
    }
}