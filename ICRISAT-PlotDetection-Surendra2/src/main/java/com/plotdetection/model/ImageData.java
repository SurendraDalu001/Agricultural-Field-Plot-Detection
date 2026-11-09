package com.plotdetection.model;

import java.io.File;

public class ImageData {
    private File file;

    public ImageData(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    @Override
    public String toString() {
        return file.getName();
    }
}