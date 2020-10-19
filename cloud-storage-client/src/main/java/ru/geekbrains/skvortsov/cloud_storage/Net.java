package ru.geekbrains.skvortsov.cloud_storage;

import java.io.*;
import java.net.Socket;

public class Net{

    private static final String SERVER_ADDR = "localhost";
    private static final int SERVER_PORT = 8189;

    private DataInputStream in;
    private DataOutputStream out;
    private Socket socket;

    private final File file = new File("client_local_storage");
    private final File clientLocalStorage = new File(file.getAbsolutePath());

    private String fileNameStr;
    private byte [][] serverFilesArrBytes;


    public DataOutputStream getOut(){
        return out;
    }

    public void setFileNameStr(String fileName){
        fileNameStr = fileName;
    }


    private static Net instance;
    public static Net getInstance(){
        if(instance == null) return instance = new Net();
        else return instance;
    }

    private Net(){
    }

    public void startConnect(CallbackObj sendToController, CallbackCmd finishOperation) {
        try {
            socket = new Socket(SERVER_ADDR, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            sendToController.callback(out);
            sendToController.callback(clientLocalStorage);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Клиент запущен.");
                    while (true) {
                        System.out.println("Ожидание байт");
                        byte readedByte = in.readByte();

                        if(readedByte == (byte) 11) {
                            System.out.println("Ответная команда на скачивание файла с сервера.");
                            download();
                            finishOperation.callback((byte) 11);

                        } else if(readedByte == (byte) 1) {
                            System.out.println("Ответная команда на получение списка файлов с сервера.");
                            receiveServerFileList();
                            sendToController.callback(serverFilesArrBytes);
                            finishOperation.callback((byte) 1);

                        } else if(readedByte == (byte) 2){
                            System.out.println("Ответная команда об удалении файла с сервера.");
                            finishOperation.callback((byte) 2);
                            System.out.println("Файл удален.");
                        }

                        else if(readedByte == (byte) 3){
                            System.out.println("Ответная команда о переименовании файла на сервере.");
                            finishOperation.callback((byte) 3);
                            System.out.println("Файл переименован.");
                        }

                        else if(readedByte == (byte) 21){
                            System.out.println("Ответная команда об авторизации на сервере.");
                            //connectStatus = true;
                            finishOperation.callback((byte) 21);
                            System.out.println("Подключение к облаку успешно осуществлено.");
                        }

                        else if(readedByte == (byte) 22){
                            finishOperation.callback((byte) 22);
                            System.out.println("Неудачная попытка подключения к облаку. Неверный логин/пароль.");
                        }

                        else if(readedByte == (byte) 23){
                            finishOperation.callback((byte) 23);
                            System.out.println("Неудачная попытка подключения к облаку. Такой пользователь уже подключен.");
                        }

                        else if(readedByte == (byte) 0) {
                            System.out.println("Ответная команда на отключение от сервера.");
                            return;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    close();
                }
            }
        });

        t.start();

        } catch (IOException e) {
            e.printStackTrace();
            close();
        }
    }


    public void download() throws IOException {
        System.out.println("Старт скачивания файла с сервера.");
        //получаем размер файла
        long fileSize = in.readLong();
        long receivedFileLength = 0L;
        System.out.println("Размер файла: " + fileSize);
        //готовимся качать файл. готовим буфер, открываем стрим в файл
        FileOutputStream fos = new FileOutputStream(new File(clientLocalStorage, fileNameStr));
        byte [] buffer = new byte[256];
        int readedBytes;
        while(receivedFileLength != fileSize) {
            readedBytes = in.read(buffer);
            fos.write(buffer, 0, readedBytes);
            receivedFileLength += readedBytes;
        }
        fos.close();

        System.out.println("Файл скачан.");
    }

    public void receiveServerFileList() throws IOException {
        System.out.println("Старт получения списка файлов с сервера.");
        //получаем размер байтового массива [][]
        int serverFilesArrBytesSize = in.readInt();
        System.out.println("Размер байтового массива [][] списка файлов: " + serverFilesArrBytesSize);
        //готовимся качать массив
        serverFilesArrBytes = new byte[serverFilesArrBytesSize][];
        byte [] serverFile;
        System.out.println("Скачиваем массив [][] байт.");
        for (int i = 0; i < serverFilesArrBytesSize; i++){
            serverFile = new byte[in.readInt()];
            in.read(serverFile);
            serverFilesArrBytes[i] = serverFile;
            System.out.println("Файл " + i);
        }
        System.out.println("Список файлов с сервера получен.");
    }

    public void close() {
        try {
            if(out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if(in != null) in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if(socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Клиент отключен.");
    }

}
