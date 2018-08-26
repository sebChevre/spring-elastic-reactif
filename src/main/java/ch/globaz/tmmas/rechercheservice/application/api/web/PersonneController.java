package ch.globaz.tmmas.rechercheservice.application.api.web;

import ch.globaz.tmmas.rechercheservice.domaine.Personne;
import ch.globaz.tmmas.rechercheservice.domaine.PersonneIndex;
import ch.globaz.tmmas.rechercheservice.infrastructure.ElasticSearchClient;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import javax.validation.Valid;
import java.io.IOException;


@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/personnes")
public class PersonneController {



    private final ElasticSearchClient elasticAdapter;



    @PostMapping
    ResponseEntity<IndexResponse> put(@Valid @RequestBody PersonneIndex person) throws IOException {
        return new ResponseEntity<IndexResponse>(elasticAdapter
                .index(person),HttpStatus.CREATED);
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
