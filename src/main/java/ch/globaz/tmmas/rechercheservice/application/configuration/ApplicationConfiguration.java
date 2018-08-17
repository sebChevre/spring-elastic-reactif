package ch.globaz.tmmas.rechercheservice.application.configuration;

import ch.globaz.tmmas.rechercheservice.infrastructure.generator.ESMetrics;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Classe de configuration principale de l'application
 */

@Configuration
class ApplicationConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }

    /**
     * Client REST pour ElasticSearch haut niveau.
     * Voir <a href="https://www.elastic.co/blog/the-elasticsearch-java-high-level-rest-client-is-out">
     *     RestHighLevelClient doc</a><br/>
     * Gère en interne un pool de thread pour les clients bas niveau.
     * @param props
     * @return une instance de
     */
    @Bean
    RestHighLevelClient restHighLevelClient(ElasticSearchProperties props) {
        return new RestHighLevelClient(
                RestClient
                        .builder(props.hosts())
                        .setRequestConfigCallback(config -> config
                                .setConnectTimeout(props.getConnectTimeout())
                                .setConnectionRequestTimeout(props.getConnectionRequestTimeout())
                                .setSocketTimeout(props.getSocketTimeout())
                        )
                        .setMaxRetryTimeoutMillis(props.getMaxRetryTimeoutMillis()));
    }


    @Bean
    @ConditionalOnProperty(value = "spring.metrics.binders.jvmthreads.enabled", matchIfMissing = true)
    @ConditionalOnMissingBean(JvmThreadMetrics.class)
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.binders.jvmgc.enabled", matchIfMissing = true)
    @ConditionalOnMissingBean(JvmGcMetrics.class)
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    @Bean
    MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    Slf4jReporter slf4jReporter() {
        final Slf4jReporter slf4jReporter = Slf4jReporter.forRegistry(metricRegistry()).build();
        slf4jReporter.start(1, TimeUnit.SECONDS);
        return slf4jReporter;
    }

    @Bean
    GraphiteReporter graphiteReporter(MetricRegistry metricRegistry) {
        final Graphite graphite = new Graphite(new InetSocketAddress("localhost", 2003));
        final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith("elastic-flux")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build(graphite);
        reporter.start(1, TimeUnit.SECONDS);
        return reporter;
    }

}
