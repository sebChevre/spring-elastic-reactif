package ch.globaz.tmmas.rechercheservice.infrastructure.generator;

import ch.globaz.tmmas.rechercheservice.application.configuration.ElasticSearchIndexes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Component
@Slf4j
@RequiredArgsConstructor
public class Indexeur {

	@Autowired
	private final PersonneGenerateur personneGenerateur;
	@Autowired
	private final RestHighLevelClient client;

	private final Timer indexTimer = Metrics.timer("es.timer");
	private final LongAdder concurrent = Metrics.gauge("es.concurrent", new LongAdder());
	private final Counter successes = Metrics.counter("es.index", "result", "success");
	private final Counter failures = Metrics.counter("es.index", "result", "failure");



	public Flux<IndexResponse> index(int count, int concurrents) {

		log.info("Start random indexing bulk...");

		return personneGenerateur
				.infinite()
				.take(count)
				.flatMap(doc -> countConcurrent(measure(indexDocSwallowErrors(doc))), concurrents);
	}

	private <T> Mono<T> countConcurrent(Mono<T> input) {
		return input
				.doOnSubscribe(s -> concurrent.increment())
				.doOnTerminate(concurrent::decrement);
	}

	private <T> Mono<T> measure(Mono<T> input) {
		return Mono
				.fromCallable(System::currentTimeMillis)
				.flatMap(time ->
						input.doOnSuccess(x -> indexTimer.record(System.currentTimeMillis() - time, TimeUnit.MILLISECONDS))
				);
	}

	private Mono<IndexResponse> indexDocSwallowErrors(Doc doc) {
		return indexDoc(doc)
				.doOnSuccess(response -> successes.increment())
				.doOnError(e -> log.error("Unable to index {}", doc, e))
				.doOnError(e -> failures.increment())
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

	public void startIndexing(int nbElements) {
		Flux
				.range(0, nbElements)
				.map(x -> Math.max(1, x * 10))
				.doOnNext(x -> log.debug("Target concurrency: {}", x))
				.concatMap(concurrency -> index(5_000, concurrency))
				.window(Duration.ofSeconds(1))
				.flatMap(Flux::count)
				.subscribe(winSize -> log.debug("Got {} responses in last second", winSize));
	}
}
