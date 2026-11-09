package com.plotdetection.service;

import com.plotdetection.model.ImageData;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageService {

    // Import a single image file
    public ImageData importImage(File file) {
        return new ImageData(file);
    }

    // Import all image files from a folder
    public List<ImageData> importFolder(File folder) {
        List<ImageData> images = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".bmp") ||
                    name.endsWith(".tif") || name.endsWith(".tiff")) {
                    images.add(new ImageData(f));
                }
            }
        }
        return images;
    }

    // Load an ImageData as JavaFX Image
    public Image loadImage(ImageData data) {
        try {
            BufferedImage bi = ImageIO.read(data.getFile());
            return SwingFXUtils.toFXImage(bi, null);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Convert BufferedImage to OpenCV Mat (BGR format)
    public Mat bufferedImageToMat(BufferedImage bi) {
        if (bi == null) return null;
        int w = bi.getWidth();
        int h = bi.getHeight();
        byte[] pixels = new byte[w * h * 3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = bi.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int idx = (y * w + x) * 3;
                pixels[idx] = (byte) b;
                pixels[idx+1] = (byte) g;
                pixels[idx+2] = (byte) r;
            }
        }
        Mat mat = new Mat(h, w, org.opencv.core.CvType.CV_8UC3);
        mat.put(0, 0, pixels);
        return mat;
    }

    // Convert OpenCV Mat to JavaFX Image
    public Image matToImage(Mat mat) {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".png", mat, mob);
        byte[] bytes = mob.toArray();
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        return new Image(bis);
    }
}