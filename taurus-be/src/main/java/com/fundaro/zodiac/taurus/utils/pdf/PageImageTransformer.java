package com.fundaro.zodiac.taurus.utils.pdf;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Applica una {@link PageEditRecipe} a un'immagine già renderizzata.
 *
 * <p>La conversione dei PDF vive in {@code utils} ma la pipeline di trasformazione appartiene al
 * livello di servizio. Questa interfaccia inverte la dipendenza: {@code utils} dichiara ciò di cui
 * ha bisogno e il servizio la implementa, senza che il pacchetto di utilità conosca il livello
 * applicativo.
 */
@FunctionalInterface
public interface PageImageTransformer {
    /**
     * @return una immagine per ogni ritaglio della ricetta, oppure una sola immagine quando la
     *     ricetta non contiene ritagli.
     */
    List<BufferedImage> transformRendered(BufferedImage source, PageEditRecipe recipe);
}
