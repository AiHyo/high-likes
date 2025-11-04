package com.aih.highlike.config;

import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.RedeliveryBackoff;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.pulsar.annotation.PulsarListenerConsumerBuilderCustomizer;

import java.util.concurrent.TimeUnit;

/**
 * 批量处理策略配置  
 */  
@Configuration
public class ThumbConsumerConfig<T> implements PulsarListenerConsumerBuilderCustomizer<T> {

    @Override
    public void customize(ConsumerBuilder<T> consumerBuilder) {
        consumerBuilder.batchReceivePolicy(
                BatchReceivePolicy.builder()
                        // 每次处理 1000 条
                        .maxNumMessages(1000)
                        // 需要小于 nack 的最小延迟时间
                        .timeout(100, TimeUnit.MILLISECONDS)
                        .build()
        );
    }

    /**
     * 配置 NACK 重试策略
     * @return RedeliveryBackoff
     */
    @Bean
    public RedeliveryBackoff negativeAckRedeliveryBackoff() {
        return MultiplierRedeliveryBackoff.builder()
                // 初始延迟 1 秒
                .minDelayMs(1_000)
                // 最大延迟 60 秒
                .maxDelayMs(60_000)
                // 每次重试延迟倍数
                .multiplier(2)
                .build();
    }

    /**
     * 配置 ACK 超时重试策略
     * @return RedeliveryBackoff
     */
    @Bean
    public RedeliveryBackoff ackTimeoutRedeliveryBackoff() {
        return MultiplierRedeliveryBackoff.builder()
                // 初始延迟 5 秒
                .minDelayMs(5_000)
                // 最大延迟 300 秒
                .maxDelayMs(300_000)
                // 每次重试延迟倍数
                .multiplier(3)
                .build();
    }

    /**
     * 配置死信队列
     * @return DeadLetterPolicy
     */
    @Bean
    public DeadLetterPolicy deadLetterPolicy() {
        return DeadLetterPolicy.builder()
                // 最大重试次数
                .maxRedeliverCount(3)
                // 死信主题名称
                .deadLetterTopic("thumb-dlq-topic")
                .build();
    }

}
