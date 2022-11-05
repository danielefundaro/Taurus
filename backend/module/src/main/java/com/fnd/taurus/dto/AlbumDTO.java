package com.fnd.taurus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AlbumDTO extends CommonFieldsDTO {
    private String name;
    private Date date;
    private List<CollectionDTO> collections;
}
