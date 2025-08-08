package ai.asktheexpert.virtualassistant.utilities;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;

public class ImageUtils {
    private static final int MAX_WIDTH = 1920;
    private static final int MAX_HEIGHT = 1080;

    /**
     * Takes input image bytes (any supported format), scales it down if necessary to fit within
     * MAX_WIDTH x MAX_HEIGHT (maintaining aspect ratio), and returns the resulting image bytes
     * in the same format. If the original image is already within bounds, it is returned unmodified.
     *
     * @param inputBytes source image bytes
     * @return resized (or original) image bytes in original format
     * @throws IOException if reading or writing fails
     */
    public static byte[] resizeIfNeeded(byte[] inputBytes) throws IOException {
        // Detect format
        ByteArrayInputStream bais = new ByteArrayInputStream(inputBytes);
        try (ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format or no reader found");
            }
            ImageReader reader = readers.next();
            String formatName = reader.getFormatName();
            reader.setInput(iis, true);
            BufferedImage original = reader.read(0);
            reader.dispose();

            int width = original.getWidth();
            int height = original.getHeight();

            double widthScale = (double) MAX_WIDTH / width;
            double heightScale = (double) MAX_HEIGHT / height;
            double scale = Math.min(1.0, Math.min(widthScale, heightScale));

            BufferedImage outputImage;
            if (scale < 1.0) {
                int newWidth = (int) Math.round(width * scale);
                int newHeight = (int) Math.round(height * scale);

                // Choose type to preserve alpha if present
                int imageType = original.getColorModel().hasAlpha()
                        ? BufferedImage.TYPE_INT_ARGB
                        : BufferedImage.TYPE_INT_RGB;

                outputImage = new BufferedImage(newWidth, newHeight, imageType);
                Graphics2D g = outputImage.createGraphics();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    Image tmp = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                    g.drawImage(tmp, 0, 0, null);
                } finally {
                    g.dispose();
                }
            } else {
                outputImage = original;
            }

            // Write output in original format
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
                if (!writers.hasNext()) {
                    // fallback to PNG if original format not supported for writing
                    formatName = "png";
                    writers = ImageIO.getImageWritersByFormatName(formatName);
                    if (!writers.hasNext()) {
                        throw new IOException("No writer found for fallback format PNG");
                    }
                }
                ImageWriter writer = writers.next();
                writer.setOutput(ios);
                writer.write(null, new IIOImage(outputImage, null, null), writer.getDefaultWriteParam());
                writer.dispose();
            }

            return baos.toByteArray();
        }
    }
}
