package io.github.bucket4j.redis;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandAsyncService;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.connection.ConnectionManager;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import io.github.bucket4j.tck.AbstractDistributedBucketTest;
import io.github.bucket4j.tck.ProxyManagerSpec;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.netty.util.internal.ThreadLocalRandom;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.UnifiedJedis;

/**
 * @author Vladimir Bukhtoyarov
 */
public class RedisClusterTest extends AbstractDistributedBucketTest {

    private static final Logger logger = LoggerFactory.getLogger(RedisClusterTest.class);
    private static final Integer[] CONTAINER_CLUSTER_PORTS = {7000, 7001, 7002, 7003, 7004, 7005};

    private static GenericContainer container;

    private static RedisClusterClient redisClient;

    private static JedisCluster jedisCluster;
    private static UnifiedJedis unifiedJedisCluster;

    private static ConnectionManager connectionManager;
    private static CommandAsyncExecutor commandExecutor;

    @BeforeAll
    public static void setup() {
        container = startRedisContainer();

        // Redisson
        connectionManager = createRedissonClient(container);
        commandExecutor = createRedissonExecutor(connectionManager);

        // Lettuce
        redisClient = createLettuceClient(container);
        jedisCluster = createJedisCluster(container);

        // Jedis
        unifiedJedisCluster = createUnifiedJedisCluster(container);

        specs = Arrays.asList(
            // Redisson
            new ProxyManagerSpec<>(
                "RedissonBasedProxyManager_NoExpiration_LongKey",
                () -> ThreadLocalRandom.current().nextLong(),
                RedissonBasedProxyManager.builderFor(commandExecutor)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.none())
                    .withKeyMapper(Mapper.LONG)
                    .build()
            ),
            new ProxyManagerSpec<>(
                "RedissonBasedProxyManager_FixedTtl_StringKey",
                () -> UUID.randomUUID().toString(),
                RedissonBasedProxyManager.builderFor(commandExecutor)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofSeconds(10)))
                    .build()
            ),
            new ProxyManagerSpec<>(
                "RedissonBasedProxyManager_FixedTtl_StringKey_withTimeout",
                () -> UUID.randomUUID().toString(),
                RedissonBasedProxyManager.builderFor(commandExecutor)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofSeconds(10)))
                    .withClientSideConfig(ClientSideConfig.getDefault().withRequestTimeout(Duration.ofSeconds(3)))
                    .build()
            ),

            // Lettuce
            new ProxyManagerSpec<>(
                "LettuceBasedProxyManager_NoExpiration_ByteArrayKey",
                () -> UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                LettuceBasedProxyManager.builderFor(redisClient)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.none())
                    .build()
            ),
            new ProxyManagerSpec<>(
                "LettuceBasedProxyManager_NoExpiration_ByteArrayKey_withTimeout",
                () -> UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                LettuceBasedProxyManager.builderFor(redisClient)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.none())
                    .withClientSideConfig(ClientSideConfig.getDefault().withRequestTimeout(Duration.ofSeconds(3)))
                    .build()
            ),

            // Jedis
            new ProxyManagerSpec<>(
                "JedisBasedProxyManager_NoExpiration_ByteArrayKey",
                () -> UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                JedisBasedProxyManager.builderFor(jedisCluster)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofSeconds(10)))
                    .build()
            ),
            new ProxyManagerSpec<>(
                    "JedisBasedProxyManager_UnifiedJedis_NoExpiration_ByteArrayKey",
                    () -> UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                    JedisBasedProxyManager.builderFor(unifiedJedisCluster)
                            .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofSeconds(10)))
                            .build()
            )
        );
    }

    @AfterAll
    public static void shutdown() {
        try {
            try {
                if (connectionManager != null) {
                    connectionManager.shutdown();
                }
            } finally {
                try {
                    if (redisClient != null) {
                        redisClient.shutdown();
                    }
                } finally {
                    try {
                        if (jedisCluster != null) {
                            jedisCluster.close();
                        }
                    } finally {
                        if (unifiedJedisCluster != null) {
                            unifiedJedisCluster.close();
                        }
                    }
                }
            }
        } finally {
            if (container != null) {
                container.close();
            }
        }
    }

    private static RedisClusterClient createLettuceClient(GenericContainer container) {
        List<RedisURI> clusterHosts = Arrays.stream(CONTAINER_CLUSTER_PORTS)
            .map(port -> RedisURI.create(container.getHost(), container.getMappedPort(port)))
            .collect(Collectors.toList());

        return RedisClusterClient.create(clusterHosts);
    }

    private static JedisCluster createJedisCluster(GenericContainer container) {
        List<HostAndPort> shards = new ArrayList<>();
        for (Integer containerClusterPort : CONTAINER_CLUSTER_PORTS) {
            String redisHost = container.getHost();
            Integer redisPort = container.getMappedPort(containerClusterPort);
            shards.add(new HostAndPort(redisHost, redisPort));
        }

        return new JedisCluster(new HashSet<>(shards));
    }

    private static ConnectionManager createRedissonClient(GenericContainer container) {
        Config config = new Config();
        ClusterServersConfig clusterConfig = config.useClusterServers();
        for (Integer containerClusterPort : CONTAINER_CLUSTER_PORTS) {
            String redisHost = container.getHost();
            Integer redisPort = container.getMappedPort(containerClusterPort);
            String redisUrl = "redis://" + redisHost + ":" + redisPort;
            clusterConfig.addNodeAddress(redisUrl);
        }
        return ConfigSupport.createConnectionManager(config);
    }

    private static CommandAsyncExecutor createRedissonExecutor(ConnectionManager connectionManager) {
        return new CommandAsyncService(connectionManager, null, RedissonObjectBuilder.ReferenceType.DEFAULT);
    }

    private static UnifiedJedis createUnifiedJedisCluster(GenericContainer container) {
        return createJedisCluster(container);
    }

    private static GenericContainer startRedisContainer() {
        // see this doc https://github.com/Grokzen/docker-redis-cluster
        GenericContainer genericContainer = new GenericContainer("grokzen/redis-cluster:6.0.7");

        genericContainer.withExposedPorts(CONTAINER_CLUSTER_PORTS);
        // start does not wait cluster availability
        genericContainer.start();

        List<RedisURI> clusterHosts = Arrays.stream(CONTAINER_CLUSTER_PORTS)
            .map(port -> RedisURI.create(genericContainer.getHost(), genericContainer.getMappedPort(port)))
            .collect(Collectors.toList());

        // need to wait until all nodes join to cluster
        for (int i = 0; i < 200; i++) {
            RedisClusterClient redisClientProbe = null;
            try {
                redisClientProbe = RedisClusterClient.create(clusterHosts);
                StatefulRedisClusterConnection<String, String> clusterConnection = redisClientProbe.connect();
                AtomicInteger availableSlots = new AtomicInteger();
                clusterConnection.getPartitions().forEach(partition -> partition.forEachSlot(slot -> availableSlots.incrementAndGet()));
                if (availableSlots.get() < SlotHash.SLOT_COUNT) {
                    throw new IllegalStateException("Only " + availableSlots.get() + " slots is ready from required " + SlotHash.SLOT_COUNT);
                }

                clusterConnection.sync().get("42");
                return genericContainer;
            } catch (Throwable e) {
                logger.error("Failed to check Redis Cluster availability: {}", e.getMessage(), e);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            } finally {
                if (redisClientProbe != null) {
                    redisClientProbe.shutdown();
                }
            }
        }

        throw new IllegalStateException("Cluster was not assembled in " + TimeUnit.MILLISECONDS.toSeconds(200 * 100) + " seconds");
    }


}
