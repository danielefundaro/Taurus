package com.fnd.taurus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fnd.taurus.entity.Audit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class AuditTupleDTO extends CommonFieldsDTO {
    private Date date;
    private Long tupleId;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Audit audit;
}
