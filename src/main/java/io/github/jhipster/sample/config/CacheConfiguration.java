package io.github.jhipster.sample.config;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import org.hibernate.cache.jcache.ConfigSettings;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.jcache.configuration.RedissonConfiguration;
import org.springframework.boot.cache.autoconfigure.JCacheManagerCustomizer;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableCaching
public class CacheConfiguration {

    public static final String LABELS_LIST_CACHE = "labelsList";

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(JHipsterProperties jHipsterProperties, ApplicationProperties applicationProperties) {
        URI redisUri = URI.create(jHipsterProperties.getCache().getRedis().getServer()[0]);
        String password = applicationProperties.getRedis().getPassword();
        if (!StringUtils.hasText(password) && redisUri.getUserInfo() != null) {
            password = redisUri.getUserInfo().substring(redisUri.getUserInfo().indexOf(':') + 1);
        }

        Config config = new Config();
        // Fix Hibernate lazy initialization https://github.com/jhipster/generator-jhipster/issues/22889
        config.setCodec(new org.redisson.codec.SerializationCodec());
        if (jHipsterProperties.getCache().getRedis().isCluster()) {
            ClusterServersConfig clusterServersConfig = config
                .useClusterServers()
                .setMasterConnectionPoolSize(jHipsterProperties.getCache().getRedis().getConnectionPoolSize())
                .setMasterConnectionMinimumIdleSize(jHipsterProperties.getCache().getRedis().getConnectionMinimumIdleSize())
                .setSubscriptionConnectionPoolSize(jHipsterProperties.getCache().getRedis().getSubscriptionConnectionPoolSize())
                .addNodeAddress(jHipsterProperties.getCache().getRedis().getServer());

            if (StringUtils.hasText(password)) {
                clusterServersConfig.setPassword(password);
            }
        } else {
            SingleServerConfig singleServerConfig = config
                .useSingleServer()
                .setConnectionPoolSize(jHipsterProperties.getCache().getRedis().getConnectionPoolSize())
                .setConnectionMinimumIdleSize(jHipsterProperties.getCache().getRedis().getConnectionMinimumIdleSize())
                .setSubscriptionConnectionPoolSize(jHipsterProperties.getCache().getRedis().getSubscriptionConnectionPoolSize())
                .setAddress(jHipsterProperties.getCache().getRedis().getServer()[0]);

            if (StringUtils.hasText(password)) {
                singleServerConfig.setPassword(password);
            }
        }
        return Redisson.create(config);
    }

    @Bean
    public javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration(
        JHipsterProperties jHipsterProperties,
        RedissonClient redissonClient
    ) {
        MutableConfiguration<Object, Object> jcacheConfig = new MutableConfiguration<>();
        jcacheConfig.setStatisticsEnabled(true);
        jcacheConfig.setExpiryPolicyFactory(
            CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, jHipsterProperties.getCache().getRedis().getExpiration()))
        );
        return RedissonConfiguration.fromInstance(redissonClient, jcacheConfig);
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(javax.cache.CacheManager cm) {
        return hibernateProperties -> hibernateProperties.put(ConfigSettings.CACHE_MANAGER, cm);
    }

    @Bean
    public JCacheManagerCustomizer cacheManagerCustomizer(javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration) {
        return cm -> {
            createCache(cm, io.github.jhipster.sample.repository.UserRepository.USERS_BY_LOGIN_CACHE, jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.repository.UserRepository.USERS_BY_EMAIL_CACHE, jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.User.class.getName(), jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.Authority.class.getName(), jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.User.class.getName() + ".authorities", jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.BankAccount.class.getName(), jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.BankAccount.class.getName() + ".operations", jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.Label.class.getName(), jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.Label.class.getName() + ".operations", jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.Operation.class.getName(), jcacheConfiguration);
            createCache(cm, io.github.jhipster.sample.domain.Operation.class.getName() + ".labels", jcacheConfiguration);
            createCache(cm, LABELS_LIST_CACHE, jcacheConfiguration);
            // jhipster-needle-redis-add-entry
        };
    }

    private void createCache(
        javax.cache.CacheManager cm,
        String cacheName,
        javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration
    ) {
        javax.cache.Cache<Object, Object> cache = cm.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        } else {
            cm.createCache(cacheName, jcacheConfiguration);
        }
    }
}
