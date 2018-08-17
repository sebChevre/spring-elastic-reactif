package ch.globaz.tmmas.rechercheservice.infrastructure.generator;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@RequiredArgsConstructor
public class ESMetrics {


	private final Timer indexTimer;
	private final Counter indexConcurrent;
	private final Counter successes;
	private final Counter failures;

	void success() {
		successes.inc();
	}

	void failure() {
		failures.inc();
	}

	void concurrentStart() {
		indexConcurrent.inc();
	}

	void concurrentStop() {
		indexConcurrent.dec();
	}

	Timer.Context startTimer() {
		return indexTimer.time();
	}

}
