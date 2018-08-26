package ch.globaz.tmmas.rechercheservice.application.api.messaging.listener;


import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PersonneMoraleCreeEvent  {

    public PersonneMoraleCreeEvent(){}
    private String nss;
    private String nom;
    private String prenom;
    private String dateNaissance;
    private Long id;

    PersonneMoraleCreeEvent(String nss, String nom, String prenom, String dateNaissance, Long id) {
        this.nss = nss;
        this.nom = nom;
        this.prenom = prenom;
        this.dateNaissance = dateNaissance;

        this.id = id;
    }


}
