package ch.globaz.tmmas.rechercheservice.infrastructure.generator;

import ch.globaz.tmmas.rechercheservice.application.configuration.ElasticSearchIndexes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class Indexeur {

	@Autowired
	private final PersonneGenerateur personneGenerateur;
	@Autowired
	private final RestHighLevelClient client;

	public void index(int count) {

		log.info("Start random indexing bulk...");

		personneGenerateur
				.infinite()
				.take(count)
				.flatMap(this::indexDocSwallowErrors, 1)
				.window(Duration.ofSeconds(1))
				.flatMap(Flux::count)
				.subscribe(winSize -> log.debug("Got {} responses in last second", winSize));
	}

	private Mono<IndexResponse> indexDocSwallowErrors(Doc doc) {
		return indexDoc(doc)
				.doOnError(e -> log.error("Unable to index {}", doc, e))
				.onErrorResume(e -> Mono.empty());
	}

	private Mono<IndexResponse> indexDoc(Doc doc) {
		//log.info("Indexing doc: {}", doc);

		return Mono.create(sink -> {
			final IndexRequest indexRequest = new IndexRequest(ElasticSearchIndexes.PERSONNES.index(),
					ElasticSearchIndexes.PERSONNES.type(), doc.getUsername
					());
			indexRequest.source(doc.getJson(), XContentType.JSON);
			client.indexAsync(indexRequest, listenerToSink(sink));
		});
	}

	private ActionListener<IndexResponse> listenerToSink(MonoSink<IndexResponse> sink) {
		return new ActionListener<IndexResponse>() {
			@Override
			public void onResponse(IndexResponse indexResponse) {
				sink.success(indexResponse);
			}

			@Override
			public void onFailure(Exception e) {
				sink.error(e);
			}
		};
	}
}
