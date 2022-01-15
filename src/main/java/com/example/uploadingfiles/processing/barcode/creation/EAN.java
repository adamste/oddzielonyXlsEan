package com.example.uploadingfiles.processing.barcode.creation;

import org.krysalis.barcode4j.impl.upcean.EAN13Bean;
import org.krysalis.barcode4j.impl.upcean.EAN8Bean;
import org.krysalis.barcode4j.impl.upcean.UPCEANBean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class EAN {
    public static byte[] generate(String barcodeText, int width, int height) throws IOException {

        UPCEANBean barcodeGenerator;
        if(barcodeText.length() == 13) {
            barcodeGenerator = new EAN13Bean();
        } else {
            barcodeGenerator = new EAN8Bean();
        }

        BitmapCanvasProvider canvas =
                new BitmapCanvasProvider(320, BufferedImage.TYPE_BYTE_BINARY, false, 0);

        barcodeGenerator.generateBarcode(canvas, barcodeText);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas.getBufferedImage(), "png", baos);
        return baos.toByteArray();
    }
}
