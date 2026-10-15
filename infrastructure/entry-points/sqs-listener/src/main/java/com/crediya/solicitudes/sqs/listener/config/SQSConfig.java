package com.crediya.solicitudes.sqs.listener.config;

import com.crediya.solicitudes.sqs.listener.helper.SQSListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.function.Function;

@Configuration
public class SQSConfig {

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "entrypoint.sqs", name = "enabled", havingValue = "true")
    public SQSListener sqsListener(SqsAsyncClient client, SQSProperties properties, Function<Message, Mono<Void>> fn) {
        return SQSListener.builder()
                .client(client)
                .properties(properties)
                .processor(fn)
                .build()
                .start();
    }
}
