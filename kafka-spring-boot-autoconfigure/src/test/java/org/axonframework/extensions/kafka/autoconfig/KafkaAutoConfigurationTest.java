/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.kafka.autoconfig;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.extensions.kafka.KafkaProperties;
import org.axonframework.extensions.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.extensions.kafka.eventhandling.consumer.ConsumerFactory;
import org.axonframework.extensions.kafka.eventhandling.consumer.DefaultConsumerFactory;
import org.axonframework.extensions.kafka.eventhandling.consumer.Fetcher;
import org.axonframework.extensions.kafka.eventhandling.consumer.KafkaMessageSource;
import org.axonframework.extensions.kafka.eventhandling.producer.ConfirmationMode;
import org.axonframework.extensions.kafka.eventhandling.producer.DefaultProducerFactory;
import org.axonframework.extensions.kafka.eventhandling.producer.KafkaEventPublisher;
import org.axonframework.extensions.kafka.eventhandling.producer.KafkaPublisher;
import org.axonframework.extensions.kafka.eventhandling.producer.ProducerFactory;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.junit.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.extensions.kafka.eventhandling.producer.KafkaEventPublisher.DEFAULT_PROCESSING_GROUP;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link KafkaAutoConfiguration}, verifying the minimal set of requirements and full fledged adjustments
 * from a producer and a consumer perspective.
 *
 * @author Nakul Mishra
 * @author Steven van Beelen
 */
public class KafkaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class));

    @Test
    public void testAutoConfigurationWithMinimalRequiredProperties() {
        this.contextRunner.withUserConfiguration(TestConfiguration.class)
                          .withPropertyValues(
                                  "axon.kafka.default-topic=testTopic",
                                  "axon.kafka.producer.transaction-id-prefix=foo",
                                  "axon.kafka.consumer.group-id=bar"
                          ).run(context -> {
            // Required bean assertions
            assertThat(context.getBeanNamesForType(ProducerFactory.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(ConsumerFactory.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(KafkaPublisher.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(Fetcher.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(KafkaMessageSource.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(KafkaMessageConverter.class)).hasSize(1);

            // Producer assertions
            DefaultProducerFactory producerFactory =
                    ((DefaultProducerFactory<?, ?>) context.getBean(DefaultProducerFactory.class));
            Map<String, Object> producerConfigs =
                    ((DefaultProducerFactory<?, ?>) context.getBean(DefaultProducerFactory.class))
                            .configurationProperties();
            KafkaProperties kafkaProperties = context.getBean(KafkaProperties.class);

            assertThat(producerFactory.confirmationMode()).isEqualTo(ConfirmationMode.TRANSACTIONAL);
            assertThat(producerFactory.transactionIdPrefix()).isEqualTo("foo");
            assertThat(producerConfigs.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                    .isEqualTo(StringSerializer.class);
            assertThat(producerConfigs.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
                    .isEqualTo(ByteArraySerializer.class);
            assertThat(kafkaProperties.getEventProcessorMode())
                    .isEqualTo(KafkaProperties.EventProcessorMode.SUBSCRIBING);

            // Consumer assertions
            Map<String, Object> consumerConfigs =
                    ((DefaultConsumerFactory<?, ?>) context.getBean(DefaultConsumerFactory.class))
                            .configurationProperties();

            assertThat(consumerConfigs.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
                    .isEqualTo(Collections.singletonList("localhost:9092"));
            assertThat(consumerConfigs.get(ConsumerConfig.CLIENT_ID_CONFIG)).isNull();
            assertThat(consumerConfigs.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isNull();
            assertThat(consumerConfigs.get(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG)).isNull();
            assertThat(consumerConfigs.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isNull();
            assertThat(consumerConfigs.get(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG)).isNull();
            assertThat(consumerConfigs.get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG)).isNull();
            assertThat(consumerConfigs.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isNull();
            assertThat(consumerConfigs.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("bar");
            assertThat(consumerConfigs.get(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG)).isNull();
            assertThat(consumerConfigs.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG))
                    .isEqualTo(StringDeserializer.class);
            assertThat(consumerConfigs.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                    .isEqualTo(ByteArrayDeserializer.class);
        });
    }

    @Test
    public void testConsumerPropertiesAreAdjustedAsExpected() {
        this.contextRunner.withUserConfiguration(TestConfiguration.class)
                          .withPropertyValues(
                                  "axon.kafka.default-topic=testTopic",
                                  "axon.kafka.consumer.group-id=bar",
                                  // Overrides 'axon.kafka.bootstrap-servers'
                                  "axon.kafka.bootstrap-servers=foo:1234",
                                  "axon.kafka.properties.foo=bar",
                                  "axon.kafka.default-topic=testTopic",
                                  "axon.kafka.properties.baz=qux",
                                  "axon.kafka.properties.foo.bar.baz=qux.fiz.buz",
                                  "axon.kafka.ssl.key-password=p1",
                                  "axon.kafka.ssl.keystore-location=classpath:ksLoc",
                                  "axon.kafka.ssl.keystore-password=p2",
                                  "axon.kafka.ssl.truststore-location=classpath:tsLoc",
                                  "axon.kafka.ssl.truststore-password=p3",
                                  "axon.kafka.consumer.auto-commit-interval=123",
                                  "axon.kafka.consumer.max-poll-records=42",
                                  "axon.kafka.consumer.auto-offset-reset=earliest",
                                  "axon.kafka.consumer.client-id=some-client-id",
                                  "axon.kafka.consumer.enable-auto-commit=false",
                                  "axon.kafka.consumer.fetch-max-wait=456",
                                  "axon.kafka.consumer.properties.fiz.buz=fix.fox",
                                  "axon.kafka.consumer.fetch-min-size=789",
                                  "axon.kafka.consumer.group-id=bar",
                                  "axon.kafka.consumer.heartbeat-interval=234",
                                  "axon.kafka.consumer.key-deserializer = org.apache.kafka.common.serialization.LongDeserializer",
                                  "axon.kafka.consumer.value-deserializer = org.apache.kafka.common.serialization.IntegerDeserializer"
                          ).run(context -> {
            // Required bean assertions
            assertThat(context.getBeanNamesForType(ConsumerFactory.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(Fetcher.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(KafkaMessageSource.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(KafkaMessageConverter.class)).hasSize(1);

            // Consumer assertions
            DefaultConsumerFactory<?, ?> consumerFactory = context.getBean(DefaultConsumerFactory.class);
            Map<String, Object> configs = consumerFactory.configurationProperties();

            assertThat(configs.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
                    .isEqualTo(Collections.singletonList("foo:1234")); // Assert override
            assertThat(configs.get(SslConfigs.SSL_KEY_PASSWORD_CONFIG)).isEqualTo("p1");
            assertThat((String) configs.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG))
                    .endsWith(File.separator + "ksLoc");
            assertThat(configs.get(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG)).isEqualTo("p2");
            assertThat((String) configs.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG))
                    .endsWith(File.separator + "tsLoc");
            assertThat(configs.get(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG)).isEqualTo("p3");
            assertThat(configs.get(ConsumerConfig.CLIENT_ID_CONFIG)).isEqualTo("some-client-id");
            assertThat(configs.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(Boolean.FALSE);
            assertThat(configs.get(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG)).isEqualTo(123);
            assertThat(configs.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
            assertThat(configs.get(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG)).isEqualTo(456);
            assertThat(configs.get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG)).isEqualTo(789);
            assertThat(configs.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("bar");
            assertThat(configs.get(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG)).isEqualTo(234);
            assertThat(configs.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)).isEqualTo(LongDeserializer.class);
            assertThat(configs.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                    .isEqualTo(IntegerDeserializer.class);
            assertThat(configs.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo(42);
            assertThat(configs.get("foo")).isEqualTo("bar");
            assertThat(configs.get("baz")).isEqualTo("qux");
            assertThat(configs.get("foo.bar.baz")).isEqualTo("qux.fiz.buz");
            assertThat(configs.get("fiz.buz")).isEqualTo("fix.fox");
        });
    }

    @Test
    public void testProducerPropertiesAreAdjustedAsExpected() {
        this.contextRunner.withUserConfiguration(TestConfiguration.class)
                          .withPropertyValues(
                                  "axon.kafka.clientId=cid",
                                  "axon.kafka.default-topic=testTopic",
                                  "axon.kafka.producer.transaction-id-prefix=foo",
                                  "axon.kafka.properties.foo.bar.baz=qux.fiz.buz",
                                  "axon.kafka.producer.acks=all",
                                  "axon.kafka.producer.batch-size=20",
                                  // Overrides "axon.kafka.producer.bootstrap-servers"
                                  "axon.kafka.producer.bootstrap-servers=bar:1234",
                                  "axon.kafka.producer.buffer-memory=12345",
                                  "axon.kafka.producer.compression-type=gzip",
                                  "axon.kafka.producer.key-serializer=org.apache.kafka.common.serialization.LongSerializer",
                                  "axon.kafka.producer.retries=2",
                                  "axon.kafka.producer.properties.fiz.buz=fix.fox",
                                  "axon.kafka.producer.ssl.key-password=p4",
                                  "axon.kafka.producer.ssl.keystore-location=classpath:ksLocP",
                                  "axon.kafka.producer.ssl.keystore-password=p5",
                                  "axon.kafka.producer.ssl.truststore-location=classpath:tsLocP",
                                  "axon.kafka.producer.ssl.truststore-password=p6",
                                  "axon.kafka.producer.value-serializer=org.apache.kafka.common.serialization.IntegerSerializer"
                          ).run(context -> {
            DefaultProducerFactory<?, ?> producerFactory = context.getBean(DefaultProducerFactory.class);
            Map<String, Object> configs = producerFactory.configurationProperties();

            // Producer assertions
            assertThat(configs.get(ProducerConfig.CLIENT_ID_CONFIG)).isEqualTo("cid");
            assertThat(configs.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
            assertThat(configs.get(ProducerConfig.BATCH_SIZE_CONFIG)).isEqualTo(20);
            assertThat(configs.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                    .isEqualTo(Collections.singletonList("bar:1234")); // Assert override
            assertThat(configs.get(ProducerConfig.BUFFER_MEMORY_CONFIG)).isEqualTo(12345L);
            assertThat(configs.get(ProducerConfig.COMPRESSION_TYPE_CONFIG)).isEqualTo("gzip");
            assertThat(configs.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(LongSerializer.class);
            assertThat(configs.get(SslConfigs.SSL_KEY_PASSWORD_CONFIG)).isEqualTo("p4");
            assertThat((String) configs.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG))
                    .endsWith(File.separator + "ksLocP");
            assertThat(configs.get(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG)).isEqualTo("p5");
            assertThat((String) configs.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG))
                    .endsWith(File.separator + "tsLocP");
            assertThat(configs.get(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG)).isEqualTo("p6");
            assertThat(configs.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(2);
            assertThat(configs.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(IntegerSerializer.class);
            assertThat(configs.get("foo.bar.baz")).isEqualTo("qux.fiz.buz");
            assertThat(configs.get("fiz.buz")).isEqualTo("fix.fox");
        });
    }

    @Test
    public void testKafkaPropertiesTrackingMode() {
        this.contextRunner.withUserConfiguration(TestConfiguration.class)
                          .withPropertyValues(
                                  // Minimal Required Properties
                                  "axon.kafka.default-topic=testTopic",
                                  "axon.kafka.producer.transaction-id-prefix=foo",
                                  "axon.kafka.consumer.group-id=bar",
                                  // Event Handling Mode
                                  "axon.kafka.event-processor-mode=TRACKING"
                          ).run(context -> {
            KafkaProperties kafkaProperties = context.getBean(KafkaProperties.class);
            assertThat(kafkaProperties.getEventProcessorMode()).isEqualTo(KafkaProperties.EventProcessorMode.TRACKING);

            KafkaEventPublisher kafkaEventPublisher = context.getBean(KafkaEventPublisher.class);
            assertThat(kafkaEventPublisher).isNotNull();

            EventProcessingConfigurer eventProcessingConfigurer = context.getBean(EventProcessingConfigurer.class);
            verify(eventProcessingConfigurer).registerEventHandler(any());
            verify(eventProcessingConfigurer)
                    .registerListenerInvocationErrorHandler(eq(DEFAULT_PROCESSING_GROUP), any());
            verify(eventProcessingConfigurer).assignHandlerInstancesMatching(eq(DEFAULT_PROCESSING_GROUP), any());
            verify(eventProcessingConfigurer).registerTrackingEventProcessor(DEFAULT_PROCESSING_GROUP);
        });
    }

    @Test
    public void testKafkaPropertiesSubscribingMode() {
        this.contextRunner.withUserConfiguration(TestConfiguration.class)
                          .withPropertyValues(
                                  // Minimal Required Properties
                                  "axon.kafka.default-topic=testTopic",
                                  "axon.kafka.producer.transaction-id-prefix=foo",
                                  "axon.kafka.consumer.group-id=bar",
                                  // Event Handling Mode
                                  "axon.kafka.event-processor-mode=SUBSCRIBING"
                          ).run(context -> {
            KafkaProperties kafkaProperties = context.getBean(KafkaProperties.class);
            assertThat(kafkaProperties.getEventProcessorMode())
                    .isEqualTo(KafkaProperties.EventProcessorMode.SUBSCRIBING);

            KafkaEventPublisher kafkaEventPublisher = context.getBean(KafkaEventPublisher.class);
            assertThat(kafkaEventPublisher).isNotNull();

            EventProcessingConfigurer eventProcessingConfigurer = context.getBean(EventProcessingConfigurer.class);
            verify(eventProcessingConfigurer).registerEventHandler(any());
            verify(eventProcessingConfigurer)
                    .registerListenerInvocationErrorHandler(eq(DEFAULT_PROCESSING_GROUP), any());
            verify(eventProcessingConfigurer).assignHandlerInstancesMatching(eq(DEFAULT_PROCESSING_GROUP), any());
            verify(eventProcessingConfigurer).registerSubscribingEventProcessor(DEFAULT_PROCESSING_GROUP);
        });
    }

    @Configuration
    protected static class TestConfiguration {

        @Bean
        public Serializer eventSerializer() {
            return XStreamSerializer.builder().build();
        }

        @Bean
        public EventBus eventBus() {
            return mock(EventBus.class);
        }

        @SuppressWarnings("unchecked")
        @Bean
        public AxonConfiguration axonConfiguration() {
            AxonConfiguration mock = mock(AxonConfiguration.class);
            when(mock.messageMonitor(any(), any())).thenReturn((MessageMonitor) NoOpMessageMonitor.instance());
            return mock;
        }

        @Bean
        public EventProcessingConfigurer eventProcessingConfigurer() {
            return spy(new EventProcessingModule());
        }
    }
}
