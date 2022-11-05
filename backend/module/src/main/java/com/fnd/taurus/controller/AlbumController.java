package com.fnd.taurus.controller;

import com.fnd.taurus.dto.AlbumDTO;
import com.fnd.taurus.entity.Album;
import com.fnd.taurus.repository.AlbumRepository;
import com.fnd.taurus.service.AlbumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "albums")
@RestController
@Validated
public class AlbumController implements CommonController<Album, AlbumDTO, AlbumRepository, AlbumService> {
    private final AlbumService albumService;

    @Autowired
    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @Override
    public AlbumService getService() {
        return albumService;
    }
}
