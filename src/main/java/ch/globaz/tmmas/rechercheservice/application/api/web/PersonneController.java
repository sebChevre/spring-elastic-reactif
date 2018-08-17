package ch.globaz.tmmas.rechercheservice.application.api.web;

import ch.globaz.tmmas.rechercheservice.domaine.Personne;
import ch.globaz.tmmas.rechercheservice.infrastructure.ElasticSearchClient;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/personnes")
public class PersonneController {

    private static final Mono<ResponseEntity<Personne>> NOT_FOUND = Mono.just(ResponseEntity.notFound().build());

    private final ElasticSearchClient elasticAdapter;

    @PutMapping
    Mono<ResponseEntity<Map<String, Object>>> put(@Valid @RequestBody Personne person) {
        return elasticAdapter
                .index(person)
                .map(this::toMap)
                .map(m -> ResponseEntity.status(HttpStatus.CREATED).body(m));
    }

    @PutMapping(value = "/bulk")
    Mono<ResponseEntity<Map<String, Object>>> bulkPut(@Valid @RequestBody List<Personne> personnes) {

        log.info("yep");
        log.info(personnes.toString());

        return elasticAdapter
                .bulkIndex(personnes)
                .map(this::toMap)
                .map(m -> ResponseEntity.status(HttpStatus.CREATED).body(m));
    }

    @GetMapping("/{userName}")
    Mono<ResponseEntity<Personne>> get(@PathVariable("userName") String userName) {
        return elasticAdapter
                .findByUserName(userName)
                .map(ResponseEntity::ok)
                .switchIfEmpty(NOT_FOUND);
    }

    @GetMapping("/recherche")
    Mono<ResponseEntity<List<Personne>>> getFuzzy(
            @RequestParam("terme") String terme,
            @RequestParam("methode") String methode){

        log.info("Search with methode: {} and terme: {}",methode,terme);

        return elasticAdapter
                .recherche(methode,terme)
                .map(ResponseEntity::ok);
    }


    private ImmutableMap<String, Object> toMap(IndexResponse response) {
        return ImmutableMap
                .<String, Object>builder()
                .put("id", response.getId())
                .put("index", response.getIndex())
                .put("type", response.getType())
                .put("version", response.getVersion())
                .put("result", response.getResult().getLowercase())
                .put("seqNo", response.getSeqNo())
                .put("primaryTerm", response.getPrimaryTerm())
                .build();
    }

    private ImmutableMap<String, Object> toMap(BulkResponse response) {
        return ImmutableMap
                .<String, Object>builder()
                .put("items", response.getItems())
                .put("took", response.getTook())
                .build();
    }
}
