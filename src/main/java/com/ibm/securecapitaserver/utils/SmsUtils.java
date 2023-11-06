package com.ibm.securecapitaserver.utils;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import static com.twilio.rest.api.v2010.account.Message.creator;


public class SmsUtils {

    public static void sendSms(String to, String fromNumber, String messageBody, String sidKey, String tokenKey) {
        Twilio.init(sidKey, tokenKey);
        Message message = creator(new PhoneNumber(to), new PhoneNumber(fromNumber), messageBody).create();

        System.out.println(message);
    }
}
