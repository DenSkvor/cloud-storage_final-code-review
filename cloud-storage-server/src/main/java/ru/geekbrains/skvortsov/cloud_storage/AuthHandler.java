package ru.geekbrains.skvortsov.cloud_storage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import java.nio.charset.StandardCharsets;


public class AuthHandler extends ChannelInboundHandlerAdapter {

    private enum State {
        WAIT_COMMAND,

        WAIT_LOGIN, WAIT_PASSWORD,

        CHECK_AUTH_DATA
    }

    private State currentState = State.WAIT_COMMAND;
    private ServerCommon serverCommonMethods;
    private byte readed;
    private String loginStr;
    private String passwordStr;


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + ctx.channel() + " подключился.");
        serverCommonMethods = new ServerCommon();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + ctx.channel() + " отключился.");
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

            if (readed == (byte) 20) {
                System.out.println("Команда на авторизацию.");
                authClient(buf, ctx);
            }

            else if(readed == (byte) 0){
                System.out.println("Команда на отключение клиента.");
                disconnectClient(ctx);
            }
            else {
                System.out.println("ERROR: Неверный командный байт: " + readed);
            }
        }
        if (buf.readableBytes() == 0) {
            System.out.println("Байтбуф полностью вычитан.");
            buf.release();
            System.out.println("Байтбуф обнулен.");
        }
    }


    private void authClient(ByteBuf buf, ChannelHandlerContext ctx) {

        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_LOGIN;
            System.out.println("Старт получения логина/пароля клиента.");
        }

        if(currentState == State.WAIT_LOGIN) {
            System.out.println("Получение логина.");
            loginStr = serverCommonMethods.receiveString(buf);
            if (loginStr == null) return;
            System.out.println("Логин: " + loginStr);
            currentState = State.WAIT_PASSWORD;
        }

        if(currentState == State.WAIT_PASSWORD) {
            System.out.println("Получение пароля.");
            passwordStr = serverCommonMethods.receiveString(buf);
            if (passwordStr == null) return;
            System.out.println("Пароль: " + passwordStr);
            currentState = State.CHECK_AUTH_DATA;
        }

        if(currentState == State.CHECK_AUTH_DATA){
            System.out.println("Проверка полученных данных на наличие в базе и получение адреса папки клиента.");
            if(checkAuthData(ctx)) ctx.channel().pipeline().remove(this);
        }

        currentState = State.WAIT_COMMAND;
    }

    private boolean checkAuthData(ChannelHandlerContext ctx){
        //Проверка пользователя на повторное подключение по одному логину
        if(AuthService.getInstance().checkIsClientOnline(loginStr)){
            //отправка обратного командного байта
            ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf(1, (byte) 23));
            System.out.println("Такой клиент уже подключен.");
            currentState = State.WAIT_COMMAND;
            return false;
        }
        //проверка логина/пароля на наличие в базе и получение адреса папки клиента
        String storageUrl = AuthService.getInstance().getStorageByLoginAndPass(loginStr,passwordStr);
        System.out.println("Адрес: " + storageUrl);
        //если данные не найдены, отсылаем командный байт об ошибке
        if(storageUrl == null){
            ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf(1, (byte) 22));
            System.out.println("Данные не найдены. Неверный логин/пароль.");
            return false;
            //если данные найдены, отсылаем клиенту обратный командный байт об успешном подключении
            //передаем сам адрес + логин в виде строки в следующий хендлер
        } else {
            AuthService.getInstance().addClientInOnlineList(loginStr);
            System.out.println("Данные найдены.");
            ctx.channel().writeAndFlush(serverCommonMethods.prepByteBuf(1, (byte) 21));

            byte[] storageUrlBytes = storageUrl.getBytes(StandardCharsets.UTF_8);
            ctx.fireChannelRead(serverCommonMethods.prepByteBuf((1 + 4 + storageUrlBytes.length), (byte) 21, storageUrlBytes.length, storageUrlBytes));

            byte[] loginBytes = loginStr.getBytes(StandardCharsets.UTF_8);
            ctx.fireChannelRead(serverCommonMethods.prepByteBuf((4 + loginBytes.length), loginBytes.length, loginBytes));

            return true;
        }

    }

    private void disconnectClient(ChannelHandlerContext ctx){
        ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1);
        buffer.writeByte((byte) 0);
        ctx.channel().writeAndFlush(buffer);
        ctx.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

}
