package ch.globaz.tmmas.rechercheservice.application.api.web;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * Filtre consistant à fournir un metrique comptant les appels effectués
 * Sur chaque API.
 * </p>
 * <p>Chaque fois que l'api <b>prometheus</b> est appelé
 * le compteur est remis à zéro. La finalité est de fournir un comtage des appels par untié de temps.
 * </p>
 */
@Slf4j
@Component
public class PrometheusRequestCountMetricsFilter implements Filter {

    private Map<String,AtomicInteger> countersByApi = new HashMap<>();

    private final static String PROMETHEUS_API = "/actuator/prometheus";

    @Autowired
    MeterRegistry registry;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("Initializing filter :{}", this);


    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;


        log.info(
                "Logging Request  {} : {}", req.getMethod(),
                req.getRequestURI());



        String key = req.getRequestURI();

        //si pas présente on initialise
        countersByApi.putIfAbsent(key, initialiseGaugeFor(key));


        countersByApi.computeIfPresent(req.getRequestURI(),(path,counter)->{
            log.info("compute...");
            log.info("new value for key [{}]: {}",key,counter.incrementAndGet());
            return counter;
        });

        chain.doFilter(request, response);

        log.info(
                "Logging Response :{}",
                res.getContentType());

        if(req.getRequestURI().equals(PROMETHEUS_API)){
            countersByApi.values().forEach(counter -> {
                counter.set(0);
            });
        }
    }

    private AtomicInteger initialiseGaugeFor(String key){

        AtomicInteger requestCounter = new AtomicInteger(0);

        Gauge gauge = Gauge.builder("search.service.request", requestCounter, (obj)->{
            return obj.doubleValue();
        })
        .tags("api", key)
        .register(registry);

        return requestCounter;

    }

    @Override
    public void destroy() {
        log.warn("Destructing filter :{}", this);
    }


}
