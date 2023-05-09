/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.kafka.utils;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.IntersectionType;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.utils.ValueUtils;
import io.ballerina.runtime.api.utils.XmlUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.stdlib.constraint.Constraints;
import io.ballerina.stdlib.kafka.observability.KafkaMetricsUtil;
import io.ballerina.stdlib.kafka.observability.KafkaObservabilityConstants;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.slf4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static io.ballerina.runtime.api.TypeTags.ANYDATA_TAG;
import static io.ballerina.runtime.api.TypeTags.ARRAY_TAG;
import static io.ballerina.runtime.api.TypeTags.BYTE_TAG;
import static io.ballerina.runtime.api.TypeTags.INTERSECTION_TAG;
import static io.ballerina.runtime.api.TypeTags.RECORD_TYPE_TAG;
import static io.ballerina.runtime.api.TypeTags.STRING_TAG;
import static io.ballerina.runtime.api.TypeTags.UNION_TAG;
import static io.ballerina.runtime.api.TypeTags.XML_TAG;
import static io.ballerina.runtime.api.utils.TypeUtils.getReferredType;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.ADDITIONAL_PROPERTIES_MAP_FIELD;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.CONSUMER_CONFIG_FIELD_NAME;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.CONSUMER_ENABLE_AUTO_COMMIT;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.CONSUMER_ENABLE_AUTO_COMMIT_CONFIG;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.CONSUMER_ENABLE_AUTO_SEEK_CONFIG;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.KAFKA_ERROR;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.KAFKA_RECORD_KEY;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.KAFKA_RECORD_PARTITION_OFFSET;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.KAFKA_RECORD_TIMESTAMP;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.KAFKA_RECORD_VALUE;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.PAYLOAD_BINDING_ERROR;
import static io.ballerina.stdlib.kafka.utils.KafkaConstants.PAYLOAD_VALIDATION_ERROR;
import static io.ballerina.stdlib.kafka.utils.ModuleUtils.getModule;

/**
 * Utility class for Kafka Connector Implementation.
 */
public class KafkaUtils {

    private KafkaUtils() {
    }

    public static Properties processKafkaConsumerConfig(Object bootStrapServers, BMap<BString, Object> configurations) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getServerUrls(bootStrapServers));
        addStringParamIfPresent(ConsumerConfig.GROUP_ID_CONFIG, configurations, properties,
                                KafkaConstants.CONSUMER_GROUP_ID_CONFIG);
        addStringParamIfPresent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, configurations, properties,
                                KafkaConstants.CONSUMER_AUTO_OFFSET_RESET_CONFIG);
        addStringParamIfPresent(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, configurations, properties,
                                KafkaConstants.CONSUMER_PARTITION_ASSIGNMENT_STRATEGY_CONFIG);
        addStringParamIfPresent(ConsumerConfig.METRICS_RECORDING_LEVEL_CONFIG, configurations, properties,
                                KafkaConstants.CONSUMER_METRICS_RECORDING_LEVEL_CONFIG);
        addStringParamIfPresent(ConsumerConfig.METRIC_REPORTER_CLASSES_CONFIG, configurations, properties,
                                KafkaConstants.CONSUMER_METRIC_REPORTER_CLASSES_CONFIG);
        addStringParamIfPresent(ConsumerConfig.CLIENT_ID_CONFIG, configurations, properties,
                                KafkaConstants.CONSUMER_CLIENT_ID_CONFIG);
        addStringParamIfPresent(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, configurations, properties,
                                KafkaConstants.CONSUMER_INTERCEPTOR_CLASSES_CONFIG);
        addStringParamIfPresent(ConsumerConfig.ISOLATION_LEVEL_CONFIG, configurations, properties,
                                KafkaConstants.CONSUMER_ISOLATION_LEVEL_CONFIG);

        addDeserializerConfigs(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, properties);
        addDeserializerConfigs(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, properties);
        // TODO: Disabled as the custom SerDes support is to be revisited and improved.
        //  Fix once the design for that is completed.
//        addCustomDeserializer(KafkaConstants.CONSUMER_KEY_DESERIALIZER_CONFIG,
//                              KafkaConstants.CONSUMER_KEY_DESERIALIZER_TYPE_CONFIG, properties,
//                              configurations);
//        addCustomDeserializer(KafkaConstants.CONSUMER_VALUE_DESERIALIZER_CONFIG,
//                              KafkaConstants.CONSUMER_VALUE_DESERIALIZER_TYPE_CONFIG, properties,
//                              configurations);
        addStringParamIfPresent(KafkaConstants.SCHEMA_REGISTRY_URL, configurations, properties,
                                KafkaConstants.CONSUMER_SCHEMA_REGISTRY_URL);

        addStringOrStringArrayParamIfPresent(KafkaConstants.ALIAS_TOPICS.getValue(), configurations, properties,
                                     KafkaConstants.ALIAS_TOPICS);

        addTimeParamIfPresent(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_SESSION_TIMEOUT_MS_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_HEARTBEAT_INTERVAL_MS_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.METADATA_MAX_AGE_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_METADATA_MAX_AGE_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_AUTO_COMMIT_INTERVAL_MS_CONFIG);
        addIntParamIfPresent(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_MAX_PARTITION_FETCH_BYTES_CONFIG);
        addIntParamIfPresent(ConsumerConfig.SEND_BUFFER_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_SEND_BUFFER_CONFIG);
        addIntParamIfPresent(ConsumerConfig.RECEIVE_BUFFER_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_RECEIVE_BUFFER_CONFIG);
        addIntParamIfPresent(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_FETCH_MIN_BYTES_CONFIG);
        addIntParamIfPresent(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_FETCH_MAX_BYTES_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_FETCH_MAX_WAIT_MS_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_RECONNECT_BACKOFF_MS_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_RETRY_BACKOFF_MS_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_METRICS_SAMPLE_WINDOW_MS_CONFIG);

        addIntParamIfPresent(ConsumerConfig.METRICS_NUM_SAMPLES_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_METRICS_NUM_SAMPLES_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_REQUEST_TIMEOUT_MS_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_CONNECTIONS_MAX_IDLE_MS_CONFIG);
        addIntParamIfPresent(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_MAX_POLL_RECORDS_CONFIG);
        addIntParamIfPresent(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_MAX_POLL_INTERVAL_MS_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_RECONNECT_BACKOFF_MAX_MS_CONFIG);
        addTimeParamIfPresent(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, configurations, properties,
                             KafkaConstants.CONSUMER_DEFAULT_API_TIMEOUT_CONFIG);

        addTimeParamIfPresent(KafkaConstants.ALIAS_POLLING_TIMEOUT.getValue(), configurations, properties,
                             KafkaConstants.ALIAS_POLLING_TIMEOUT);
        addTimeParamIfPresent(KafkaConstants.ALIAS_POLLING_INTERVAL.getValue(), configurations, properties,
                             KafkaConstants.ALIAS_POLLING_INTERVAL);
        addIntParamIfPresent(KafkaConstants.ALIAS_CONCURRENT_CONSUMERS.getValue(), configurations, properties,
                             KafkaConstants.ALIAS_CONCURRENT_CONSUMERS);

        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        addBooleanParamIfPresent(ConsumerConfig.CHECK_CRCS_CONFIG, configurations, properties,
                                 KafkaConstants.CONSUMER_CHECK_CRCS_CONFIG, true);
        addBooleanParamIfPresent(ConsumerConfig.EXCLUDE_INTERNAL_TOPICS_CONFIG, configurations, properties,
                                 KafkaConstants.CONSUMER_EXCLUDE_INTERNAL_TOPICS_CONFIG, true);

        addBooleanParamIfPresent(KafkaConstants.ALIAS_DECOUPLE_PROCESSING.getValue(), configurations, properties,
                                 KafkaConstants.ALIAS_DECOUPLE_PROCESSING, false);
        addStringParamIfPresent(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, configurations,
                properties, KafkaConstants.SECURITY_PROTOCOL_CONFIG);
        if (Objects.nonNull(configurations.get(KafkaConstants.SECURE_SOCKET))) {
            processSslProperties(configurations, properties);
        }

        if (Objects.nonNull(configurations.get(KafkaConstants.AUTHENTICATION_CONFIGURATION))) {
            processSaslProperties(configurations, properties);
        }
        if (Objects.nonNull(configurations.getMapValue(ADDITIONAL_PROPERTIES_MAP_FIELD))) {
            processAdditionalProperties(configurations.getMapValue(ADDITIONAL_PROPERTIES_MAP_FIELD),
                                        properties);
        }
        return properties;
    }

    public static Properties processKafkaProducerConfig(Object bootstrapServers, BMap<BString, Object> configurations) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getServerUrls(bootstrapServers));
        addStringParamIfPresent(ProducerConfig.ACKS_CONFIG, configurations,
                                properties, KafkaConstants.PRODUCER_ACKS_CONFIG);
        addStringParamIfPresent(ProducerConfig.COMPRESSION_TYPE_CONFIG, configurations,
                                properties, KafkaConstants.PRODUCER_COMPRESSION_TYPE_CONFIG);
        addStringParamIfPresent(ProducerConfig.CLIENT_ID_CONFIG, configurations,
                                properties, KafkaConstants.PRODUCER_CLIENT_ID_CONFIG);
        addStringParamIfPresent(ProducerConfig.METRICS_RECORDING_LEVEL_CONFIG, configurations,
                                properties, KafkaConstants.PRODUCER_METRICS_RECORDING_LEVEL_CONFIG);
        addStringParamIfPresent(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, configurations,
                                properties, KafkaConstants.PRODUCER_METRIC_REPORTER_CLASSES_CONFIG);
        addStringParamIfPresent(ProducerConfig.PARTITIONER_CLASS_CONFIG, configurations,
                                properties, KafkaConstants.PRODUCER_PARTITIONER_CLASS_CONFIG);
        addStringParamIfPresent(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, configurations,
                                properties, KafkaConstants.PRODUCER_INTERCEPTOR_CLASSES_CONFIG);
        addStringParamIfPresent(ProducerConfig.TRANSACTIONAL_ID_CONFIG, configurations,
                                properties, KafkaConstants.PRODUCER_TRANSACTIONAL_ID_CONFIG);
        addStringParamIfPresent(KafkaConstants.SCHEMA_REGISTRY_URL, configurations, properties,
                                KafkaConstants.PRODUCER_SCHEMA_REGISTRY_URL);

        addSerializerTypeConfigs(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, properties);
        addSerializerTypeConfigs(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, properties);
        // TODO: Disabled as the custom SerDes support is to be revisited and improved.
        //  Fix once the design for that is completed.
//        addCustomKeySerializer(properties, configurations);
//        addCustomValueSerializer(properties, configurations);

        addIntParamIfPresent(ProducerConfig.BUFFER_MEMORY_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_BUFFER_MEMORY_CONFIG);
        addIntParamIfPresent(ProducerConfig.RETRIES_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_RETRIES_CONFIG);
        addIntParamIfPresent(ProducerConfig.BATCH_SIZE_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_BATCH_SIZE_CONFIG);
        addTimeParamIfPresent(ProducerConfig.LINGER_MS_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_LINGER_MS_CONFIG);
        addIntParamIfPresent(ProducerConfig.SEND_BUFFER_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_SEND_BUFFER_CONFIG);
        addIntParamIfPresent(ProducerConfig.RECEIVE_BUFFER_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_RECEIVE_BUFFER_CONFIG);
        addIntParamIfPresent(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_MAX_REQUEST_SIZE_CONFIG);
        addTimeParamIfPresent(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_RECONNECT_BACKOFF_MS_CONFIG);
        addTimeParamIfPresent(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_RECONNECT_BACKOFF_MAX_MS_CONFIG);
        addTimeParamIfPresent(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_RETRY_BACKOFF_MS_CONFIG);
        addTimeParamIfPresent(ProducerConfig.MAX_BLOCK_MS_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_MAX_BLOCK_MS_CONFIG);
        addTimeParamIfPresent(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_REQUEST_TIMEOUT_MS_CONFIG);
        addTimeParamIfPresent(ProducerConfig.METADATA_MAX_AGE_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_METADATA_MAX_AGE_CONFIG);
        addTimeParamIfPresent(ProducerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_METRICS_SAMPLE_WINDOW_MS_CONFIG);
        addIntParamIfPresent(ProducerConfig.METRICS_NUM_SAMPLES_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_METRICS_NUM_SAMPLES_CONFIG);
        addIntParamIfPresent(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, configurations,
                             properties, KafkaConstants.PRODUCER_MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION);
        addTimeParamIfPresent(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_CONNECTIONS_MAX_IDLE_MS_CONFIG);
        addTimeParamIfPresent(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, configurations,
                             properties, KafkaConstants.PRODUCER_TRANSACTION_TIMEOUT_CONFIG);
        addStringParamIfPresent(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, configurations,
                             properties, KafkaConstants.SECURITY_PROTOCOL_CONFIG);
        addBooleanParamIfPresent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, configurations,
                                 properties, KafkaConstants.PRODUCER_ENABLE_IDEMPOTENCE_CONFIG);
        if (Objects.nonNull(configurations.get(KafkaConstants.SECURE_SOCKET))) {
            processSslProperties(configurations, properties);
        }
        if (Objects.nonNull(configurations.get(KafkaConstants.AUTHENTICATION_CONFIGURATION))) {
            processSaslProperties(configurations, properties);
        }
        if (Objects.nonNull(configurations.getMapValue(ADDITIONAL_PROPERTIES_MAP_FIELD))) {
            processAdditionalProperties(configurations.getMapValue(ADDITIONAL_PROPERTIES_MAP_FIELD),
                                        properties);
        }
        return properties;
    }

    @SuppressWarnings(KafkaConstants.UNCHECKED)
    private static void processSslProperties(BMap<BString, Object> configurations, Properties configParams) {
        BMap<BString, Object> secureSocket = (BMap<BString, Object>) configurations.get(KafkaConstants.SECURE_SOCKET);

        BMap<BString, Object> keyConfig = (BMap<BString, Object>) secureSocket.get(KafkaConstants.KEY_CONFIG);
        if (keyConfig != null) {
            if (keyConfig.containsKey(KafkaConstants.SSL_CERT_FILE_LOCATION_CONFIG)) {
                BString certFile = (BString) keyConfig.get(KafkaConstants.SSL_CERT_FILE_LOCATION_CONFIG);
                BString keyFile = (BString) keyConfig.get(KafkaConstants.SSL_KEY_FILE_LOCATION_CONFIG);
                BString keyPassword = getBStringValueIfPresent(keyConfig, KafkaConstants.SSL_KEY_PASSWORD_CONFIG);
                String certValue;
                String keyValue;
                try {
                    certValue = readPasswordValueFromFile(certFile.getValue());
                } catch (IOException e) {
                    throw createKafkaError("Error reading certificate file : " + e.getMessage());
                }
                try {
                    keyValue = readPasswordValueFromFile(keyFile.getValue());
                } catch (IOException e) {
                    throw createKafkaError("Error reading private key file : " + e.getMessage());
                }
                configParams.setProperty(SslConfigs.SSL_KEYSTORE_KEY_CONFIG, keyValue);
                configParams.setProperty(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, certValue);
                if (keyPassword != null) {
                    configParams.setProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword.getValue());
                }
                configParams.setProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, KafkaConstants.SSL_STORE_TYPE_CONFIG);
            } else {
                addStringParamIfPresent(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                        (BMap<BString, Object>) keyConfig.get(KafkaConstants.KEYSTORE_CONFIG), configParams,
                        KafkaConstants.LOCATION_CONFIG);
                addStringParamIfPresent(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
                        (BMap<BString, Object>) keyConfig.get(KafkaConstants.KEYSTORE_CONFIG), configParams,
                        KafkaConstants.PASSWORD_CONFIG);
                addStringParamIfPresent(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyConfig, configParams,
                        KafkaConstants.SSL_KEY_PASSWORD_CONFIG);
            }
        }
        Object cert = secureSocket.get(KafkaConstants.TRUSTSTORE_CONFIG);
        if (cert instanceof BString) {
            String trustCertValue;
            try {
                trustCertValue = readPasswordValueFromFile(((BString) cert).getValue());
            } catch (IOException e) {
                throw createKafkaError("Error reading certificate file : " + e.getMessage());
            }
            configParams.setProperty(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, trustCertValue);
            configParams.setProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, KafkaConstants.SSL_STORE_TYPE_CONFIG);
        } else {
            addStringParamIfPresent(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                    (BMap<BString, Object>) secureSocket.get(KafkaConstants.TRUSTSTORE_CONFIG),
                    configParams, KafkaConstants.LOCATION_CONFIG);
            addStringParamIfPresent(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
                    (BMap<BString, Object>) secureSocket.get(KafkaConstants.TRUSTSTORE_CONFIG),
                    configParams, KafkaConstants.PASSWORD_CONFIG);
        }
        // ciphers
        addStringArrayAsStringParamIfPresent(SslConfigs.SSL_CIPHER_SUITES_CONFIG, configurations, configParams,
                KafkaConstants.SSL_CIPHER_SUITES_CONFIG);
        // provider
        addStringParamIfPresent(SslConfigs.SSL_PROVIDER_CONFIG, configurations, configParams,
                KafkaConstants.SSL_PROVIDER_CONFIG);

        // protocol
        BMap<BString, Object> protocol = (BMap<BString, Object>) secureSocket.get(KafkaConstants.PROTOCOL_CONFIG);
        addStringParamIfPresent(SslConfigs.SSL_PROTOCOL_CONFIG, protocol, configParams,
                                KafkaConstants.SSL_PROTOCOL_NAME);
        addStringArrayAsStringParamIfPresent(SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG,
                                protocol, configParams,
                                KafkaConstants.SSL_PROTOCOL_VERSIONS);
    }

    @SuppressWarnings(KafkaConstants.UNCHECKED)
    private static void processSaslProperties(BMap<BString, Object> configurations, Properties properties) {
        BMap<BString, Object> authenticationConfig =
                (BMap<BString, Object>) configurations.getMapValue(KafkaConstants.AUTHENTICATION_CONFIGURATION);
        String mechanism = authenticationConfig.getStringValue(KafkaConstants.AUTHENTICATION_MECHANISM).getValue();
        if (KafkaConstants.SASL_PLAIN.equals(mechanism)) {
            String username = authenticationConfig.getStringValue(KafkaConstants.USERNAME).getValue();
            String password = authenticationConfig.getStringValue(KafkaConstants.PASSWORD).getValue();
            String jaasConfigValue =
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + username +
                            "\" password=\"" + password + "\";";
            addStringParamIfPresent(SaslConfigs.SASL_MECHANISM, authenticationConfig, properties,
                                    KafkaConstants.AUTHENTICATION_MECHANISM);
            addStringParamIfPresent(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, authenticationConfig, properties,
                                    KafkaConstants.SECURITY_PROTOCOL_CONFIG);
            properties.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfigValue);
        }
    }

    private static void processAdditionalProperties(BMap propertiesMap, Properties kafkaProperties) {
        for (Object key : propertiesMap.getKeys()) {
            kafkaProperties.setProperty(key.toString(), propertiesMap.getStringValue((BString) key).getValue());
        }
    }

    private static void addSerializerTypeConfigs(String paramName, Properties configParams) {
        configParams.put(paramName, KafkaConstants.BYTE_ARRAY_SERIALIZER);
    }

    private static void addDeserializerConfigs(String paramName, Properties configParams) {
            configParams.put(paramName, KafkaConstants.BYTE_ARRAY_DESERIALIZER);
    }

    // TODO: Disabled as the SerDes support is to be revisited and improved. Fix once the design for that is completed.
//    private static void addCustomKeySerializer(Properties properties, BMap<BString, Object> configurations) {
//        Object serializer = configurations.get(KafkaConstants.PRODUCER_KEY_SERIALIZER_CONFIG);
//        String serializerType =
//                configurations.getStringValue(KafkaConstants.PRODUCER_KEY_SERIALIZER_TYPE_CONFIG).getValue();
//        if (Objects.nonNull(serializer) && KafkaConstants.SERDES_CUSTOM.equals(serializerType)) {
//            properties.put(KafkaConstants.PRODUCER_KEY_SERIALIZER_CONFIG.getValue(),
//                           configurations.get(KafkaConstants.PRODUCER_KEY_SERIALIZER_CONFIG));
//        }
//    }
//
//    private static void addCustomValueSerializer(Properties properties, BMap<BString, Object> configurations) {
//        Object serializer = configurations.get(KafkaConstants.PRODUCER_VALUE_SERIALIZER_CONFIG);
//        String serializerType =
//                configurations.getStringValue(KafkaConstants.PRODUCER_VALUE_SERIALIZER_TYPE_CONFIG).getValue();
//        if (Objects.nonNull(serializer) && KafkaConstants.SERDES_CUSTOM.equals(serializerType)) {
//            properties.put(KafkaConstants.PRODUCER_VALUE_SERIALIZER_CONFIG.getValue(),
//                           configurations.get(KafkaConstants.PRODUCER_VALUE_SERIALIZER_CONFIG));
//        }
//    }

//    private static void addCustomDeserializer(BString configParam, BString typeConfig, Properties properties,
//                                              BMap<BString, Object> configurations) {
//        Object deserializer = configurations.get(configParam);
//        String deserializerType = configurations.getStringValue(typeConfig).getValue();
//        if (Objects.nonNull(deserializer) && KafkaConstants.SERDES_CUSTOM.equals(deserializerType)) {
//            properties.put(configParam.getValue(), configurations.get(configParam));
//            properties.put(KafkaConstants.BALLERINA_STRAND, Runtime.getCurrentRuntime());
//        }
//    }

//    private static String getSerializerType(String value) {
//        switch (value) {
//            case KafkaConstants.SERDES_BYTE_ARRAY:
//                return KafkaConstants.BYTE_ARRAY_SERIALIZER;
//            case KafkaConstants.SERDES_STRING:
//                return KafkaConstants.STRING_SERIALIZER;
//            case KafkaConstants.SERDES_INT:
//                return KafkaConstants.INT_SERIALIZER;
//            case KafkaConstants.SERDES_FLOAT:
//                return KafkaConstants.FLOAT_SERIALIZER;
//            case KafkaConstants.SERDES_AVRO:
//                return KafkaConstants.AVRO_SERIALIZER;
//            case KafkaConstants.SERDES_CUSTOM:
//                return KafkaConstants.CUSTOM_SERIALIZER;
//            default:
//                return value;
//        }
//    }
//
//    private static String getDeserializerValue(String value) {
//        switch (value) {
//            case KafkaConstants.SERDES_BYTE_ARRAY:
//                return KafkaConstants.BYTE_ARRAY_DESERIALIZER;
//            case KafkaConstants.SERDES_STRING:
//                return KafkaConstants.STRING_DESERIALIZER;
//            case KafkaConstants.SERDES_INT:
//                return KafkaConstants.INT_DESERIALIZER;
//            case KafkaConstants.SERDES_FLOAT:
//                return KafkaConstants.FLOAT_DESERIALIZER;
//            case KafkaConstants.SERDES_AVRO:
//                return KafkaConstants.AVRO_DESERIALIZER;
//            case KafkaConstants.SERDES_CUSTOM:
//                return KafkaConstants.CUSTOM_DESERIALIZER;
//            default:
//                return value;
//        }
//    }

    private static void addStringParamIfPresent(String paramName,
                                                BMap<BString, Object> configs,
                                                Properties configParams,
                                                BString key) {
        if (Objects.nonNull(configs.get(key))) {
            BString value = (BString) configs.get(key);
            if (!(value == null || value.getValue().equals(""))) {
                configParams.setProperty(paramName, value.getValue());
            }
        }
    }

    private static void addStringOrStringArrayParamIfPresent(String paramName,
                                                     BMap<BString, Object> configs,
                                                     Properties configParams,
                                                     BString key) {
        if (configs.containsKey(key)) {
            List<String> values;
            Object paramValues = configs.get(key);
            if (paramValues instanceof BArray) {
                BArray stringArray = (BArray) paramValues;
                values = getStringListFromStringBArray(stringArray);
            } else {
                values = List.of(((BString) paramValues).getValue());
            }
            configParams.put(paramName, values);
        }
    }

    private static void addStringArrayAsStringParamIfPresent(String paramName,
                                                     BMap<BString, Object> configs,
                                                     Properties configParams,
                                                     BString key) {
        if (configs.containsKey(key)) {
            BArray stringArray = (BArray) configs.get(key);
            String values = getStringFromStringBArray(stringArray);
            configParams.put(paramName, values);
        }
    }

    private static String getStringFromStringBArray(BArray stringArray) {
        String[] values = stringArray.getStringArray();
        return String.join(",", values);
    }

    private static BString getBStringValueIfPresent(BMap<BString, ?> config, BString key) {
        return config.containsKey(key) ? config.getStringValue(key) : null;
    }

    private static void addTimeParamIfPresent(String paramName,
                                             BMap<BString, Object> configs,
                                             Properties configParams,
                                             BString key) {
        if (configs.containsKey(key)) {
            BigDecimal configValueInSeconds = ((BDecimal) configs.get(key)).decimalValue();
            int valueInMilliSeconds = (configValueInSeconds).multiply(KafkaConstants.MILLISECOND_MULTIPLIER).intValue();
            configParams.put(paramName, valueInMilliSeconds);
        }
    }

    private static void addIntParamIfPresent(String paramName,
                                             BMap<BString, Object> configs,
                                             Properties configParams,
                                             BString key) {
        Long value = (Long) configs.get(key);
        if (Objects.nonNull(value)) {
            configParams.put(paramName, value.intValue());
        }
    }

    private static void addBooleanParamIfPresent(String paramName,
                                                 BMap<BString, Object> configs,
                                                 Properties configParams,
                                                 BString key,
                                                 boolean defaultValue) {
        boolean value = (boolean) configs.get(key);
        if (value != defaultValue) {
            configParams.put(paramName, value);
        }
    }

    private static void addBooleanParamIfPresent(String paramName,
                                                 BMap<BString, Object> configs,
                                                 Properties configParams,
                                                 BString key) {
        boolean value = (boolean) configs.get(key);
        configParams.put(paramName, value);
    }

    public static ArrayList<TopicPartition> getTopicPartitionList(BArray partitions, Logger logger) {
        ArrayList<TopicPartition> partitionList = new ArrayList<>();
        if (partitions != null) {
            for (int counter = 0; counter < partitions.size(); counter++) {
                BMap<BString, Object> partition = (BMap<BString, Object>) partitions.get(counter);
                String topic = partition.get(KafkaConstants.ALIAS_TOPIC).toString();
                int partitionValue = getIntFromLong((Long) partition.get(KafkaConstants.ALIAS_PARTITION), logger,
                                                    KafkaConstants.ALIAS_PARTITION.getValue());
                partitionList.add(new TopicPartition(topic, partitionValue));
            }
        }
        return partitionList;
    }

    public static List<String> getStringListFromStringBArray(BArray stringArray) {
        ArrayList<String> values = new ArrayList<>();
        if ((Objects.isNull(stringArray)) || (!getReferredType(((ArrayType) stringArray.getType()).getElementType())
                .equals(PredefinedTypes.TYPE_STRING))) {
            return values;
        }
        if (stringArray.size() != 0) {
            for (int i = 0; i < stringArray.size(); i++) {
                values.add(stringArray.getString(i));
            }
        }
        return values;
    }

    /**
     * Populate the {@code TopicPartition} record type in Ballerina.
     *
     * @param topic     name of the topic
     * @param partition value of the partition offset
     * @return {@code BMap} of the record
     */
    public static BMap<BString, Object> populateTopicPartitionRecord(String topic, long partition) {
        return ValueCreator.createRecordValue(getTopicPartitionRecord(), topic, partition);
    }

    public static BMap<BString, Object> populatePartitionOffsetRecord(BMap<BString, Object> topicPartition,
                                                                          long offset) {
        return ValueCreator.createRecordValue(getPartitionOffsetRecord(), topicPartition, offset);
    }

    public static BMap<BString, Object> populateConsumerRecord(ConsumerRecord record, RecordType recordType,
                                                               boolean validateConstraints, boolean autoSeek) {
        Object key = null;
        Map<String, Field> fieldMap = recordType.getFields();
        Type keyType = getReferredType(fieldMap.get(KAFKA_RECORD_KEY).getFieldType());
        Type valueType = getReferredType(fieldMap.get(KAFKA_RECORD_VALUE).getFieldType());
        if (Objects.nonNull(record.key())) {
            key = getValueWithIntendedType(keyType, (byte[]) record.key(), record, autoSeek);
        }

        Object value = getValueWithIntendedType(valueType, (byte[]) record.value(), record, autoSeek);
        BMap<BString, Object> topicPartition = ValueCreator.createRecordValue(getTopicPartitionRecord(), record.topic(),
                (long) record.partition());
        BMap<BString, Object> consumerRecord = ValueCreator.createRecordValue(recordType);
        consumerRecord.put(StringUtils.fromString(KAFKA_RECORD_KEY), key);
        consumerRecord.put(StringUtils.fromString(KAFKA_RECORD_VALUE), value);
        consumerRecord.put(StringUtils.fromString(KAFKA_RECORD_TIMESTAMP), record.timestamp());
        consumerRecord.put(StringUtils.fromString(KAFKA_RECORD_PARTITION_OFFSET), ValueCreator.createRecordValue(
                getPartitionOffsetRecord(), topicPartition, record.offset()));
        if (validateConstraints) {
            validateConstraints(consumerRecord, ValueCreator.createTypedescValue(recordType), record, autoSeek);
        }
        return consumerRecord;
    }

    public static BArray getConsumerRecords(ConsumerRecords records, RecordType recordType, boolean readonly,
                                            boolean validateConstraints, boolean autoCommit,
                                            KafkaConsumer consumer, boolean autoSeek) {
        BArray consumerRecordsArray = ValueCreator.createArrayValue(TypeCreator.createArrayType(recordType));
        HashMap<String, PartitionOffset> partitionOffsetMap = new HashMap<>();
        int i = 0;
        for (Object record : records) {
            ConsumerRecord consumerRecord = (ConsumerRecord) record;
            try {
                consumerRecordsArray.append(populateConsumerRecord((ConsumerRecord) record, recordType,
                        validateConstraints, autoSeek));
            } catch (BError bError) {
                if (handleBError(consumer, (ConsumerRecord) record, autoSeek, bError, i == 0)) {
                    break;
                }
            }
            if (autoCommit) {
                updatePartitionOffsetMap(partitionOffsetMap, consumerRecord,
                        consumerRecord.topic() + "-" + consumerRecord.partition());
            }
            i++;
        }
        if (readonly) {
            consumerRecordsArray.freezeDirect();
        }
        commitAndSeekConsumedRecord(consumer, partitionOffsetMap);
        return consumerRecordsArray;
    }

    private static boolean handleBError(KafkaConsumer consumer, ConsumerRecord record, boolean autoSeek, BError bError,
                                        boolean firstRecord) {
        if (isPayloadError(bError)) {
            if (!autoSeek) {
                consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
                if (firstRecord) {
                    throw bError;
                }
                return true;
            } else {
                bError.printStackTrace();
            }
        } else {
            throw bError;
        }
        return false;
    }

    private static Map<TopicPartition, OffsetAndMetadata> getOffsetsFromMap(HashMap<String, PartitionOffset>
                                                                                    partitionMap) {
        Map<TopicPartition, OffsetAndMetadata> metadataMap = new HashMap<>();
        for (PartitionOffset partitionOffset : partitionMap.values()
                .toArray(new PartitionOffset[partitionMap.size()])) {
            metadataMap.put(partitionOffset.getTopicPartition(),
                    new OffsetAndMetadata(partitionOffset.getOffset() + 1));
        }
        return metadataMap;
    }

    private static void updatePartitionOffsetMap(HashMap<String, PartitionOffset> partitionOffsetMap,
                                                 ConsumerRecord consumerRecord, String topicPartitionName) {
        if (partitionOffsetMap.containsKey(topicPartitionName)) {
            partitionOffsetMap.get(topicPartitionName).setOffset(consumerRecord.offset());
        } else {
            partitionOffsetMap.put(topicPartitionName, new PartitionOffset(consumerRecord.topic(),
                    consumerRecord.partition(), consumerRecord.offset()));
        }
    }

    public static Object getValueWithIntendedType(Type type, byte[] value, ConsumerRecord consumerRecord,
                                                  boolean autoSeek) {
        String strValue = new String(value, StandardCharsets.UTF_8);
        Object intendedValue;
        try {
            switch (type.getTag()) {
                case STRING_TAG:
                    intendedValue = StringUtils.fromString(strValue);
                    break;
                case XML_TAG:
                    intendedValue = XmlUtils.parse(strValue);
                    break;
                case ANYDATA_TAG:
                    intendedValue = ValueCreator.createArrayValue(value);
                    break;
                case RECORD_TYPE_TAG:
                    intendedValue = ValueUtils.convert(JsonUtils.parse(strValue), type);
                    break;
                case UNION_TAG:
                    if (hasStringType((UnionType) type)) {
                        intendedValue = StringUtils.fromString(strValue);
                        break;
                    }
                    intendedValue = getValueFromJson(type, strValue);
                    break;
                case ARRAY_TAG:
                    if (getReferredType(((ArrayType) type).getElementType()).getTag() == BYTE_TAG) {
                        intendedValue = ValueCreator.createArrayValue(value);
                        break;
                    }
                    /*-fallthrough*/
                default:
                    intendedValue = getValueFromJson(type, strValue);
            }
        } catch (BError bError) {
            throw createPayloadBindingError(bError, consumerRecord, autoSeek);
        }
        if (intendedValue instanceof BError) {
            throw createPayloadBindingError((BError) intendedValue, consumerRecord, autoSeek);
        }
        return intendedValue;
    }

    private static boolean hasStringType(UnionType type) {
        return type.getMemberTypes().stream().anyMatch(memberType -> {
            if (memberType.getTag() == STRING_TAG) {
                return true;
            }
            return false;
        });
    }

    private static Object getValueFromJson(Type type, String stringValue) {
        return ValueUtils.convert(JsonUtils.parse(stringValue), type);
    }

    public static BMap<BString, Object> getPartitionOffsetRecord() {
        return createKafkaRecord(KafkaConstants.OFFSET_STRUCT_NAME);
    }

    public static BMap<BString, Object> getTopicPartitionRecord() {
        return createKafkaRecord(KafkaConstants.TOPIC_PARTITION_STRUCT_NAME);
    }

    public static BError createKafkaError(String message) {
        return ErrorCreator.createDistinctError(KAFKA_ERROR, getModule(),
                                                StringUtils.fromString(message));
    }

    public static BError createKafkaError(String message, BError cause) {
        return ErrorCreator.createDistinctError(KAFKA_ERROR, getModule(),
                                                 StringUtils.fromString(message), cause);
    }

    public static BError createPayloadValidationError(BError cause, ConsumerRecord record, boolean autoSeek) {
        BMap<BString, Object> partition = populatePartitionOffsetRecord(populateTopicPartitionRecord(record.topic(),
                record.partition()), record.offset());
        if (autoSeek) {
            return ErrorCreator.createError(getModule(), PAYLOAD_VALIDATION_ERROR,
                    StringUtils.fromString("Failed to validate payload."), cause, partition);
        }
        return ErrorCreator.createError(getModule(), PAYLOAD_VALIDATION_ERROR, StringUtils.fromString("Failed to " +
                "validate payload. If needed, please seek past the record to continue consumption."), cause, partition);
    }

    public static BError createPayloadBindingError(BError cause, ConsumerRecord record, boolean autoSeek) {
        BMap<BString, Object> partition = populatePartitionOffsetRecord(populateTopicPartitionRecord(record.topic(),
                record.partition()), record.offset());
        if (autoSeek) {
            return ErrorCreator.createError(getModule(), PAYLOAD_BINDING_ERROR,
                    StringUtils.fromString("Data binding failed."), cause, partition);
        }
        return ErrorCreator.createError(getModule(), PAYLOAD_BINDING_ERROR, StringUtils.fromString("Data binding " +
                "failed. If needed, please seek past the record to continue consumption."), cause, partition);
    }

    public static BMap<BString, Object> createKafkaRecord(String recordName) {
        return ValueCreator.createRecordValue(getModule(), recordName);
    }

    public static BArray getPartitionOffsetArrayFromOffsetMap(Map<TopicPartition, Long> offsetMap) {
        BArray partitionOffsetArray = ValueCreator.createArrayValue(TypeCreator.createArrayType(
                getPartitionOffsetRecord().getType()));
        if (!offsetMap.entrySet().isEmpty()) {
            for (Map.Entry<TopicPartition, Long> entry : offsetMap.entrySet()) {
                TopicPartition tp = entry.getKey();
                Long offset = entry.getValue();
                BMap<BString, Object> topicPartition = populateTopicPartitionRecord(tp.topic(), tp.partition());
                BMap<BString, Object> partition = populatePartitionOffsetRecord(topicPartition, offset);
                partitionOffsetArray.append(partition);
            }
        }
        return partitionOffsetArray;
    }

    /**
     * Get {@code Map<TopicPartition, OffsetAndMetadata>} map used in committing consumers.
     *
     * @param offsets {@code BArray} of Ballerina {@code PartitionOffset} records
     * @return {@code Map<TopicPartition, OffsetAndMetadata>} created using Ballerina {@code PartitionOffset}
     */
    public static Map<TopicPartition, OffsetAndMetadata> getPartitionToMetadataMap(BArray offsets) {
        Map<TopicPartition, OffsetAndMetadata> partitionToMetadataMap = new HashMap<>();
        for (int i = 0; i < offsets.size(); i++) {
            BMap offset = (BMap) offsets.get(i);
            int offsetValue = offset.getIntValue(KafkaConstants.ALIAS_OFFSET).intValue();
            TopicPartition topicPartition = createTopicPartitionFromPartitionOffset(offset);
            partitionToMetadataMap.put(topicPartition, new OffsetAndMetadata(offsetValue));
        }
        return partitionToMetadataMap;
    }

    /**
     * Get {@code TopicPartition} object from {@code BMap} of Ballerina {@code PartitionOffset}.
     *
     * @param offset BMap consists of Ballerina PartitionOffset record.
     * @return TopicPartition Object created
     */
    public static TopicPartition createTopicPartitionFromPartitionOffset(BMap offset) {
        BMap partition = (BMap) offset.get(KafkaConstants.ALIAS_PARTITION);
        String topic = partition.getStringValue(KafkaConstants.ALIAS_TOPIC).getValue();
        int partitionValue = partition.getIntValue(KafkaConstants.ALIAS_PARTITION).intValue();

        return new TopicPartition(topic, partitionValue);
    }

    /**
     * Get the Integer value from an Object, if possible.
     *
     * @param value  the {@code Object} which needs to be converted to int
     * @param name   name of the parameter, for logging purposes
     * @param logger {@code Logger} instance to log if there's an issue
     * @return Integer value of the {@code Object}, {@code null} otherwise
     */
    public static Integer getIntValue(Object value, BString name, Logger logger) {
        Long longValue = getLongValue(value);
        if (Objects.isNull(longValue)) {
            return null;
        }
        return getIntFromLong(longValue, logger, name.getValue());
    }

    /**
     * Get the {@code int} value from a {@code long} value.
     *
     * @param longValue {@code long} value, which we want to convert
     * @param logger    {@code Logger} instance, to log the error if there's an error
     * @param name      parameter name, which will be converted. This is required for logging purposes
     * @return {@code int} value of the {@code long} value, if possible, {@code Integer.MAX_VALUE} if the number is too
     * large
     */
    public static int getIntFromLong(long longValue, Logger logger, String name) {
        try {
            return Math.toIntExact(longValue);
        } catch (ArithmeticException e) {
            logger.warn("The value set for {} needs to be less than {}. The {} value is set to {}", name,
                        Integer.MAX_VALUE, name, Integer.MAX_VALUE);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Get the {@code int} value from a {@code BDecimal} value.
     *
     * @param bDecimal {@code BDecimal} value, which we want to convert
     * @param logger   {@code Logger} instance, to log the error if there's an error
     * @param name     parameter name, which will be converted. This is required for logging purposes
     * @return {@code int} value of the {@code BDecimal} value, if possible, {@code Integer.MAX_VALUE} if the number
     * is too large
     */
    public static int getIntFromBDecimal(BDecimal bDecimal, Logger logger, String name) {
        try {
            return getMilliSeconds(bDecimal);
        } catch (ArithmeticException e) {
            logger.warn("The value set for {} needs to be less than {}. The {} value is set to {}", name,
                    Integer.MAX_VALUE, name, Integer.MAX_VALUE);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Get the millisecond value from a {@code BDecimal}.
     *
     * @param longValue BDecimal from which we want to get the milliseconds
     * @return millisecond value of the longValue in {@code int}
     */
    public static int getMilliSeconds(BDecimal longValue) {
        BigDecimal valueInSeconds = longValue.decimalValue();
        return valueInSeconds.multiply(KafkaConstants.MILLISECOND_MULTIPLIER).intValue();
    }

    /**
     * Get the {@code Long} value from an {@code Object}.
     *
     * @param value Object from which we want to get the Long value
     * @return Long value of the Object, if present. {@code null} otherwise
     */
    public static Long getLongValue(Object value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return (Long) value;
    }

    /**
     * Get the default API timeout defined in the Kafka configurations.
     *
     * @param consumerProperties - Native consumer properties object
     * @return value of the default api timeout, if defined, -1 otherwise.
     */
    public static int getDefaultApiTimeout(Properties consumerProperties) {
        if (Objects.nonNull(consumerProperties.get(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG))) {
            return (int) consumerProperties.get(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG);
        }
        return KafkaConstants.DURATION_UNDEFINED_VALUE;
    }

    public static void createKafkaProducer(Properties producerProperties, BObject producerObject) {
        KafkaProducer kafkaProducer = new KafkaProducer<>(producerProperties);
        producerObject.addNativeData(KafkaConstants.NATIVE_PRODUCER, kafkaProducer);
        producerObject.addNativeData(KafkaConstants.NATIVE_PRODUCER_CONFIG, producerProperties);
        producerObject.addNativeData(KafkaConstants.BOOTSTRAP_SERVERS,
                                     producerProperties.getProperty(KafkaConstants.BOOTSTRAP_SERVERS));
        producerObject.addNativeData(KafkaConstants.CLIENT_ID, getClientIdFromProperties(producerProperties));
        KafkaMetricsUtil.reportNewProducer(producerObject);
    }

    public static String getTopicNamesString(List<String> topicsList) {
        return String.join(", ", topicsList);
    }

    public static String getClientIdFromProperties(Properties properties) {
        if (properties == null) {
            return KafkaObservabilityConstants.UNKNOWN;
        }
        String clientId = properties.getProperty(KafkaConstants.CLIENT_ID);
        if (clientId == null) {
            return KafkaObservabilityConstants.UNKNOWN;
        }
        return clientId;
    }

    public static String getBootstrapServers(BObject object) {
        if (object == null) {
            return KafkaObservabilityConstants.UNKNOWN;
        }
        String bootstrapServers = (String) object.getNativeData(KafkaConstants.BOOTSTRAP_SERVERS);
        if (bootstrapServers == null) {
            return KafkaObservabilityConstants.UNKNOWN;
        }
        return bootstrapServers;
    }

    public static String getClientId(BObject object) {
        if (object == null) {
            return KafkaObservabilityConstants.UNKNOWN;
        }
        String clientId = (String) object.getNativeData(KafkaConstants.CLIENT_ID);
        if (clientId == null) {
            return KafkaObservabilityConstants.UNKNOWN;
        }
        return clientId;
    }

    public static String getServerUrls(Object bootstrapServer) {
        if (TypeUtils.getType(bootstrapServer).getTag() == TypeTags.ARRAY_TAG) {
            // if string[]
            String[] serverUrls = ((BArray) bootstrapServer).getStringArray();
            return String.join(",", serverUrls);
        } else {
            // if string
            return ((BString) bootstrapServer).getValue();
        }
    }

    public static Type getAttachedFunctionReturnType(BObject serviceObject, String functionName) {
        MethodType function = null;
        ObjectType objectType = (ObjectType) TypeUtils.getReferredType(TypeUtils.getType(serviceObject));
        MethodType[] resourceFunctions = objectType.getMethods();
        for (MethodType resourceFunction : resourceFunctions) {
            if (functionName.equals(resourceFunction.getName())) {
                function = resourceFunction;
                Type returnType = function.getReturnType();
                return returnType;
            }
        }
        return function;
    }

    public static Object validateConstraints(Object value, BTypedesc bTypedesc, ConsumerRecord consumerRecord,
                                             boolean autoSeek) {
        Object validationResult = Constraints.validate(value, bTypedesc);
        if (validationResult instanceof BError) {
            throw createPayloadValidationError((BError) validationResult, consumerRecord, autoSeek);
        }
        return value;
    }

    public static String readPasswordValueFromFile(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath));
    }

    public static BArray getValuesWithIntendedType(Type type, KafkaConsumer consumer, ConsumerRecords records,
                                                   boolean constraintValidation, boolean autoCommit, boolean autoSeek) {
        ArrayType intendedType;
        if (type.getTag() == INTERSECTION_TAG) {
            intendedType = (ArrayType) ((IntersectionType) type).getConstituentTypes().get(0);
        } else {
            intendedType = TypeCreator.createArrayType(((ArrayType) type).getElementType());
        }
        BArray bArray = ValueCreator.createArrayValue(intendedType);
        HashMap<String, PartitionOffset> partitionOffsetMap = new HashMap<>();
        int i = 0;
        for (Object record: records) {
            ConsumerRecord consumerRecord = (ConsumerRecord) record;
            try {
                Object value = getValueWithIntendedType(getReferredType(intendedType.getElementType()),
                        (byte[]) (consumerRecord.value()), consumerRecord, autoSeek);
                if (constraintValidation) {
                    validateConstraints(value, ValueCreator.createTypedescValue(intendedType.getElementType()),
                            consumerRecord, autoSeek);
                }
                bArray.append(value);
            } catch (BError bError) {
                if (handleBError(consumer, (ConsumerRecord) record, autoSeek, bError, i == 0)) {
                    break;
                }
            }
            if (autoCommit) {
                updatePartitionOffsetMap(partitionOffsetMap, consumerRecord,
                        consumerRecord.topic() + "-" + consumerRecord.partition());
            }
            i++;
        }
        if (type.isReadOnly() || ((ArrayType) type).getElementType().isReadOnly()) {
            bArray.freezeDirect();
        }
        commitAndSeekConsumedRecord(consumer, partitionOffsetMap);
        return bArray;
    }

    private static boolean isPayloadError(BError bError) {
        return bError.getType().getName().equals(PAYLOAD_BINDING_ERROR) ||
                bError.getType().getName().equals(PAYLOAD_VALIDATION_ERROR);
    }

    private static void commitAndSeekConsumedRecord(KafkaConsumer consumer,
                                                    HashMap<String, PartitionOffset> partitionOffsetMap) {
        consumer.commitSync(getOffsetsFromMap(partitionOffsetMap));
        for (PartitionOffset partitionOffset : partitionOffsetMap.values()
                .toArray(new PartitionOffset[partitionOffsetMap.values().size()])) {
            consumer.seek(partitionOffset.getTopicPartition(), partitionOffset.getOffset() + 1);
        }
    }

    public static boolean getAutoCommitConfig(BObject bObject) {
        BMap consumerConfig = bObject.getMapValue(CONSUMER_CONFIG_FIELD_NAME);
        if (consumerConfig.containsKey(ADDITIONAL_PROPERTIES_MAP_FIELD)) {
            BMap additionalProperties = (BMap) consumerConfig.get(ADDITIONAL_PROPERTIES_MAP_FIELD);
            if (additionalProperties.containsKey(CONSUMER_ENABLE_AUTO_COMMIT)) {
                return Boolean.parseBoolean(((BString) additionalProperties.get(CONSUMER_ENABLE_AUTO_COMMIT))
                        .getValue());
            }
        }
        return (boolean) consumerConfig.get(CONSUMER_ENABLE_AUTO_COMMIT_CONFIG);
    }

    public static boolean getAutoSeekOnErrorConfig(BObject bObject) {
        return (boolean) bObject.getMapValue(CONSUMER_CONFIG_FIELD_NAME).get(CONSUMER_ENABLE_AUTO_SEEK_CONFIG);
    }
}
