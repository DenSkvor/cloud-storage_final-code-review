package ru.geekbrains.skvortsov.cloud_storage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;

public class ServerCommon {

    private enum State {

        WAIT_STRING_LENGTH, WAIT_STRING

    }

    private State currentState = State.WAIT_STRING_LENGTH;

    private final ByteBuf intBuffer = ByteBufAllocator.DEFAULT.directBuffer(4);

    private ByteBuf strBuffer;

    private int stringLength;


    public String receiveString(ByteBuf buf){
        //получение длины строки
        if (currentState == State.WAIT_STRING_LENGTH) {
            while (buf.readableBytes() > 0 && intBuffer.writableBytes() > 0) {
                intBuffer.writeByte(buf.readByte());
            }
            if(intBuffer.readableBytes() < 4) return null;

            System.out.println("Получение длины строки.");
            stringLength = intBuffer.readInt();
            intBuffer.clear();
            System.out.println("Длина строки: " + stringLength);
            currentState = State.WAIT_STRING;
        }

        //получение строки
        if (currentState == State.WAIT_STRING) {
            if(strBuffer == null) strBuffer = ByteBufAllocator.DEFAULT.directBuffer(stringLength);
            while (buf.readableBytes() > 0 && strBuffer.writableBytes() > 0) {
                strBuffer.writeByte(buf.readByte());
            }
            if(strBuffer.readableBytes() < stringLength) return null;

            System.out.println("Получение строки.");
            byte[] stringBytes = new byte[stringLength];
            strBuffer.readBytes(stringBytes);
            strBuffer.release();
            strBuffer = null;
            String receivedString = new String(stringBytes, StandardCharsets.UTF_8);
            currentState = State.WAIT_STRING_LENGTH;
            return receivedString;
        }
        return null;
    }


    public ByteBuf prepByteBuf(int length, byte command){
        ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(length);
        buffer.writeByte(command);
        return buffer;
    }

    public ByteBuf prepByteBuf(int length, byte command, int contentSize){
        ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(length);
        buffer.writeByte(command);
        buffer.writeInt(contentSize);
        return buffer;
    }

    public ByteBuf prepByteBuf(int length, byte command, long contentSize){
        ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(length);
        buffer.writeByte(command);
        buffer.writeLong(contentSize);
        return buffer;
    }

    public ByteBuf prepByteBuf(int length, byte command, int contentSize, byte[] content){
        ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(length);
        buffer.writeByte(command);
        buffer.writeInt(contentSize);
        buffer.writeBytes(content);
        return buffer;
    }

    public ByteBuf prepByteBuf(int length, int contentSize, byte[] content){
        ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(length);
        buffer.writeInt(contentSize);
        buffer.writeBytes(content);
        return buffer;
    }

}
