package com.company.platform.tool.qrcode.api;

import com.company.platform.tool.qrcode.model.QrCodeRequest;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

public interface QrCodeService { BufferedImage generate(QrCodeRequest request); void writePng(QrCodeRequest request, OutputStream output) throws IOException; }
