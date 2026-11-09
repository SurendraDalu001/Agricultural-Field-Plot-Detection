//package com.plotdetection.app;
//
//import javafx.application.Application;
//import javafx.fxml.FXMLLoader;
//import javafx.scene.Scene;
//import javafx.stage.Stage;
//
//public class MainApp extends Application {
//
//    @Override
//    public void start(Stage stage) throws Exception {
//
//        FXMLLoader loader = new FXMLLoader(
//                getClass().getResource("/view/MainView.fxml"));
//
//        Scene scene = new Scene(loader.load(), 1400, 800);
//
//        stage.setTitle("Image Plot Detection");
//        stage.setScene(scene);
//        stage.setMaximized(true);
//        stage.show();
//    }
//
//    public static void main(String[] args) {
//        launch(args);
//    }
//}

package com.plotdetection.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import nu.pattern.OpenCV;

public class MainApp extends Application {

    // Load OpenCV native library
	static {
	    OpenCV.loadLocally();
	}

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/MainView.fxml")
        );
        Scene scene = new Scene(loader.load(), 1400, 800);
        scene.getStylesheets().add(getClass().getResource("/css/Style.css").toExternalForm());
        stage.setTitle("Image Plot Detection");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }
//
    public static void main(String[] args) {
        launch(args);
    }
}