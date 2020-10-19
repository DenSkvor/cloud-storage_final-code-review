package ru.geekbrains.skvortsov.cloud_storage;

import javax.xml.transform.Result;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuthService {

    private final ArrayList<String>clientOnlineList = new ArrayList<>();

    private static AuthService instance;
    public static AuthService getInstance(){
        if(instance == null) return instance = new AuthService();
        return instance;
    }
    private AuthService() {
    }


    private static final String url = "jdbc:mysql://localhost:3306/cloud_storage_client_db?serverTimezone=Europe/Moscow";
    private static final String user = "root";
    private static final String password = "123";

    private Connection connection;
    private Statement statement;


    public void start() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(url,user,password);
        statement = connection.createStatement();
        System.out.println("Сервер авторизации запущен.");
    }

    public void stop() {
        clientOnlineList.clear();
        try {
            if(statement != null) statement.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        try {
            if(connection != null) connection.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        System.out.println("Сервер авторизации остановлен.");
    }

    public String getStorageByLoginAndPass(String login, String password) {
        try {
            ResultSet resultSet = statement.executeQuery("SELECT client_storage_url FROM client_tbl WHERE client_login = '" + login + "' AND client_password = '" + password + "'");
            if(resultSet.next()) return resultSet.getString(1);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }

    public void addClientInOnlineList(String userLogin){
        clientOnlineList.add(userLogin);
    }

    public void removeClientFromOnlineList(String userLogin){
        clientOnlineList.remove(userLogin);
    }

    public boolean checkIsClientOnline(String userLogin){
        for (String login : clientOnlineList) {
            if (login.equals(userLogin)) return true;
        }
        return false;
    }

}

