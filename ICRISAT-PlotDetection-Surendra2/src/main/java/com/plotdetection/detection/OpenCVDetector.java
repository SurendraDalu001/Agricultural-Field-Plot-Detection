package com.plotdetection.detection;

import com.plotdetection.model.PlotData;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class OpenCVDetector {

    public List<PlotData> detect(Mat image) {
        List<PlotData> plots = new ArrayList<>();

        // 1. Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        // 2. Adaptive threshold
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(gray, thresh, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 11, 2);

        // 3. Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        int id = 1;
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            double area = rect.width * rect.height;

            // Filter out noise
            if (area > 500 && rect.width > 20 && rect.height > 20) {
                double aspectRatio = (double) rect.width / rect.height;
                if (aspectRatio > 0.2 && aspectRatio < 5.0) {
                    // Use new constructor with x1,y1,x2,y2
                    int x1 = rect.x;
                    int y1 = rect.y;
                    int x2 = rect.x + rect.width;
                    int y2 = rect.y + rect.height;
                    String plotId = "DET_" + id++;
                    PlotData plot = new PlotData(plotId, x1, y1, x2, y2, area,
                            "", "", "", 0.0);
                    plots.add(plot);
                }
            }
        }

        // Fallback: Otsu threshold
        if (plots.isEmpty()) {
            plots = detectWithOtsu(gray);
        }

        return plots;
    }

    private List<PlotData> detectWithOtsu(Mat gray) {
        List<PlotData> plots = new ArrayList<>();
        Mat thresh = new Mat();
        Imgproc.threshold(gray, thresh, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        int id = 1;
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            double area = rect.width * rect.height;
            if (area > 500 && rect.width > 20 && rect.height > 20) {
                int x1 = rect.x;
                int y1 = rect.y;
                int x2 = rect.x + rect.width;
                int y2 = rect.y + rect.height;
                String plotId = "DET_" + id++;
                PlotData plot = new PlotData(plotId, x1, y1, x2, y2, area,
                        "", "", "", 0.0);
                plots.add(plot);
            }
        }
        return plots;
    }
}