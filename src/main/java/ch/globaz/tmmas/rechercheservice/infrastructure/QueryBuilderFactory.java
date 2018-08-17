package ch.globaz.tmmas.rechercheservice.infrastructure;

import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QueryBuilderFactory {

    public static QueryBuilder wildCarsdQueryBuilder(String terme){

        QueryBuilder wildCarsdQueryBuilder = QueryBuilders.queryStringQuery(queryString(terme))
                .fields(fieldsToParse())
                .fuzziness(Fuzziness.TWO)
                .fuzzyPrefixLength(0)
                .fuzzyMaxExpansions(50)
                .fuzzyTranspositions(Boolean.TRUE);

        return wildCarsdQueryBuilder;
    }

    public static QueryBuilder fuzzyQueryBuilder(String terme){

        Object []  objectFields = fieldsToParseAsList().toArray();
        String [] fields = Arrays.copyOf(objectFields,objectFields.length,String[].class) ;

        QueryBuilder fuzzyTranspositionsCarsdQueryBuilder = QueryBuilders
                .multiMatchQuery(terme,fields)
                .fields(fieldsToParse())
                .fuzziness(Fuzziness.TWO)
                .prefixLength(0)
                .maxExpansions(50)
                .fuzzyTranspositions(Boolean.TRUE);

        return fuzzyTranspositionsCarsdQueryBuilder;
    }

    private static String queryString(String terme) {
        return "*"+terme+"*";
    }

    private static final Map<String,Float> fieldsToParse(){
        Map<String,Float> fields = new HashMap<>();
        fields.put("adresse.npa",1.0f);
        fields.put("adresse.localite",1.0f);
        fields.put("nom",1.0f);
        fields.put("prenom",1.0f);
        fields.put("employeur.ide",1.0f);
        fields.put("nss",1.0f);
        return fields;
    }

    private static final List<String> fieldsToParseAsList(){
        return fieldsToParse().keySet().stream()
            .collect(Collectors.toList());

    }
}
