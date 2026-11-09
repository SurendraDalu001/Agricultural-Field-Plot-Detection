package com.plotdetection.model;

public class CropInfo {

    private String cropName;
    private String cropType;
    private String nitrogen;
    private String cropCode;

    public CropInfo(String cropName,
                    String cropType,
                    String nitrogen,
                    String cropCode) {

        this.cropName = cropName;
        this.cropType = cropType;
        this.nitrogen = nitrogen;
        this.cropCode = cropCode;
    }

    public String getCropName() {
        return cropName;
    }

    public String getCropType() {
        return cropType;
    }

    public String getNitrogen() {
        return nitrogen;
    }

    public String getCropCode() {
        return cropCode;
    }
}
