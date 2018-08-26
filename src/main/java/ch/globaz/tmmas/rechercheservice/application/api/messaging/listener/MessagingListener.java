package ch.globaz.tmmas.rechercheservice.application.api.messaging.listener;


import ch.globaz.tmmas.rechercheservice.domaine.PersonneIndex;
import ch.globaz.tmmas.rechercheservice.infrastructure.ElasticSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MessagingListener {


    @Autowired
    ElasticSearchClient elasticSearchClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingListener.class);


    @KafkaListener(topics = "personne-index-cree",containerFactory = "jsonKafkaListenerContainerFactory")
    public void listen(PersonneMoraleCreeEvent message, Acknowledgment acknowledgment) throws IOException {

        LOGGER.info("Message consumed {}",message);

        LOGGER.info("(PersonneMoraleCreeEvent id:{}",message.getId());
        //l'id tiers existe

        elasticSearchClient.index(PersonneIndex.fromEvent(message));
        acknowledgment.acknowledge();
        LOGGER.info("Consummer acknoledge");
    }

}
