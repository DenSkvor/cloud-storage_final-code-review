package ru.geekbrains.skvortsov.cloud_storage;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;

public class Controller implements Initializable{

    @FXML
    ListView<String> filesListClient;

    @FXML
    ListView<String> filesListServer;

    @FXML
    TextField loginField;

    @FXML
    PasswordField passwordField;

    @FXML
    Button connectButton;

    @FXML
    HBox connectBox;

    @FXML
    Label labelOffline;

    @FXML
    Label labelOnline;

    private DataOutputStream out;

    private File clientLocalStorage;

    private byte [][] serverFilesArrBytes;

    private CountDownLatch refreshCDL;
    private CountDownLatch downloadCDL;
    private CountDownLatch deleteCDL;
    private CountDownLatch connectCDL;
    private CountDownLatch renameCDL;

    private boolean connectStatus;
    private byte connectType;

    private CallbackObj sendToController = new CallbackObj() {
        @Override
        public void callback(Object obj) {
            if (obj instanceof DataOutputStream) out = (DataOutputStream) obj;
            if (obj instanceof File) clientLocalStorage = (File) obj;
            if (obj instanceof byte[][]) serverFilesArrBytes = (byte[][]) obj;
        }
    };

    private CallbackCmd finishOperation = new CallbackCmd() {
        @Override
        public void callback(byte commandByte) {

            if(commandByte == (byte) 21) {
                connectCDL.countDown();
                connectType = (byte) 21;
                connectStatus = true;
            }
            if(commandByte == (byte) 22) {
                connectCDL.countDown();
                connectType = (byte) 22;
            }
            if(commandByte == (byte) 23) {
                connectCDL.countDown();
                connectType = (byte) 23;
            }
            if(commandByte == (byte) 1) refreshCDL.countDown();
            if(commandByte == (byte) 11) downloadCDL.countDown();
            if(commandByte == (byte) 2) deleteCDL.countDown();
            if(commandByte == (byte) 3) renameCDL.countDown();
        }
    };


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Net.getInstance().startConnect(sendToController, finishOperation);
        refreshClientFileList(filesListClient);

        //настройка сворачивания элементов
        connectBox.managedProperty().bind(connectBox.visibleProperty());
        labelOnline.managedProperty().bind(labelOnline.visibleProperty());
        labelOffline.managedProperty().bind(labelOffline.visibleProperty());
        labelOnline.setVisible(false);
        labelOffline.setVisible(true);
        connectBox.setVisible(true);

        //настройка изменяемости по клику мыши списка файлов клиента на локале
        filesListClient.setEditable(true);
        filesListClient.setCellFactory(TextFieldListCell.forListView());
        filesListClient.setOnEditCommit(new EventHandler<ListView.EditEvent<String>>() {
            @Override
            public void handle(ListView.EditEvent event) {
                String oldFilename = filesListClient.getSelectionModel().getSelectedItem();
                filesListClient.getItems().set(event.getIndex(), (String) event.getNewValue());
                String newFilename = filesListClient.getItems().get(event.getIndex());

                new File(clientLocalStorage, oldFilename).renameTo(new File(clientLocalStorage, newFilename));
                refreshClientFileList(filesListClient);
            }
        });

        //настройка изменяемости по клику мыши списка файлов клиента в облаке
        filesListServer.setEditable(true);
        filesListServer.setCellFactory(TextFieldListCell.forListView());
        filesListServer.setOnEditCommit(new EventHandler<ListView.EditEvent<String>>() {
            @Override
            public void handle(ListView.EditEvent event) {
                String oldFilename = filesListServer.getSelectionModel().getSelectedItem();
                filesListServer.getItems().set(event.getIndex(), (String) event.getNewValue());
                String newFilename = filesListServer.getItems().get(event.getIndex());
                try {

                    renameFile(oldFilename, newFilename);

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось переименовать файл.", ButtonType.OK);
                    alert.setHeaderText("Ошибка.");
                    alert.showAndWait();
                }
                refreshClientFileList(filesListClient);
            }
        });

    }

    public void renameFile(String oldFileName, String newFileName) throws IOException, InterruptedException {
        renameCDL = new CountDownLatch(1);
        System.out.println("Отправка командного байта на переименование файла.");
        out.write((byte) 3);
        byte [] oldFileNameBytes = oldFileName.getBytes(StandardCharsets.UTF_8);
        out.writeInt(oldFileNameBytes.length);
        out.write(oldFileNameBytes);

        byte [] newFileNameBytes = newFileName.getBytes(StandardCharsets.UTF_8);
        out.writeInt(newFileNameBytes.length);
        out.write(newFileNameBytes);
        renameCDL.await();
        System.out.println("Файл переименован.");
    }

//кнопки

    public void btnConnect(ActionEvent actionEvent) {
        if(loginField.getText().equals("") || passwordField.getText().equals("")) return;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Alert alertInfo = new Alert(Alert.AlertType.INFORMATION,null);
                    alertInfo.setHeaderText("Подключение...");
                    alertInfo.show();

                    connect(loginField, passwordField);

                    alertInfo.close();
                    if(connectType == (byte) 21) {
                        System.out.println("Соединение установлено.");
                        connectBox.setVisible(false);
                        labelOffline.setVisible(false);
                        labelOnline.setVisible(true);
                        refreshServerFileList(filesListServer);
                    }
                    else if (connectType == (byte) 22){
                        System.out.println("Неверный логин/пароль.");
                        Alert alert = new Alert(Alert.AlertType.WARNING, "Неверный логин/пароль.", ButtonType.OK);
                        alert.setHeaderText("Ошибка.");
                        alert.showAndWait();
                    }
                    else if (connectType == (byte) 23){
                        System.out.println("Такой логин уже подключен");
                        Alert alert = new Alert(Alert.AlertType.WARNING, "Такой логин уже подключен", ButtonType.OK);
                        alert.setHeaderText("Ошибка.");
                        alert.showAndWait();
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void connect(TextField loginField, PasswordField passwordField) throws IOException, InterruptedException {
        connectCDL = new CountDownLatch(1);
        String login = loginField.getText().toLowerCase();
        String password = passwordField.getText();

        System.out.println("Отправка командного байта на подключение к серверу, логина, пароля.");
        out.write((byte) 20);
        out.writeInt(login.length());
        out.write(login.getBytes(StandardCharsets.UTF_8));

        out.writeInt(password.length());
        out.write(password.getBytes(StandardCharsets.UTF_8));

        connectCDL.await();
    }

//

    public void btnRefresh(ActionEvent actionEvent) {
        if(!connectStatus) return;
        refreshFileListAll(filesListClient, filesListServer);
    }

    public void refreshFileListAll(ListView<String> filesListClient, ListView<String> filesListServer){

        refreshClientFileList(filesListClient);
        refreshServerFileList(filesListServer);

    }

    public void refreshClientFileList(ListView<String> filesListClient){
        //локальная папка клиента
        filesListClient.getItems().clear();
        String[] clientFiles = clientLocalStorage.list();
        if(clientFiles != null) {
            for (String fileName : clientFiles) {
                filesListClient.getItems().add(fileName);
            }
        }
    }

    public void refreshServerFileList(ListView<String> filesListServer){
        //облако
        refreshCDL = new CountDownLatch(1);
        try {
            System.out.println("Отправка командного байта на запрос списка файлов с сервера.");
            out.writeByte((byte) 1);
            refreshCDL.await();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось запросить список файлов у сервера.", ButtonType.OK);
            alert.setHeaderText("Ошибка.");
            alert.showAndWait();
        }

        String [] serverFilesArrStr = new String[serverFilesArrBytes.length];
        for (int i = 0; i < serverFilesArrStr.length; i++) {
            serverFilesArrStr[i] = new String(serverFilesArrBytes[i], StandardCharsets.UTF_8);
        }

        filesListServer.getItems().clear();
        if(serverFilesArrStr != null) {
            for (String fileName : serverFilesArrStr) {
                filesListServer.getItems().add(fileName);
            }
        }
    }

//

    public void btnDownloadFile(ActionEvent actionEvent) {
        if(!filesListServer.isFocused() || !connectStatus) return;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Alert alertInfo = new Alert(Alert.AlertType.INFORMATION, null);
                    alertInfo.setHeaderText("Скачивание файла...");
                    alertInfo.show();

                    downloadFile(filesListServer.getSelectionModel().getSelectedItem());

                    refreshClientFileList(filesListClient);

                    alertInfo.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось скачать файл.", ButtonType.OK);
                    alert.setHeaderText("Ошибка.");
                    alert.showAndWait();
                }
            }
        });
    }

    public void downloadFile(String fileName) throws IOException, InterruptedException {
        downloadCDL = new CountDownLatch(1);
        Net.getInstance().setFileNameStr(fileName);
        out.write((byte) 11);
        System.out.println("Отправка командного байта на скачивание файла.");
        byte [] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        out.writeInt(fileNameBytes.length);
        System.out.println("Отправка размера имени файла: " + fileNameBytes.length);
        out.write(fileNameBytes);
        System.out.println("Отправка имени файла.");
        downloadCDL.await();
    }

//

    public void btnSendFile(ActionEvent actionEvent) {
        if(!filesListClient.isFocused() || !connectStatus) return;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                File file = new File(clientLocalStorage, filesListClient.getSelectionModel().getSelectedItem());
                try {
                    Alert alertInfo = new Alert(Alert.AlertType.INFORMATION, null);
                    alertInfo.setHeaderText("Загрузка файла...");
                    alertInfo.show();

                    sendFile(file);

                    refreshServerFileList(filesListServer);

                    alertInfo.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось отправить файл.", ButtonType.OK);
                    alert.setHeaderText("Ошибка.");
                    alert.showAndWait();
                }
            }
        });
    }

    public void sendFile(File file) throws IOException {
        String fileName = file.getName();
        out.write((byte) 10);
        System.out.println("Отправка командного байта на загрузку файла.");
        byte [] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        out.writeInt(fileNameBytes.length);
        System.out.println("Отправка размера имени файла: " + fileNameBytes.length);
        out.write(fileNameBytes);
        System.out.println("Отправка имени файла.");
        out.writeLong(file.length());
        System.out.println("Отправка размера файла: " + file.length());
        byte [] buffer = new byte [256];
        FileInputStream fis = new FileInputStream(file);
        int readedBytes = 0;
        System.out.println("Отправка файла.");
        while ((readedBytes = fis.read(buffer)) > 0) {
            out.write(buffer, 0, readedBytes);
        }
        fis.close();
        System.out.println("Файл передан.");
    }

//

    public void btnDel(ActionEvent actionEvent) {
        if(!connectStatus) return;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {

                    deleteFile(filesListClient, filesListServer);

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось удалить файл.", ButtonType.OK);
                    alert.setHeaderText("Ошибка.");
                    alert.showAndWait();
                }
            }
        });
    }

    public void deleteFile(ListView<String> filesListClient, ListView<String> filesListServer) throws IOException, InterruptedException {
        if(filesListClient.isFocused()){
            new File(clientLocalStorage, filesListClient.getSelectionModel().getSelectedItem()).delete();
            refreshClientFileList(filesListClient);
        }
        if(filesListServer.isFocused()){
            String fileName = filesListServer.getSelectionModel().getSelectedItem();
            byte [] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            System.out.println("Отправка командного байта на удаление файла с сервера.");
            deleteCDL = new CountDownLatch(1);
            out.write((byte) 2);
            out.writeInt(fileNameBytes.length);
            out.write(fileNameBytes);
            deleteCDL.await();
            System.out.println("Файл удален.");
            refreshServerFileList(filesListServer);
        }
    }

//

    public void btnExit(ActionEvent actionEvent) {
        try {
            out.write((byte) 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Platform.exit();
    }

}
