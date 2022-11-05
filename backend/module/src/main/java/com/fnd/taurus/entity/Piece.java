package com.fnd.taurus.entity;

import com.fnd.taurus.enums.PieceTypeEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pieces")
@Where(clause = "deleted <> 1")
public class Piece extends CommonFields {
    @Column(name = "name", length = 255, nullable = false)
    private String name;
    @Column(name = "type", length = 50)
    @Enumerated(EnumType.STRING)
    private PieceTypeEnum type;
    @Column(name = "author", length = 255)
    private String author;
    @Column(name = "arranger", length = 255)
    private String arranger;
    @OneToMany(mappedBy = "piece")
    private List<Collection> collections;
    @OneToMany(mappedBy = "piece")
    private List<Media> media;
}
