package ch.globaz.tmmas.rechercheservice.domaine;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

import javax.validation.constraints.NotBlank;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class Adresse {

    @NotBlank
    private  String rue;

    @NotBlank
    private  String numero;

    private  String complementNumero;

    @NotBlank
    private  String npa;

    @NotBlank
    private  String localite;

}
