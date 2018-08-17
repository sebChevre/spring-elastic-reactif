package ch.globaz.tmmas.rechercheservice.domaine;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.net.URL;

@NoArgsConstructor
@Getter
@Setter
public class Employeur {

    @NotBlank
    private  String nom;

    @NotBlank
    private  String email;

    @NotNull
    private  String url;

    @NotBlank
    private  String ide;


}
