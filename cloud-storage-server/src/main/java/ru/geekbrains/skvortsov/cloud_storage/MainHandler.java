package ru.geekbrains.skvortsov.cloud_storage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainHandler extends ChannelInboundHandlerAdapter {

    private enum State {
        WAIT_COMMAND,

        WAIT_LOGIN,

        WAIT_STORAGE_URL,

        WAIT_FILE_NAME, WAIT_FILE_LENGTH, WAIT_FILE, FILE_TRANSFER,

        WAIT_OLD_FILE_NAME, WAIT_NEW_FILE_NAME, RENAME_FILE

    }

    private State currentState = State.WAIT_COMMAND;
    private ServerCommon serverCommonMethods;
    private byte readed;
    private long fileLength;
    private long receivedFileLength;
    private String oldFileNameStr;
    private String newFileNameStr;
    private BufferedOutputStream out;
    private ByteBuf longBuffer;
    private String clientCloudStorage;
    private String clientLogin;

    public MainHandler(){
        serverCommonMethods = new ServerCommon();
        longBuffer = ByteBufAllocator.DEFAULT.directBuffer(8);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + ctx.channel() + " отключился.");
        AuthService.getInstance().removeClientFromOnlineList(clientLogin);
        longBuffer.release();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = ((ByteBuf) msg);
        System.out.println("\nПоступило сообщение от клинта.");

        while (buf.readableBytes() > 0) {
            System.out.println("Вычитываем байтбуф.");

            if (currentState == State.WAIT_COMMAND) {
                System.out.println("Определение типа команды.");
                readed = buf.readByte();
            }

            if (readed == (byte) 10) {
                System.out.println("Команда на загрузку файла с клиента на сервер.");
                receiveFileToServer(buf);
            }

            else if (readed == (byte) 11){
                System.out.println("Команда на скачивание файла с сервера.");
                sendFileToClient(buf, ctx);
            }

            else if (readed == (byte) 1) {
                System.out.println("Команда на предоставление списка файлов.");
                sendFileListToClient(ctx);
            }

            else if(readed == (byte) 2){
                System.out.println("Команда на удаление файла.");
                deleteFile(buf, ctx);
            }

            else if(readed == (byte) 3){
                System.out.println("Команда на переименование файла.");
                renameFile(buf, ctx);
            }

            else if(readed == (byte) 21){
                System.out.println("Команда на получение из хендлера авторизации адреса папки клиента в облаке.");
                receiveClientCloudStorageUrl(buf);
            }

            else if(readed == (byte) 0){
                System.out.println("Команда на отключение клиента.");
                disconnectClient(ctx);
            }
            else {
                System.out.println("Ошибка: Неверный командный байт: " + readed);
            }
        }
        if (buf.readableBytes() == 0) {
            System.out.println("Байтбуф полностью вычитан.");
            buf.release();
            System.out.println("Байтбуф обнулен.");
        }
    }


    private void receiveFileToServer(ByteBuf buf) throws IOException {

        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_FILE_NAME;
            receivedFileLength = 0L;
            System.out.println("Старт загрузки файла на сервер.");
        }

        if (currentState == State.WAIT_FILE_NAME) {
            System.out.println("Получение имени файла.");
            String fileNameStr = serverCommonMethods.receiveString(buf);
            if (fileNameStr == null) return;
            System.out.println("Имя файла: " + fileNameStr);
            out = new BufferedOutputStream(new FileOutputStream(new File(clientCloudStorage, fileNameStr)));
            currentState = State.WAIT_FILE_LENGTH;
        }

        if (currentState == State.WAIT_FILE_LENGTH) {
            System.out.println("Получение размера файла.");
            while (buf.readableBytes() > 0 && longBuffer.writableBytes() > 0) {
                longBuffer.writeByte(buf.readByte());
            }
            if(longBuffer.readableBytes() < 8) return;

            fileLength = longBuffer.readLong();
            longBuffer.clear();
            System.out.println("Размер файла: " + fileLength);
            currentState = State.WAIT_FILE;
        }

        if (currentState == State.WAIT_FILE) {
            System.out.println("Получение файла.");
            while (buf.readableBytes() > 0) {
                out.write(buf.readByte());
                //System.out.println(".");
                receivedFileLength++;
                if (fileLength == receivedFileLength) {
                    currentState = State.WAIT_COMMAND;
                    System.out.println("Файл передан.");
                    out.close();
                    break;
                }
            }
        }
    }


    private void sendFileToClient(ByteBuf buf, ChannelHandlerContext ctx) throws IOException {
        File file = null;

        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_FILE_NAME;
            System.out.println("Старт передачи файла клиенту.");
        }

        if (currentState == State.WAIT_FILE_NAME) {
            System.out.println("Получение имени файла.");
            String fileNameStr = serverCommonMethods.receiveString(buf);
            if(fileNameStr == null) return;
            System.out.println("Имя файла: " + fileNameStr);
            file = new File(clientCloudStorage, fileNameStr);

            currentState = State.FILE_TRANSFER;
        }

        if(currentState == State.FILE_TRANSFER) {
            FileRegion region = new DefaultFileRegion(file, 0, file.length());
            System.out.println("Размер файла: " + file.length());
            System.out.println("Отправка командного сообщения.");
            ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf((1 + 8), (byte) 11, file.length()));
            System.out.println("Отправка файла.");
            ChannelFuture transferOperationFuture = ctx.channel().writeAndFlush(region);

            currentState = State.WAIT_COMMAND;
            System.out.println("Файл отправлен.");
        }
    }


    private void sendFileListToClient(ChannelHandlerContext ctx){
        System.out.println("Старт передачи списка файлов клиенту.");
        //конвертируем список файлов в массив[][] байт
        String[] serverFiles = new File(clientCloudStorage).list();
        byte [][] serverFilesArrBytes = new byte[serverFiles.length][];
        for (int i = 0; i < serverFiles.length; i++) {
            serverFilesArrBytes[i] = serverFiles[i].getBytes(StandardCharsets.UTF_8);
        }

        //подготовка буфера с командным байтом и длиной массива [][]
        System.out.println("Отправка командного сообщения.");
        ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf((1 + 4), (byte) 1, serverFiles.length));

        for (int i = 0; i < serverFiles.length; i++){
            System.out.println("Отправка буфера с размером и именем файла");
            ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf((4 + serverFilesArrBytes[i].length), serverFilesArrBytes[i].length, serverFilesArrBytes[i]));
        }

        currentState = State.WAIT_COMMAND;
        System.out.println("Список отправлен.");
    }


    private void deleteFile(ByteBuf buf, ChannelHandlerContext ctx){

        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_FILE_NAME;
            System.out.println("Старт удаления файла.");
        }

        if (currentState == State.WAIT_FILE_NAME) {
            System.out.println("Получение имени файла.");

            String fileNameStr = serverCommonMethods.receiveString(buf);
            if(fileNameStr == null) return;
            System.out.println("Имя файла: " + fileNameStr);

            new File(clientCloudStorage, fileNameStr).delete();

            System.out.println("Файл " + fileNameStr + " удален.");
            //отправка командного байта об успешном удалении
            ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf(1, (byte) 2));

            currentState = State.WAIT_COMMAND;
        }
    }


    private void renameFile(ByteBuf buf, ChannelHandlerContext ctx){
        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_OLD_FILE_NAME;
            System.out.println("Старт переименования файла.");
        }

        if (currentState == State.WAIT_OLD_FILE_NAME) {
            System.out.println("Получение старого имени файла.");
            if((oldFileNameStr = serverCommonMethods.receiveString(buf)) == null) return;
            System.out.println("Старое имя файла: " + oldFileNameStr);
            currentState = State.WAIT_NEW_FILE_NAME;
        }

        if(currentState == State.WAIT_NEW_FILE_NAME) {
            System.out.println("Получение нового имени файла.");
            if((newFileNameStr = serverCommonMethods.receiveString(buf)) == null) return;
            System.out.println("Новое имя файла: " + newFileNameStr);
            currentState = State.RENAME_FILE;
        }

        if (currentState == State.RENAME_FILE) {
            new File(clientCloudStorage, oldFileNameStr).renameTo(new File(clientCloudStorage, newFileNameStr));
            //отправка командного байта об успешном переименовании
            ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf(1, (byte) 3));

            currentState = State.WAIT_COMMAND;
        }
    }


    private void receiveClientCloudStorageUrl(ByteBuf buf){
        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_STORAGE_URL;
            System.out.println("Старт получения адреса папки клиента.");
        }

        if (currentState == State.WAIT_STORAGE_URL) {
            if((clientCloudStorage = serverCommonMethods.receiveString(buf)) == null) return;
            System.out.println("Адрес: " + clientCloudStorage);

            currentState = State.WAIT_LOGIN;
        }

        if (currentState == State.WAIT_LOGIN) {
            System.out.println("Старт получения логина пользователя.");
            if ((clientLogin = serverCommonMethods.receiveString(buf)) == null) return;
            System.out.println("Логин: " + clientLogin);

            currentState = State.WAIT_COMMAND;
        }
    }


    private void disconnectClient(ChannelHandlerContext ctx){
        System.out.println("Старт отключения клиента: " + clientLogin);
        ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf(1, (byte) 0));
        ctx.channel().close();
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

}



