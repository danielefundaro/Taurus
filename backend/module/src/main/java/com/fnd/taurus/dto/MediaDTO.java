package com.fnd.taurus.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fnd.taurus.enums.MediaTypeEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Getter
@Setter
@NoArgsConstructor
public class MediaDTO extends CommonFieldsDTO {
    private String name;
    @Enumerated(EnumType.STRING)
    private MediaTypeEnum type;
    private String contentType;
    private Integer page;
    @JsonIgnore()
    private String path;
    private Integer order;
    @JsonIgnore()
    private PieceDTO piece;
    private InstrumentDTO instrument;
}
