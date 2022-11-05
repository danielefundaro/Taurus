package com.fnd.taurus.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "albums")
@Where(clause = "deleted <> 1")
public class Album extends CommonFields {
    @Column(name = "name", length = 255, nullable = false)
    private String name;
    @Column(name = "date")
    @Temporal(TemporalType.DATE)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date date;
    @OneToMany(mappedBy = "album")
    private List<Collection> collections;
}
