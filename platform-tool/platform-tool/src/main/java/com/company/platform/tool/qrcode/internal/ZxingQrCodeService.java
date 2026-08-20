package com.company.platform.tool.qrcode.internal;

import com.company.platform.tool.api.PlatformToolException;
import com.company.platform.tool.qrcode.api.QrCodeService;
import com.company.platform.tool.qrcode.model.QrCodeRequest;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public final class ZxingQrCodeService implements QrCodeService {
    @Override public BufferedImage generate(QrCodeRequest request) {
        try { return MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(request.content(), BarcodeFormat.QR_CODE, request.width(), request.height(), Map.of(EncodeHintType.MARGIN, request.margin(), EncodeHintType.CHARACTER_SET, "UTF-8"))); }
        catch (WriterException exception) { throw new PlatformToolException("QR_GENERATION_FAILED", "Unable to generate QR code", exception); }
    }
    @Override public void writePng(QrCodeRequest request, OutputStream output) throws IOException { javax.imageio.ImageIO.write(generate(request), "PNG", output); }
}
