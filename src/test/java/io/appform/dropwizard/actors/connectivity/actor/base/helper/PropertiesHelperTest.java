package io.appform.dropwizard.actors.connectivity.actor.base.helper;

import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.base.helper.PropertiesHelper;
import io.appform.dropwizard.actors.compression.CompressionAlgorithm;
import io.appform.dropwizard.actors.compression.CompressionConfig;
import io.appform.dropwizard.actors.utils.MessageHeaders;
import lombok.val;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

public class PropertiesHelperTest {

    private PropertiesHelper propertiesHelper;

    @Before
    public void setup() {

    }

    @Test
    public void testCreatePropertiesWithDelay() {
        val config = ActorConfig.builder().build();
        propertiesHelper = new PropertiesHelper(config);
        long delay = 1000;
        val properties = propertiesHelper.createPropertiesWithDelay(delay, false, false);

        Assert.assertTrue(properties.getHeaders().containsKey(MessageHeaders.DELAY));
        Assert.assertEquals(delay, properties.getHeaders().get(MessageHeaders.DELAY));
        Assert.assertEquals(2L, (long)properties.getDeliveryMode());
        Assert.assertNull(properties.getExpiration());
    }

    @Test
    public void testCreatePropertiesWithDelayWithTtlEnabled() {
        val config = ActorConfig.builder()
                .ttlConfig(TtlConfig.builder()
                        .ttlEnabled(true)
                        .build())
                .delayType(DelayType.TTL)
                .build();

        propertiesHelper = new PropertiesHelper(config);
        long delay = 1000;
        val properties = propertiesHelper.createPropertiesWithDelay(delay, false, true);

        Assert.assertTrue(properties.getHeaders().isEmpty());
        Assert.assertEquals(2L, (long)properties.getDeliveryMode());
        Assert.assertEquals(String.valueOf(delay), properties.getExpiration());
    }

    @Test
    public void testCreatePropertiesWithDelayWithCompression() {
        val config = ActorConfig.builder()
                .compressionConfig(CompressionConfig.builder()
                        .enableCompression(true)
                        .compressionAlgorithm(CompressionAlgorithm.GZIP)
                        .build())
                .build();
        propertiesHelper = new PropertiesHelper(config);

        long delay = 1000;
        val properties = propertiesHelper.createPropertiesWithDelay(delay, true, false);

        Assert.assertTrue(properties.getHeaders().containsKey(MessageHeaders.DELAY));
        Assert.assertEquals(delay, properties.getHeaders().get(MessageHeaders.DELAY));
        Assert.assertTrue(properties.getHeaders().containsKey(MessageHeaders.COMPRESSION_TYPE));
        Assert.assertEquals(CompressionAlgorithm.GZIP, properties.getHeaders().get(MessageHeaders.COMPRESSION_TYPE));
        Assert.assertEquals(2L, (long)properties.getDeliveryMode());
        Assert.assertNull(properties.getExpiration());

    }

    @Test
    public void testCreatePropertiesWithDelayWithCompressionAndTtlEnabled() {
        val config = ActorConfig.builder()
                .ttlConfig(TtlConfig.builder()
                        .build())
                .compressionConfig(CompressionConfig.builder()
                        .enableCompression(true)
                        .compressionAlgorithm(CompressionAlgorithm.GZIP)
                        .build())
                .delayType(DelayType.TTL)
                .build();

        propertiesHelper = new PropertiesHelper(config);
        long delay = 1000;
        val properties = propertiesHelper.createPropertiesWithDelay(delay, true, true);

        Assert.assertTrue(properties.getHeaders().containsKey(MessageHeaders.COMPRESSION_TYPE));
        Assert.assertEquals(CompressionAlgorithm.GZIP, properties.getHeaders().get(MessageHeaders.COMPRESSION_TYPE));
        Assert.assertEquals(2L, (long)properties.getDeliveryMode());
        Assert.assertEquals(String.valueOf(delay), properties.getExpiration());
    }

}
