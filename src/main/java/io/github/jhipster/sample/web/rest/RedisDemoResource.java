package io.github.jhipster.sample.web.rest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller to look inside the Redis cache service and to demonstrate plain key-value storage with a TTL.
 * <p>
 * Mapped under {@code /api/admin/**}, so only users with the {@code ROLE_ADMIN} authority can call it.
 */
@RestController
@RequestMapping("/api/admin/redis")
public class RedisDemoResource {

    private static final Logger LOG = LoggerFactory.getLogger(RedisDemoResource.class);

    private static final String DEMO_KEY_PREFIX = "demo:";

    private static final int MAX_KEYS = 500;

    private final RedissonClient redissonClient;

    public RedisDemoResource(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * {@code GET  /admin/redis/keys} : list the keys stored in Redis, with their type and remaining time to live.
     *
     * @param pattern a Redis glob pattern, for example {@code *labelsList*}.
     * @param limit the maximum number of keys to return.
     * @return the keys found.
     */
    @GetMapping("/keys")
    public Map<String, Object> getKeys(@RequestParam(defaultValue = "*") String pattern, @RequestParam(defaultValue = "100") int limit) {
        LOG.debug("REST request to list Redis keys matching {}", pattern);
        RKeys keys = redissonClient.getKeys();
        List<Map<String, Object>> found = new ArrayList<>();
        for (String name : keys.getKeys(KeysScanOptions.defaults().pattern(pattern).limit(Math.min(limit, MAX_KEYS)))) {
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("key", name);
            key.put("type", keys.getType(name));
            key.put("ttlMillis", keys.remainTimeToLive(name));
            found.add(key);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeysInDatabase", keys.count());
        result.put("keys", found);
        return result;
    }

    /**
     * {@code PUT  /admin/redis/demo/:key} : store a string value under {@code demo:<key>} that expires after {@code ttlSeconds}.
     *
     * @param key the key, without the {@code demo:} prefix.
     * @param ttlSeconds the time to live in seconds.
     * @param value the value to store (request body, plain text).
     * @return the stored entry.
     */
    @PutMapping(value = "/demo/{key}", consumes = "text/plain")
    public Map<String, Object> putDemoValue(
        @PathVariable("key") String key,
        @RequestParam(defaultValue = "60") long ttlSeconds,
        @RequestBody String value
    ) {
        LOG.debug("REST request to store Redis demo key {} for {} seconds", key, ttlSeconds);
        bucket(key).set(value, Duration.ofSeconds(ttlSeconds));
        return describe(key);
    }

    /**
     * {@code GET  /admin/redis/demo/:key} : read {@code demo:<key>} and its remaining time to live.
     *
     * @param key the key, without the {@code demo:} prefix.
     * @return the entry, or {@code 404 (Not Found)} once it has expired.
     */
    @GetMapping("/demo/{key}")
    public ResponseEntity<Map<String, Object>> getDemoValue(@PathVariable("key") String key) {
        LOG.debug("REST request to read Redis demo key {}", key);
        if (!bucket(key).isExists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(describe(key));
    }

    /**
     * {@code DELETE  /admin/redis/demo/:key} : delete {@code demo:<key>}.
     *
     * @param key the key, without the {@code demo:} prefix.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/demo/{key}")
    public ResponseEntity<Void> deleteDemoValue(@PathVariable("key") String key) {
        LOG.debug("REST request to delete Redis demo key {}", key);
        bucket(key).delete();
        return ResponseEntity.noContent().build();
    }

    private RBucket<String> bucket(String key) {
        return redissonClient.getBucket(DEMO_KEY_PREFIX + key, StringCodec.INSTANCE);
    }

    private Map<String, Object> describe(String key) {
        RBucket<String> bucket = bucket(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", DEMO_KEY_PREFIX + key);
        result.put("value", bucket.get());
        result.put("ttlMillis", bucket.remainTimeToLive());
        return result;
    }
}
