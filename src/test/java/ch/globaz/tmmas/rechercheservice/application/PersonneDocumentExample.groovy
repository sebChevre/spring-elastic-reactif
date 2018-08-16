package ch.globaz.tmmas.rechercheservice.application

class PersonneDocumentExample {

    static final String USER_NAME = "sce";

    static final String DOCUMENT = """{
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
                            "username": "$USER_NAME",
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

}
