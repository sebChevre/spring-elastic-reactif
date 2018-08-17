package ch.globaz.tmmas.rechercheservice.infrastructure;

import static ch.globaz.tmmas.rechercheservice.application.configuration.ElasticSearchIndexes.*;

import ch.globaz.tmmas.rechercheservice.application.configuration.ElasticSearchIndexes;
import ch.globaz.tmmas.rechercheservice.domaine.Personne;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

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

    @Autowired
    private final RestHighLevelClient client;
    @Autowired
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
                        client.getAsync(new GetRequest(PERSONNES.index(), PERSONNES.type(), userName),
                                listenerToMonoElement(element))
                )
                .filter(GetResponse::isExists)
                .map(GetResponse::getSource)
                .map(map -> objectMapper.convertValue(map, Personne.class));
    }

    public Mono<List<Personne>> recherche(String methode,String terme){

        log.debug("Search with methode : {} and terme: {}",methode,terme);

        switch (methode){
            case "fuzzy":
                log.debug("fuzzy search");
                return fuzzy(fuzzySearchRequest(terme, ElasticSearchIndexes.PERSONNES.index()));
            case "wildcards":
                log.debug("wildcard search");
                return wildCard(wildCardsSearchRequest(terme,ElasticSearchIndexes.PERSONNES.index()));
            case "composed":
                log.debug("composed search");
                return composed(composedSearchRequest(terme,ElasticSearchIndexes.PERSONNES.index()));
            default:
                throw new IllegalArgumentException("methode name specified error : " + methode);
        }

    }

    private Mono<List<Personne>> composed(MultiSearchRequest searchRequest) {

        return Mono.<MultiSearchResponse>create(element ->{
            //appel asynchrone via le client es
            log.info("Async multisearch call");
            client.multiSearchAsync(searchRequest, listenerToMonoElement(element));
        })
        .filter(resp ->{
            //pas de reultats on ne mappe rien
            log.info("Filter resulats, size : {}",resp.getResponses().length);
            return resp.getResponses().length > 0;
        })
        .map(multiReponse -> {

            MultiSearchResponse.Item first = multiReponse.getResponses()[0];
            log.info("First multisearch response {}",first.getResponse().getHits().totalHits);
            MultiSearchResponse.Item second = multiReponse.getResponses()[1];
            log.info("Second multisearch response {}",second.getResponse().getHits().totalHits);

            Flux<SearchHit> firstFluxSearchHit = Flux.fromIterable(Arrays.asList(first.getResponse().getHits()
                    .getHits()));
            Flux<SearchHit> secondFluxSearchHit = Flux.fromIterable(Arrays.asList(second.getResponse().getHits()
                    .getHits()));


            Flux<SearchHit> searchHitsFlux = Flux.concat(firstFluxSearchHit, secondFluxSearchHit);

            List<Personne> personnes = new ArrayList<>();

            log.info("Async multisearch composed flux iteration");
            searchHitsFlux
                    .map(hit -> {
                        try {
                            log.info("Async multisearch composed flux iteration for serialzation:; {}",hit.getSourceAsString());
                            return objectMapper.readValue(hit.getSourceAsString(), Personne.class);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .subscribe(personnes::add);


            return personnes
                    .stream()
                    .distinct()
                    .collect(Collectors.toList());
        });

    }


    private Mono<List<Personne>> wildCard(SearchRequest searchRequest) {

        return Mono.<SearchResponse>create(element ->
                //appel asynchrone via le client es
                client.searchAsync(searchRequest, listenerToMonoElement(element))
        )
        .filter(resp ->
                //pas de reultats on ne mappe rien
                resp.getHits().totalHits > 0
        )
        .map(map -> {

            List<Personne> personnes = new ArrayList<>();

            return Arrays.asList(map.getHits().getHits()).stream()
                    .map(hit -> {
                        log.info(hit.getSourceAsString());
                        try {
                            return objectMapper.readValue(hit.getSourceAsString(),Personne.class);
                        } catch (IOException e) {
                            log.error("IO Exception when deserialising hit :" + hit.getSourceAsString());
                            return null;
                        }
                    })
                    .collect(Collectors.toList());
        });

    }

    public Mono<List<Personne>> fuzzy(SearchRequest searchRequest) {


        return Mono.<SearchResponse>create(element ->
            //appel asynchrone via le client es
            client.searchAsync(searchRequest,
                    listenerToMonoElement(element))
        )
        .filter(resp ->
            //pas de reultats on ne mappe rien
                resp.getHits().totalHits > 0
        )
        .map(map -> {

            List<Personne> personnes = new ArrayList<>();

            return Arrays.asList(map.getHits().getHits()).stream()
                    .map(hit -> {
                        log.info(hit.getSourceAsString());
                        try {
                            return objectMapper.readValue(hit.getSourceAsString(),Personne.class);
                        } catch (IOException e) {
                            log.error("IO Exception when deserialising hit :" + hit.getSourceAsString());
                            return null;
                        }
                    })
                    .collect(Collectors.toList());

           /* map.getHits().forEach(hit -> {
                log.info(hit.getSourceAsString());
                try {
                    personnes.add(objectMapper.readValue(hit.getSourceAsString(),Personne.class));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });


            return personnes;*/
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

    /**
     * Indexe un élément de type {@code Personne}
     * @param doc le document à indéxer
     * @return une Mono contenant potentiellement la réponse
     */
    public Mono<BulkResponse> bulkIndex(List<Personne> docs) {
        return bulkIndexDocs(docs)
                .doOnError(e -> log.error("Unable to bulkindex {}", docs, e));
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
    private Mono<BulkResponse> bulkIndexDocs(List<Personne> docs) {
        return Mono.create(sink -> {
            try {
                doBulkIndex(docs, listenerToMonoElement(sink));
            } catch (JsonProcessingException e) {
                sink.error(e);
            }
        });
    }


    private void doIndex(Personne doc, ActionListener<IndexResponse> listener) throws JsonProcessingException {
        final IndexRequest indexRequest = new IndexRequest(PERSONNES.index(), PERSONNES.type(), doc.getUsername());
        final String json = objectMapper.writeValueAsString(doc);
        indexRequest.source(json, XContentType.JSON);
        client.indexAsync(indexRequest, listener);
    }

    private void doBulkIndex(List<Personne> docs, ActionListener<BulkResponse> listener) throws
            JsonProcessingException {
        BulkRequest bulkRequest = new BulkRequest();

        docs.forEach(doc -> {

            try {

                IndexRequest indexRequest = new IndexRequest(PERSONNES.index(), PERSONNES.type(), doc.getUsername());
                String json = objectMapper.writeValueAsString(doc);
                indexRequest.source(json, XContentType.JSON);
                bulkRequest.add(indexRequest);

            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });

        client.bulkAsync(bulkRequest,listener);
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
                log.debug("OnResponse: {}", response);
                element.success(response);
            }

            @Override
            public void onFailure(Exception e) {
                log.debug("OnFailure: {}", e.getMessage());
                element.error(e);
            }
        };
    }

    private SearchRequest wildCardsSearchRequest(String terme, String index) {

        QueryBuilder wildCardQueryBuilder = QueryBuilderFactory.wildCarsdQueryBuilder(terme);

        return new SearchRequest()
                .indices(index)
                .source(new SearchSourceBuilder().query(wildCardQueryBuilder).from(0).size(5));

    }

    private MultiSearchRequest composedSearchRequest(String terme, String index) {

        MultiSearchRequest multiSearchRequest  = new MultiSearchRequest();
        multiSearchRequest.add(wildCardsSearchRequest(terme,index));
        multiSearchRequest.add(fuzzySearchRequest(terme,index));

        return multiSearchRequest;
    }


    private SearchRequest fuzzySearchRequest(String terme, String index){

        QueryBuilder fuzzyCardQueryBuilder = QueryBuilderFactory.fuzzyQueryBuilder(terme);

        return new SearchRequest()
                .indices(index)
                .source(new SearchSourceBuilder().query(fuzzyCardQueryBuilder).from(0).size(5));


    }


}
