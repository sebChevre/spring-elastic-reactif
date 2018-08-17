package ch.globaz.tmmas.rechercheservice.application

import ch.globaz.tmmas.rechercheservice.domaine.Personne
import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import static org.springframework.http.HttpMethod.PUT
import static org.springframework.http.HttpStatus.CREATED
import static org.springframework.http.HttpStatus.OK
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8

@ContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT)
class PersonnesControllerTest extends Specification{

    @Value('${local.server.port}')
    int port

    TestRestTemplate rest = new TestRestTemplate()
    def jsonSlurper = new JsonSlurper()

    def 'should index document'() {
        given:
            HttpEntity<String> request = indexRequest(PersonneDocumentsExample.DOCUMENT_1)
            print("request: " + request)
        when:
            ResponseEntity<String> response = rest.exchange(url(), PUT, request, String)
        then:
            print(response)
            response.statusCode == CREATED
    }

    def 'should bulkindex document'() {
        given:
            HttpEntity<String> request = indexRequest(PersonneDocumentsExample.documents())
            print("request: " + request)
        when:
            ResponseEntity<String> response = rest.exchange(bulkUrl(), PUT, request, String)
        then:
            def object = jsonSlurper.parseText(response.body)
            object.items.size == 3
            response.statusCode == CREATED
    }

    def 'should find indexed document'() {
        given:
            assert rest.exchange(url(), PUT, indexRequest(PersonneDocumentsExample.DOCUMENT_1), String).statusCode ==
                CREATED
        when:
        ResponseEntity<Map> response = rest.getForEntity(url() + "/" + PersonneDocumentsExample.USER_NAME1, Map)
        then:
        response.statusCode == OK
        response.body.adresse.rue == 'Eglise'
    }

    /**
    def 'should find indexed document with fuzzy'() {
        given:
            assert rest.exchange(bulkUrl(), PUT, indexRequest(PersonneDocumentsExample.documents()), String)
                .statusCode == CREATED
        when:
            sleep(1000)
            ResponseEntity<List> response = rest.getForEntity(url() + "/recherche?terme=756.12&methode=fuzzy", List)
        then:
            response.statusCode == OK
            print response.body
            response.body.size() == 1;
            response.body[0].adresse.rue == 'Eglise'
    }
*/

    private String url() {
        return "http://localhost:$port/personnes"
    }

    private String bulkUrl() {
        return "http://localhost:$port/personnes/bulk"
    }

    HttpEntity<String> indexRequest(String document) {
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(APPLICATION_JSON_UTF8)
        return new HttpEntity<String>(document, headers)
    }

    HttpEntity<String> indexRequest(List<Personne> documents) {
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(APPLICATION_JSON_UTF8)

       // print "DDDD" + documents

        HttpEntity<String> t = new HttpEntity<String>(documents, headers)

      //  print "XXXX" + t
        return new HttpEntity<String>(documents, headers)
    }
}
