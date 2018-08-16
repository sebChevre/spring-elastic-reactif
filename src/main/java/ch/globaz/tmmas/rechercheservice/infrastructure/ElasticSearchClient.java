package ch.globaz.tmmas.rechercheservice.infrastructure;

import ch.globaz.tmmas.rechercheservice.domaine.Personne;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Client elasticsearch réactif "adapté". <br/>
 * - les repository de Spring Data Elasticsearch ne supportent pas les appels réactifs (non bloquants)<br/>
 * - L'API > 6 d'ElasticSearch supporte les appels non-bloquants mais sous forme de callback<br/>
 * - Afin d'éviter de se retrouver dans une callback hell, ce client va encapsuler les callback dans de flux réactif (Mono ou Flux)<br/>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ElasticSearchClient {
    private final RestHighLevelClient client;
    private final ObjectMapper objectMapper;

    private final Timer indexTimer = Metrics.timer("es.timer");
    private final LongAdder concurrent = Metrics.gauge("es.concurrent", new LongAdder());
    private final Counter successes = Metrics.counter("es.index", "result", "success");
    private final Counter failures = Metrics.counter("es.index", "result", "failure");

    /**
     * Recherche un document pas son username (id es)
     * @param userName le nom d'utilisateur recherché
     * @return une instance de Mono contenant potentiellement l'élément
     */
    public Mono<Personne> findByUserName(String userName) {

        return Mono.<GetResponse>create(element ->
                        //appel asynchrone via le client es
                        client.getAsync(new GetRequest("personne", "personne", userName), listenerToMonoElement(element))
                )
                .filter(GetResponse::isExists)
                .map(GetResponse::getSource)
                .map(map -> objectMapper.convertValue(map, Personne.class));
    }

    public Mono<List<Personne>> fuzzyByUserName(String userName) {

        QueryBuilder matchQueryBuilder = QueryBuilders.matchQuery("username", "sce")
                .fuzziness(Fuzziness.AUTO)
                .prefixLength(3)
                .maxExpansions(10);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(matchQueryBuilder);
        sourceBuilder.from(0);
        sourceBuilder.size(5);

        SearchRequest request = new SearchRequest();
        request.indices("personne");
        request.source(sourceBuilder);

        CollectionType javaType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, Personne.class);

        return Mono.<SearchResponse>create(element ->
                //appel asynchrone via le client es


                client.searchAsync(request, listenerToMonoElement(element))

        )
        .filter(resp -> {
            return resp.getHits().totalHits > 0;
        })
        .map(map -> {

            List<Personne> personnes = new ArrayList<>();

            map.getHits().forEach(hit -> {
                log.info(hit.getSourceAsString());
                try {
                    personnes.add(objectMapper.readValue(hit.getSourceAsString(),Personne.class));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            //Arrays.asList(map.getHits()).forEach(el -> log.info(el.getS));



            /**List<Personne> perss = objectMapper.convertValue(tc, javaType);

            perss.forEach(el -> log.info(el.toString()));
            return perss;*/

            return personnes;
        });
    }

    /**
     * Indexe un élément de type {@code Personne}
     * @param doc le document à indéxer
     * @return une Mono contenant potentiellement la réponse
     */
    public Mono<IndexResponse> index(Personne doc) {
        return indexDoc(doc)
                .compose(this::countSuccFail)
                .compose(this::countConcurrent)
                .compose(this::measureTime)
                .doOnError(e -> log.error("Unable to index {}", doc, e));
    }

    private Mono<IndexResponse> countConcurrent(Mono<IndexResponse> mono) {
        return mono
                .doOnSubscribe(s -> concurrent.increment())
                .doOnTerminate(concurrent::decrement);
    }

    private Mono<IndexResponse> measureTime(Mono<IndexResponse> mono) {
        return Mono
                .fromCallable(System::currentTimeMillis)
                .flatMap(time ->
                        mono.doOnSuccess(response ->
                                indexTimer.record(System.currentTimeMillis() - time, TimeUnit.MILLISECONDS))
                );
    }

    private Mono<IndexResponse> countSuccFail(Mono<IndexResponse> mono) {
        return mono
                .doOnError(e -> failures.increment())
                .doOnSuccess(response -> successes.increment());
    }

    private Mono<IndexResponse> indexDoc(Personne doc) {
        return Mono.create(sink -> {
            try {
                doIndex(doc, listenerToMonoElement(sink));
            } catch (JsonProcessingException e) {
                sink.error(e);
            }
        });
    }

    private void doIndex(Personne doc, ActionListener<IndexResponse> listener) throws JsonProcessingException {
        final IndexRequest indexRequest = new IndexRequest("personne", "personne", doc.getUsername());
        final String json = objectMapper.writeValueAsString(doc);
        indexRequest.source(json, XContentType.JSON);
        client.indexAsync(indexRequest, listener);
    }

    /**
     * Callback du client es de base. Le listener traite le mono retourné.
     * @param element l'élément potentiel retourné (un Mono retourn o à 1 élément)
     * @param <T> le type de l'élément
     * @return une instance de {@code ActionListener}
     */
    private <T> ActionListener<T> listenerToMonoElement(MonoSink<T> element) {
        return new ActionListener<T>() {
            @Override
            public void onResponse(T response) {
                element.success(response);
            }

            @Override
            public void onFailure(Exception e) {
                element.error(e);
            }
        };
    }

}
