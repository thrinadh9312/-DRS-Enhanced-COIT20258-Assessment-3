package com.drs.client;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
public final class App extends Application {
    @Override public void start(Stage stage)throws Exception{
        FXMLLoader loader=new FXMLLoader(App.class.getResource("/fxml/main.fxml"));Scene scene=new Scene(loader.load(),1250,820);scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm());
        stage.setTitle("DRS-Enhanced | Thirthala Thrinadh | 12313659");stage.setMinWidth(1000);stage.setMinHeight(700);stage.setScene(scene);stage.show();
        stage.setOnCloseRequest(e->((MainController)loader.getController()).close());
    }
    public static void main(String[] args){launch(args);}
}
