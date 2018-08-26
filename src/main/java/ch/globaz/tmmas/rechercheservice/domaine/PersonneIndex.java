package ch.globaz.tmmas.rechercheservice.domaine;


import ch.globaz.tmmas.rechercheservice.application.api.messaging.listener.PersonneMoraleCreeEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@NoArgsConstructor
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(of = {"username"})
public class PersonneIndex {


    private Adresse adresse;


    private  String prenom;


    private  String nom;



    private String dateNaissance;


    private  String nss;

    private PersonneIndex(@NotBlank String prenom, @NotBlank String nom, @NotNull
            String dateNaissance, @NotBlank String nss) {

        this.prenom = prenom;
        this.nom = nom;
        this.dateNaissance = dateNaissance;
        this.nss = nss;
    }

    public static PersonneIndex fromEvent(PersonneMoraleCreeEvent event){
        return new PersonneIndex(event.getNom(),event.getPrenom(), event.getDateNaissance(),event.getNss());
    }



}


