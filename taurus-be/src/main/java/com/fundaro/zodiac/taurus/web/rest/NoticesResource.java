package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
@RestController
@RequestMapping("/api/notices")
public class NoticesResource extends CommonResource<Notices, NoticesDTO, NoticesCriteria, NoticesService> {

    public NoticesResource(NoticesService noticesService) {
        super(noticesService, NoticesResource.class, Notices.class.getSimpleName());
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Long> getUnreadCount(AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().info("REST request to get count of unread notices");
        return ResponseEntity.ok().body(getService().countUnread(abstractAuthenticationToken));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> readAll(AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().info("REST request to mark all notices as read");
        getService().readAll(abstractAuthenticationToken);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NoticesDTO> read(@PathVariable Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().info("REST request to set notice {} as already read", id);
        NoticesDTO result = getService().read(id, abstractAuthenticationToken);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(getApplicationName(), false, getEntityName(), result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("delete-all")
    public ResponseEntity<Void> deleteAll(AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().info("REST request to delete all notices");
        getService().deleteAll(abstractAuthenticationToken);
        return ResponseEntity.noContent().build();
    }
}
