package com.fnd.taurus.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fnd.taurus.enums.PieceTypeEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PieceDTO extends CommonFieldsDTO {
    private String name;
    @Enumerated(EnumType.STRING)
    private PieceTypeEnum type;
    private String author;
    private String arranger;
    @JsonIgnore()
    private List<CollectionDTO> collections;
    private List<MediaDTO> media;
}
