package com.fnd.taurus.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CollectionDTO extends CommonFieldsDTO {
    private Integer order;
    @JsonIgnore()
    private AlbumDTO album;
    private PieceDTO piece;
}
