package ch.globaz.tmmas.rechercheservice.domaine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;


@NoArgsConstructor
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(of = {"username"})
public class Personne {

    @NotNull
    @Valid
    private  Adresse adresse;

    @NotBlank
    private  String prenom;

    @NotBlank
    private  String nom;

    @NotBlank
    private  String email;

    private  String emailProfessionel;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private  String sexe;

    @NotBlank
    private  String noTelephone;

    @NotNull
    private  LocalDate dateNaissance;

    @Valid
    private Employeur employeur;

    @NotBlank
    private  String nss;



}


