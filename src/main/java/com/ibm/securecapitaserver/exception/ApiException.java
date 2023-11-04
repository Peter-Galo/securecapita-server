package com.ibm.securecapitaserver.exception;

public class ApiException extends RuntimeException{

    public ApiException(String message){
        super(message);
    }
}
