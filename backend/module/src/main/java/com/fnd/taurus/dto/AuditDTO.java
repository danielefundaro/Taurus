package com.fnd.taurus.dto;

import com.fnd.taurus.enums.AuditTypeEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AuditDTO extends CommonFieldsDTO {
    private Date date;
    @Enumerated(EnumType.STRING)
    private AuditTypeEnum type;
    private String userId;
    private String tableName;
    private List<AuditTupleDTO> tuples;
}
