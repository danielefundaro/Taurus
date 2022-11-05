package com.fnd.taurus.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class InstrumentDTO extends CommonFieldsDTO {
    private String name;
    @JsonIgnore()
    private List<MediaDTO> media;
}
