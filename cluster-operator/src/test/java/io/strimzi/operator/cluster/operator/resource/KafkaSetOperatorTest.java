/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.operator.resource;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaBuilder;
import io.strimzi.operator.cluster.ResourceUtils;
import io.strimzi.operator.cluster.model.KafkaCluster;
import io.strimzi.operator.cluster.model.KafkaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.strimzi.operator.cluster.model.AbstractModel.containerEnvVars;
import static io.strimzi.operator.cluster.model.KafkaCluster.ENV_VAR_KAFKA_ZOOKEEPER_CONNECT;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class KafkaSetOperatorTest {

    public static final InlineLogging KAFKA_LOG_CONFIG = new InlineLogging();
    public static final InlineLogging ZOOKEEPER_LOG_CONFIG = new InlineLogging();
    static {
        KAFKA_LOG_CONFIG.setLoggers(singletonMap("zookeeper.root.logger", "OFF"));
        ZOOKEEPER_LOG_CONFIG.setLoggers(singletonMap("kafka.root.logger.level", "OFF"));
    }

    private StatefulSet currectSts;
    private StatefulSet desiredSts;

    @BeforeEach
    public void before() {
        KafkaVersion.Lookup versions = new KafkaVersion.Lookup(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap());
        currectSts = KafkaCluster.fromCrd(getResource(), versions).generateStatefulSet(true, null, null);
        desiredSts = KafkaCluster.fromCrd(getResource(), versions).generateStatefulSet(true, null, null);
    }

    private Kafka getResource() {
        String clusterCmName = "foo";
        String clusterCmNamespace = "test";
        int replicas = 3;
        String image = "bar";
        int healthDelay = 120;
        int healthTimeout = 30;
        return new KafkaBuilder(ResourceUtils.createKafkaCluster(clusterCmNamespace, clusterCmName,
                replicas, image, healthDelay, healthTimeout))
                .editSpec()
                    .editKafka()
                        .withNewPersistentClaimStorage()
                            .withSize("123")
                            .withStorageClass("foo")
                            .withDeleteClaim(true)
                            .endPersistentClaimStorage()
                        .withLogging(KAFKA_LOG_CONFIG)
                    .endKafka()
                    .editZookeeper()
                        .withLogging(ZOOKEEPER_LOG_CONFIG)
                    .endZookeeper()
                .endSpec()
            .build();
    }

    private StatefulSetDiff createDiff() {
        return new StatefulSetDiff(currectSts, desiredSts);
    }

    @Test
    public void testNotNeedsRollingUpdateWhenIdentical() {
        assertThat(KafkaSetOperator.needsRollingUpdate(createDiff()), is(false));
    }

    @Test
    public void testNotNeedsRollingUpdateWhenReplicasDecrease() {
        currectSts.getSpec().setReplicas(desiredSts.getSpec().getReplicas() + 1);
        assertThat(KafkaSetOperator.needsRollingUpdate(createDiff()), is(false));
    }

    @Test
    public void testNeedsRollingUpdateWhenLabelsRemoved() {
        Map<String, String> labels = new HashMap(desiredSts.getMetadata().getLabels());
        labels.put("foo", "bar");
        currectSts.getMetadata().setLabels(labels);
        assertThat(KafkaSetOperator.needsRollingUpdate(createDiff()), is(true));
    }

    @Test
    public void testNeedsRollingUpdateWhenImageChanges() {
        String newImage = currectSts.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() + "-foo";
        currectSts.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(newImage);
        assertThat(KafkaSetOperator.needsRollingUpdate(createDiff()), is(true));
    }

    @Test
    public void testNeedsRollingUpdateWhenReadinessDelayChanges() {
        Integer newDelay = currectSts.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe().getInitialDelaySeconds() + 1;
        currectSts.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe().setInitialDelaySeconds(newDelay);
        assertThat(KafkaSetOperator.needsRollingUpdate(createDiff()), is(true));
    }

    @Test
    public void testNeedsRollingUpdateWhenReadinessTimeoutChanges() {
        Integer newTimeout = currectSts.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe().getTimeoutSeconds() + 1;
        currectSts.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe().setTimeoutSeconds(newTimeout);
        assertThat(KafkaSetOperator.needsRollingUpdate(createDiff()), is(true));
    }

    @Test
    public void testNeedsRollingUpdateWhenEnvZkConnectChanges() {
        String envVar = ENV_VAR_KAFKA_ZOOKEEPER_CONNECT;
        String newEnvVarValue = containerEnvVars(currectSts.getSpec().getTemplate().getSpec().getContainers().get(1))
            .get(envVar) + "-foo";
        EnvVar newEnvVar = new EnvVar(envVar, newEnvVarValue, null);
        currectSts.getSpec().getTemplate().getSpec().getContainers().get(1).getEnv().add(newEnvVar);
        assertThat(KafkaSetOperator.needsRollingUpdate(createDiff()), is(true));
    }

    @Test
    public void testNeedsRollingUpdateWhenNewEnvRemoved() {
        String envVar = "SOME_RANDOM_ENV";
        currectSts.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().add(new EnvVar(envVar,
                "foo", null));
        assertThat(KafkaSetOperator.needsRollingUpdate(createDiff()), is(true));
    }
}
