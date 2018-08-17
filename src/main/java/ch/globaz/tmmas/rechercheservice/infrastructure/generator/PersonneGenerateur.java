package ch.globaz.tmmas.rechercheservice.infrastructure.generator;

import ch.globaz.tmmas.rechercheservice.domaine.Employeur;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.codearte.jfairy.Fairy;
import io.codearte.jfairy.producer.company.Company;
import io.codearte.jfairy.producer.person.Address;
import io.codearte.jfairy.producer.person.Person;
import lombok.Value;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;


import java.util.Arrays;

@Component
public class PersonneGenerateur {

	private final ObjectMapper objectMapper;
	private final ThreadLocal<Fairy> fairy;
	private final Scheduler scheduler = Schedulers.newParallel(PersonneGenerateur.class.getSimpleName());

	PersonneGenerateur(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		fairy = ThreadLocal.withInitial(Fairy::create);
	}

	Flux<Doc> infinite() {
		return generateOne().repeat();
	}

	private Mono<Doc> generateOne() {
		return Mono
				.fromCallable(this::generate)
				.subscribeOn(scheduler);
	}

	private String generateFakeNss(String randomNumer){

		return "756." + fairy.get().baseProducer().randomBetween(1000,9999) + "."
				+ fairy.get().baseProducer().randomBetween(1000,9999) + "."
				+ fairy.get().baseProducer().randomBetween(10,99);
	}

	private Employeur generateFakeEmployuer(Company cie){
		Employeur e = new Employeur();
		e.setEmail(cie.getEmail());
		e.setNom(cie.getName());
		e.setUrl(cie.getUrl());
		e.setIde("CHE-" + fairy.get().baseProducer().randomBetween(100,999) +  "."
				+ fairy.get().baseProducer().randomBetween(100,999) + "."
		+ fairy.get().baseProducer().randomBetween(100,999));


		return e;
	}

	private Doc generate() {
		Person person = fairy.get().person();
		final String username = person.getUsername() + RandomUtils.nextInt(1_000_000, 9_000_000);
		final ImmutableMap<String, Object> map = ImmutableMap.<String, Object>builder()
				.put("adresse", toMap(person.getAddress()))
				.put("prenom", person.getFirstName())
				.put("nom", person.getLastName())
				.put("email", person.getEmail())
				.put("emailProfessionel", person.getCompanyEmail())
				.put("username", username)
				.put("password", person.getPassword())
				.put("sexe", person.getSex())
				.put("nss", generateFakeNss(person.getNationalIdentityCardNumber()))
				.put("noTelephone", person.getTelephoneNumber())
				.put("dateNaissance", person.getDateOfBirth())
				.put("employeur", generateFakeEmployuer(person.getCompany()))
				.build();

		try {
			final String json = objectMapper.writeValueAsString(map);
			return new Doc(username, json);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private ImmutableMap<String, Object> toMap(Address address) {
		return ImmutableMap.<String, Object>builder()
				.put("rue", address.getStreet())
				.put("numero", address.getStreetNumber())
				.put("complementNumero", address.getApartmentNumber())
				.put("npa", address.getPostalCode())
				.put("localite", address.getCity())
				.build();
	}
}
@Value
class Doc {
	private final String username;
	private final String json;
}
