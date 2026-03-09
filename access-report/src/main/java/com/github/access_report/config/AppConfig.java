package com.github.access_report.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {

    private final GitHubProperties props;

    public AppConfig(GitHubProperties props) {
        this.props = props;
    }

    /**
     * RestTemplate — shared HTTP client for all GitHub API calls.
     * Thread-safe, so one instance is reused across all requests.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    /**
     * Thread pool for parallel GitHub API calls.
     *
     * Why 20 threads?
     * - GitHub rate limit: 5,000 requests/hour for authenticated users
     * - With 100 repos, we fire 100 collaborator calls in parallel
     * - 20 threads gives good speed without overwhelming the rate limit
     * - Queue of 500 handles burst scenarios safely
     */
    @Bean(name = "githubTaskExecutor")
    public Executor githubTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("github-api-");
        executor.initialize();
        return executor;
    }

    /**
     * Caffeine cache manager.
     *
     * Spring Cache Abstraction (from start.spring.io) provides @Cacheable annotation.
     * Caffeine is the actual storage engine behind it.
     *
     * Cache expires after 5 minutes — keeps data reasonably fresh
     * while staying well within GitHub's rate limits.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .recordStats()
        );
        return manager;
    }
}