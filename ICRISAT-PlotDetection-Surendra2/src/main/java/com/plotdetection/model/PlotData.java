//package com.plotdetection.model;
//
//import org.opencv.core.Rect;
//
//public class PlotData {
//    private String id;
//    private int x1, y1, x2, y2;
//    private double area;
//    private String cropName;
//    private String cropType;
//    private String nitrogenLevel;
//    private double biomass;
//
//    // Constructor
//    public PlotData(String id, int x1, int y1, int x2, int y2, double area,
//                    String cropName, String cropType, String nitrogenLevel, double biomass) {
//        this.id = id;
//        this.x1 = x1;
//        this.y1 = y1;
//        this.x2 = x2;
//        this.y2 = y2;
//        this.area = area;
//        this.cropName = cropName;
//        this.cropType = cropType;
//        this.nitrogenLevel = nitrogenLevel;
//        this.biomass = biomass;
//    }
//
//    // Convenience constructor for detected plots (auto-compute area)
//    public PlotData(String id, int x1, int y1, int x2, int y2) {
//        this(id, x1, y1, x2, y2, Math.abs(x2 - x1) * Math.abs(y2 - y1), "", "", "", 0.0);
//    }
//
//    // Getters
//    public String getId() { return id; }
//    public void setId(String id) { this.id = id; }
//
//    public int getX1() { return x1; }
//    public int getY1() { return y1; }
//    public int getX2() { return x2; }
//    public int getY2() { return y2; }
//
//    // Computed values (top-left, width, height)
//    public int getX() { return Math.min(x1, x2); }
//    public int getY() { return Math.min(y1, y2); }
//    public int getWidth() { return Math.abs(x2 - x1); }
//    public int getHeight() { return Math.abs(y2 - y1); }
//    public double getArea() { return area; }
//
//    // For OpenCV Rect
//    public Rect getRect() {
//        return new Rect(getX(), getY(), getWidth(), getHeight());
//    }
//
//    // Setters for x1,y1,x2,y2 (optional)
//    public void setX1(int x1) { this.x1 = x1; }
//    public void setY1(int y1) { this.y1 = y1; }
//    public void setX2(int x2) { this.x2 = x2; }
//    public void setY2(int y2) { this.y2 = y2; }
//
//    // Setters for crop data
//    public String getCropName() { return cropName; }
//    public void setCropName(String cropName) { this.cropName = cropName; }
//    public String getCropType() { return cropType; }
//    public void setCropType(String cropType) { this.cropType = cropType; }
//    public String getNitrogenLevel() { return nitrogenLevel; }
//    public void setNitrogenLevel(String nitrogenLevel) { this.nitrogenLevel = nitrogenLevel; }
//    public double getBiomass() { return biomass; }
//    public void setBiomass(double biomass) { this.biomass = biomass; }
//
//    // Recalculate area (call after coordinate changes)
//    public void updateArea() {
//        this.area = Math.abs(x2 - x1) * Math.abs(y2 - y1);
//    }
//
//    @Override
//    public String toString() {
//        return String.format("Plot %s: (%d,%d)-(%d,%d), area=%.2f, crop=%s",
//                id, x1, y1, x2, y2, area, cropName);
//    }
//}

package com.plotdetection.model;

import org.opencv.core.Rect;

public class PlotData {
    private String id;
    private int x1, y1, x2, y2;
    private double area;
    private String cropName;
    private String cropType;
    private String nitrogenLevel;
    private double biomass;
    private String imagePath;   // <-- NEW: stores the saved image path

    // Constructor
    public PlotData(String id, int x1, int y1, int x2, int y2, double area,
                    String cropName, String cropType, String nitrogenLevel, double biomass) {
        this.id = id;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.area = area;
        this.cropName = cropName;
        this.cropType = cropType;
        this.nitrogenLevel = nitrogenLevel;
        this.biomass = biomass;
        this.imagePath = null;
    }

    // Convenience constructor for detected plots (auto-compute area)
    public PlotData(String id, int x1, int y1, int x2, int y2) {
        this(id, x1, y1, x2, y2, Math.abs(x2 - x1) * Math.abs(y2 - y1), "", "", "", 0.0);
    }

    // Copy method – creates a deep copy of the plot
    public PlotData copy() {
        PlotData copy = new PlotData(this.id, this.x1, this.y1, this.x2, this.y2, this.area,
                this.cropName, this.cropType, this.nitrogenLevel, this.biomass);
        copy.setImagePath(this.imagePath);
        return copy;
    }

    // Getters and Setters (including imagePath)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }

    public int getX() { return Math.min(x1, x2); }
    public int getY() { return Math.min(y1, y2); }
    public int getWidth() { return Math.abs(x2 - x1); }
    public int getHeight() { return Math.abs(y2 - y1); }
    public double getArea() { return area; }

    public Rect getRect() {
        return new Rect(getX(), getY(), getWidth(), getHeight());
    }

    public String getCropName() { return cropName; }
    public void setCropName(String cropName) { this.cropName = cropName; }
    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }
    public String getNitrogenLevel() { return nitrogenLevel; }
    public void setNitrogenLevel(String nitrogenLevel) { this.nitrogenLevel = nitrogenLevel; }
    public double getBiomass() { return biomass; }
    public void setBiomass(double biomass) { this.biomass = biomass; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public void setX1(int x1) { this.x1 = x1; }
    public void setY1(int y1) { this.y1 = y1; }
    public void setX2(int x2) { this.x2 = x2; }
    public void setY2(int y2) { this.y2 = y2; }

    public void updateArea() {
        this.area = Math.abs(x2 - x1) * Math.abs(y2 - y1);
    }

    @Override
    public String toString() {
        return String.format("Plot %s: (%d,%d)-(%d,%d), area=%.2f, crop=%s%s",
                id, x1, y1, x2, y2, area, cropName,
                imagePath != null ? " [saved]" : "");
    }
}