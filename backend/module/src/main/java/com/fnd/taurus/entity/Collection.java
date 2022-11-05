package com.fnd.taurus.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "collections")
@Where(clause = "deleted <> 1")
public class Collection extends CommonFields {
    @Column(name = "[order]")
    private Integer order;
    @ManyToOne(optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;
    @ManyToOne(optional = false)
    @JoinColumn(name = "piece_id", nullable = false)
    private Piece piece;
}
