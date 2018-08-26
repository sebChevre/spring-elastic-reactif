package ch.globaz.tmmas.rechercheservice.infrastructure;

import static ch.globaz.tmmas.rechercheservice.application.configuration.ElasticSearchIndexes.*;

import ch.globaz.tmmas.rechercheservice.application.configuration.ElasticSearchIndexes;
import ch.globaz.tmmas.rechercheservice.domaine.Personne;
import ch.globaz.tmmas.rechercheservice.domaine.PersonneIndex;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public ResponseEntity findByUserName(String userName) throws IOException {

        GetResponse getResponse = client.get(new GetRequest(PERSONNES.index(), PERSONNES.type(), userName));

        if(getResponse.isExists()){
            return new ResponseEntity<Personne>(objectMapper.convertValue(getResponse.getSource(),Personne.class),
                    HttpStatus.OK);
        }
         return ResponseEntity.notFound().build();
    }

    public List<Personne> recherche(String methode,String terme) throws IOException {

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

    private List<Personne> composed(MultiSearchRequest searchRequest) throws IOException {

        MultiSearchResponse composerResponse = client.multiSearch(searchRequest);

        MultiSearchResponse.Item first = composerResponse.getResponses()[0];
        log.info("First multisearch response {}",first.getResponse().getHits().totalHits);

        MultiSearchResponse.Item second = composerResponse.getResponses()[1];
        log.info("Second multisearch response {}",second.getResponse().getHits().totalHits);

        Stream<SearchHit> firstStream = Stream.of(first.getResponse().getHits().getHits());
        Stream<SearchHit> secondStream = Stream.of(second.getResponse().getHits().getHits());


        List<Personne> personnes = Stream.concat(firstStream,secondStream)
                .map(hit -> {
                    log.info("Sync multisearch composed flux iteration for serialzation:; {}",hit.getSourceAsString());
                    try {
                        return objectMapper.readValue(hit.getSourceAsString(), Personne.class);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).collect(Collectors.toList());



            return personnes
                    .stream()
                    .distinct()
                    .collect(Collectors.toList());

    }


    private List<Personne> wildCard(SearchRequest searchRequest) throws IOException {


        SearchResponse response = client.search(searchRequest);

        Stream<SearchHit> fluxWildCards = Stream.of(response.getHits().getHits());

        return  fluxWildCards.map(hit -> {
            log.info(hit.getSourceAsString());
            try {
                return objectMapper.readValue(hit.getSourceAsString(),Personne.class);
            } catch (IOException e) {
                log.error("IO Exception when deserialising hit :" + hit.getSourceAsString());
                return null;
            }
        }).collect(Collectors.toList());



    }

    public List<Personne> fuzzy(SearchRequest searchRequest) throws IOException {


        SearchResponse response = client.search(searchRequest);

        Stream<SearchHit> fluxWildCards = Stream.of(response.getHits().getHits());

        return  fluxWildCards.map(hit -> {
            log.info(hit.getSourceAsString());
            try {
                return objectMapper.readValue(hit.getSourceAsString(),Personne.class);
            } catch (IOException e) {
                log.error("IO Exception when deserialising hit :" + hit.getSourceAsString());
                return null;
            }
        }).collect(Collectors.toList());

    }

    /**
     * Indexe un élément de type {@code Personne}
     * @param doc le document à indéxer
     * @return une Mono contenant potentiellement la réponse
     */
    public IndexResponse index(PersonneIndex doc) throws IOException {
        return indexDoc(doc);
    }

    /**
     * Indexe un élément de type {@code Personne}
     * @param docs le document à indéxer
     * @return une Mono contenant potentiellement la réponse
     */
    public BulkResponse bulkIndex(List<Personne> docs) throws IOException {
        return bulkIndexDocs(docs);
    }

    /**
    private IndexResponse countConcurrent(IndexResponse response) {
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

     */

    private IndexResponse indexDoc(PersonneIndex doc) throws IOException {
        return doIndex(doc);

    }
    private BulkResponse bulkIndexDocs(List<Personne> docs) throws IOException {
         return doBulkIndex(docs);

    }


    private IndexResponse doIndex(PersonneIndex doc) throws IOException {
        final IndexRequest indexRequest = new IndexRequest(PERSONNES.index(), PERSONNES.type(), doc.getNss());
        final String json = objectMapper.writeValueAsString(doc);
        indexRequest.source(json, XContentType.JSON);
        log.info("OK ES");
        return client.index(indexRequest);

    }

    private BulkResponse doBulkIndex(List<Personne> docs) throws
            IOException {
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

        return client.bulk(bulkRequest,null);
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
