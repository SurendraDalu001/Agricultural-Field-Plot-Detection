package com.plotdetection.service;

import java.sql.*;

import com.plotdetection.model.PlotData;

public class DatabaseService {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/plot_detection?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Surendra";

    public DatabaseService() {
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        String sql = """
                CREATE TABLE IF NOT EXISTS plots (
                    plot_id VARCHAR(50) PRIMARY KEY,
                    x1 INT,
                    y1 INT,
                    x2 INT,
                    y2 INT,
                    area DOUBLE,
                    crop_name VARCHAR(255),
                    crop_type VARCHAR(255),
                    nitrogen_level VARCHAR(50),
                    biomass DOUBLE,
                    image_path VARCHAR(500),
                    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertPlot(PlotData plot, String imagePath) {
        String sql = """
                INSERT INTO plots (
                    plot_id, x1, y1, x2, y2, area,
                    crop_name, crop_type, nitrogen_level, biomass, image_path
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, plot.getId());
            pstmt.setInt(2, plot.getX1());
            pstmt.setInt(3, plot.getY1());
            pstmt.setInt(4, plot.getX2());
            pstmt.setInt(5, plot.getY2());
            pstmt.setDouble(6, plot.getArea());
            pstmt.setString(7, plot.getCropName());
            pstmt.setString(8, plot.getCropType());
            pstmt.setString(9, plot.getNitrogenLevel());
            pstmt.setDouble(10, plot.getBiomass());
            pstmt.setString(11, imagePath);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updatePlot(PlotData plot, String imagePath) {
        String sql = """
                UPDATE plots SET
                    x1 = ?, y1 = ?, x2 = ?, y2 = ?, area = ?,
                    crop_name = ?, crop_type = ?, nitrogen_level = ?, biomass = ?,
                    image_path = ?
                WHERE plot_id = ?;
                """;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, plot.getX1());
            pstmt.setInt(2, plot.getY1());
            pstmt.setInt(3, plot.getX2());
            pstmt.setInt(4, plot.getY2());
            pstmt.setDouble(5, plot.getArea());
            pstmt.setString(6, plot.getCropName());
            pstmt.setString(7, plot.getCropType());
            pstmt.setString(8, plot.getNitrogenLevel());
            pstmt.setDouble(9, plot.getBiomass());
            pstmt.setString(10, imagePath);
            pstmt.setString(11, plot.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean plotExists(String plotId) {
        String sql = "SELECT 1 FROM plots WHERE plot_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, plotId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void savePlot(PlotData plot, String imagePath) {
        if (plotExists(plot.getId())) {
            updatePlot(plot, imagePath);
        } else {
            insertPlot(plot, imagePath);
        }
    }

    public void deletePlot(String plotId) {
        String sql = "DELETE FROM plots WHERE plot_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, plotId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}