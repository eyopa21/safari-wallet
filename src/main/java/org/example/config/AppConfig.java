package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class AppConfig {

    // read timeout must sit above the bank's slow path (6s), so a slow answer is waited for, not retried
    @Bean
    RestClient bankRestClient(RestClient.Builder builder, BankProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    // the only threads that ever wait on the bank; request threads never do
    @Bean
    ThreadPoolTaskExecutor bankExecutor(BankProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.workerThreads());
        executor.setMaxPoolSize(props.workerThreads());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("bank-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
