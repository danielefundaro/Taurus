package com.fnd.taurus.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "instruments")
@Where(clause = "deleted <> 1")
public class Instrument extends CommonFields {
    @Column(name = "name", length = 255, nullable = false)
    private String name;
    @OneToMany(mappedBy = "instrument")
    private List<Media> media;
}
