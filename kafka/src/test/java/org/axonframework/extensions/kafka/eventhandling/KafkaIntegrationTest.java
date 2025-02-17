/*
 * Copyright (c) 2010-2018. Axon Framework
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
package org.axonframework.extensions.kafka.eventhandling;

import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.extensions.kafka.eventhandling.consumer.AsyncFetcher;
import org.axonframework.extensions.kafka.eventhandling.consumer.ConsumerFactory;
import org.axonframework.extensions.kafka.eventhandling.consumer.DefaultConsumerFactory;
import org.axonframework.extensions.kafka.eventhandling.consumer.Fetcher;
import org.axonframework.extensions.kafka.eventhandling.consumer.KafkaMessageSource;
import org.axonframework.extensions.kafka.eventhandling.producer.KafkaPublisher;
import org.axonframework.extensions.kafka.eventhandling.producer.KafkaEventPublisher;
import org.axonframework.extensions.kafka.eventhandling.producer.ProducerFactory;
import org.axonframework.extensions.kafka.eventhandling.util.ProducerConfigUtil;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.extensions.kafka.eventhandling.util.ConsumerConfigUtil.minimal;
import static org.junit.Assert.*;

/**
 * Kafka Integration tests asserting a message can be published through a Producer on a Kafka topic and received through
 * a Consumer.
 *
 * @author Nakul Mishra
 * @author Steven van Beelen
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@EmbeddedKafka(topics = {"integration"}, partitions = 5, controlledShutdown = true)
public class KafkaIntegrationTest {

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private EmbeddedKafkaBroker kafkaBroker;

    private Configurer configurer = DefaultConfigurer.defaultConfiguration();
    private EventBus eventBus;
    private ProducerFactory<String, byte[]> producerFactory;
    private KafkaPublisher<String, byte[]> publisher;
    private Fetcher fetcher;

    @Before
    public void setupComponents() {
        producerFactory = ProducerConfigUtil.ackProducerFactory(kafkaBroker, ByteArraySerializer.class);
        publisher = KafkaPublisher.<String, byte[]>builder()
                .producerFactory(producerFactory)
                .topic("integration")
                .build();
        KafkaEventPublisher sender = KafkaEventPublisher.<String, byte[]>builder().kafkaPublisher(publisher).build();
        configurer.eventProcessing(
                eventProcessingConfigurer -> eventProcessingConfigurer.registerEventHandler(c -> sender)
        );

        ConsumerFactory<String, byte[]> consumerFactory =
                new DefaultConsumerFactory<>(minimal(kafkaBroker, "consumer1", ByteArrayDeserializer.class));

        fetcher = AsyncFetcher.<String, byte[]>builder()
                .consumerFactory(consumerFactory)
                .topic("integration")
                .pollTimeout(300)
                .build();

        eventBus = SimpleEventBus.builder().build();
        configurer.configureEventBus(configuration -> eventBus);

        configurer.start();
    }

    @After
    public void shutdown() {
        producerFactory.shutDown();
        fetcher.shutdown();
        publisher.shutDown();
    }

    @Test
    public void testPublishAndReadMessages() throws Exception {
        KafkaMessageSource messageSource = new KafkaMessageSource(fetcher);
        BlockingStream<TrackedEventMessage<?>> stream1 = messageSource.openStream(null);
        stream1.close();
        BlockingStream<TrackedEventMessage<?>> stream2 = messageSource.openStream(null);

        eventBus.publish(asEventMessage("test"));

        // The consumer may need some time to start
        assertTrue(stream2.hasNextAvailable(25, TimeUnit.SECONDS));
        TrackedEventMessage<?> actual = stream2.nextAvailable();
        assertNotNull(actual);

        stream2.close();
    }
}
