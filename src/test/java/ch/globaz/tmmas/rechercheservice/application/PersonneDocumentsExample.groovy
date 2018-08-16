package ch.globaz.tmmas.rechercheservice.application

class PersonneDocumentsExample {

    static final String USER_NAME1 = "sce";
    static final String USER_NAME2 = "sde";
    static final String USER_NAME3 = "sze";

    static String DOCUMENT_1 = """{
                            "adresse": {
                                "rue": "Eglise",
                                "numero": "71",
                                "complementNumero": "",
                                "npa": "2854",
                                "localite": "Bassecourt"
                            },
                            "prenom": "Sébastien",
                            "nom": "Chèvre",
                            "email": "seb.chevre@horizon.com",
                            "emailProfessionel": "toto@globaz.ch",
                            "nss":"756.1234.5678.90",
                            "username": "$USER_NAME1",
                            "password": "fM8e57Jl",
                            "sexe": "HOMME",
                            "noTelephone": "0041798765653",
                            "dateNaissance": "1978-09-11",
                            "employeur": {
                                "nom": "Globaz S.A.",
                                "email": "info@globaz.ch",
                                "ide": "CHE-102.360.639",
                                "url": "http://www.globaz.ch"
                            }
                        }
                    """

    static String DOCUMENT_2 = """{
                            "adresse": {
                                "rue": "Moulin",
                                "numero": "12",
                                "complementNumero": "appertement n° 3",
                                "npa": "2300",
                                "localite": "La Chaux-de-Fonds"
                            },
                            "prenom": "Mickey",
                            "nom": "Mouse",
                            "email": "mm@horizon.com",
                            "emailProfessionel": "toto@manor.ch",
                            "nss":"756.5678.9011.33",
                            "username": "$USER_NAME2",
                            "password": "f4ffsfdf",
                            "sexe": "HOMME",
                            "noTelephone": "0041788745654",
                            "dateNaissance": "1978-02-11",
                            "employeur": {
                                "nom": "Manor S.A.",
                                "email": "manor@globaz.ch",
                                "ide": "CHE-112.360.339",
                                "url": "http://www.manor.ch"
                            }
                        }
                    """

    static String DOCUMENT_3 = """{
                            "adresse": {
                                "rue": "Gare",
                                "numero": "11",
                                "complementNumero": "",
                                "npa": "7000",
                                "localite": "Zurich"
                            },
                            "prenom": "Fred",
                            "nom": "Coulon",
                            "email": "fred.coulon@horizon.com",
                            "emailProfessionel": "toto@globaz.ch",
                            "nss":"756.1424.5678.90",
                            "username": "$USER_NAME3",
                            "password": "fM227Jl",
                            "sexe": "HOMME",
                            "noTelephone": "0041768765000",
                            "dateNaissance": "1968-09-12",
                            "employeur": {
                                "nom": "MC Donald Suisse S.A.",
                                "email": "info@mcdonald.ch",
                                "ide": "CHE-333.360.639",
                                "url": "http://www.mcdonald.ch"
                            }
                        }
                    """

    static String documents(){
        List<String> docs = new ArrayList<>();
        docs.add(DOCUMENT_1);
        docs.add(DOCUMENT_2);
        docs.add(DOCUMENT_3);

        String joins = docs.join(",")

        "[$joins]"
    }



}
