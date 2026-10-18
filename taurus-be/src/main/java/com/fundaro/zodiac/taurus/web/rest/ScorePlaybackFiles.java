package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.scoreplayback.MusicXmlDocuments;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Response of the playback file endpoints: plain MusicXML, never cached by intermediaries. */
public final class ScorePlaybackFiles {

    private ScorePlaybackFiles() {}

    public static ResponseEntity<byte[]> musicXml(byte[] xml) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MusicXmlDocuments.PLAIN_MEDIA_TYPE));
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(xml);
    }
}
