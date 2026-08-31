package mg.manao.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CooperativeDTO {
    private String id;
    private String nom;
    private String adresse;
    private String telephone;
    private String presidentId;
    private boolean rappelActif;
    private int rappelDelaiMinutes;
}
