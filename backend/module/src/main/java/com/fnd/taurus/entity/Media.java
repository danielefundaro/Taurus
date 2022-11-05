package com.fnd.taurus.entity;

import com.fnd.taurus.enums.MediaTypeEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "media")
@Where(clause = "deleted <> 1")
public class Media extends CommonFields {
    @Column(name = "name", length = 255, nullable = false)
    private String name;
    @Column(name = "type", length = 50)
    @Enumerated(EnumType.STRING)
    private MediaTypeEnum type;
    @Column(name = "content_type", length = 255)
    private String contentType;
    @Column(name = "page")
    private Integer page;
    @Column(name = "path")
    private String path;
    @Column(name = "[order]")
    private Integer order;
    @ManyToOne(optional = false)
    @JoinColumn(name = "piece_id", nullable = false)
    private Piece piece;
    @ManyToOne()
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;
}
