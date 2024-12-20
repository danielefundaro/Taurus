package com.fundaro.zodiac.taurus.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConverterPdf2Image {
    public static List<String> parse(File sourceFile, String destinationPath) throws IOException {
        String formatName = "png";
        int dpi = 300;
        List<String> files = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(sourceFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.GRAY);
                String destinationFile = String.format("%s_%s.%s", destinationPath, page + 1, formatName);
                ImageIOUtil.writeImage(bim, destinationFile, dpi);
                files.add(destinationFile);

                // Remove external bounding box
//                BufferedImage img = ImageIO.read(new File(destinationFile));
//                Rectangle bounds = getBounds(bim, Color.WHITE);
//                BufferedImage trimmed = bim.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
//                ImageIOUtil.writeImage(trimmed, destinationFile, dpi);
            }
        }

        return files;
    }

    private static Rectangle getBounds(BufferedImage img, Color fillColor) {
        int width = img.getWidth(), height = img.getHeight();
        int top = height / 2, left = width / 2;
        int bottom = top, right = left;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (new Color(img.getRGB(x, y), true).equals(fillColor)) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);

                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }

        return new Rectangle(left, top, right - left, bottom - top);
    }
}
