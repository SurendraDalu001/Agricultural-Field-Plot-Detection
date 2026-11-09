package com.plotdetection.service;

import com.plotdetection.detection.OpenCVDetector;
import com.plotdetection.model.PlotData;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;

public class DetectionService {

    private OpenCVDetector detector;

    public DetectionService(OpenCVDetector detector) {
        this.detector = detector;
    }

    public List<PlotData> detectPlots(Mat image) {
        return detector.detect(image);
    }

    public Mat annotatePlots(Mat image, List<PlotData> plots) {
        Mat annotated = image.clone();
        for (PlotData plot : plots) {
            Rect rect = plot.getRect();
            // Draw green rectangle
            Imgproc.rectangle(annotated, rect.tl(), rect.br(),
                    new Scalar(0, 255, 0), 2);
            // Put label above the rectangle
            Imgproc.putText(annotated, "Plot " + plot.getId(),
                    rect.tl(), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6,
                    new Scalar(0, 255, 0), 2);
        }
        return annotated;
    }
}