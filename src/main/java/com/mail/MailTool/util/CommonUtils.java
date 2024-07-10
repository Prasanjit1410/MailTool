package com.mail.MailTool.util;


import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mail.MailTool.constant.CommonConstants;
import java.util.AbstractMap.SimpleEntry;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;

@Component
@Log4j2
public class CommonUtils {

    public static final boolean SORT_BY_REGION = true;

    public InternityUser setUser(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();

        InternityUser user = new InternityUser();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                //System.out.println("Header: "+ headerName +".." + request.getHeader(headerName));
                if(headerName.equals(CommonConstants.AUTH_UID)) {
                    user.setUId(request.getHeader(CommonConstants.AUTH_UID));
                }
            }
        }


        return user;
    }

    /**
     * This is getRoleFrom token method. Used to get role from token
     * @param token
     * @param key
     * @return String as a response.
     */
    public static String getRoleFromToken(String token, String key) {
        String authToken = token.substring(7);
        SecretKey secretKey = Keys.hmacShaKeyFor(key.getBytes());
        Claims body = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(authToken)
                .getBody();
        List<Map<String, String>> authorities = (List<Map<String, String>>) body.get("authorities");
        String role = authorities.get(0).get("authority");

        return role;
    }

    /**
     * This is getRoleFrom token method. Used to get subject from token
     * @param token
     * @param key
     * @return String as a response.
     */
    public static String getSubjectFromToken(String token, String key) {
        String authToken = token.substring(7);
        SecretKey secretKey = Keys.hmacShaKeyFor(key.getBytes());
        Claims body = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(authToken)
                .getBody();
        String subject = body.get("sub").toString();
        return subject;
    }

    /**
     * This is isOTPExpired method. Used to check whether the OTP is expired or not
     * @param time
     * @return Boolean as a response.
     */
    public static Boolean isOTPExpired(String time, Integer otpExpiration){
        LocalDateTime createDateTime = LocalDateTime.parse(time);
        LocalDateTime currentDateTime = LocalDateTime.now();
        Duration duration = Duration.between(createDateTime, currentDateTime);
        Long difference = duration.toMinutes();
        if (difference > otpExpiration) {
            return true;
        }
        return false;
    }

    /**
     * This method is used to generate random OTP
     * @return String as a response
     */
    public static String generateOTP() {
        String numbers = "0123456789";
        Random random = new Random();
        char[] otp = new char[6];
        for (int i = 0; i < 6; i++) {
            otp[i] = numbers.charAt(random.nextInt(numbers.length()));
        }
        return String.valueOf(123456);
    }

    /**
     * This method is responsible for calculating age.
     * @param stringDateOfBirth
     * @param currentDate
     * @return This method returns age of user
     */
    public static int calculateAge(String stringDateOfBirth, LocalDateTime currentDate) {
        int age = 0;
        if (stringDateOfBirth != null && !stringDateOfBirth.equalsIgnoreCase("")) {
            LocalDateTime birthDate = LocalDateTime.parse(stringDateOfBirth);
            age = currentDate.getYear() - birthDate.getYear();
        }
        return age;
    }

    /**
     * This method is used for Mobile number validation.
     * @param mobileNumber
     * @return Boolean
     */
    public static Boolean isMobileValid(String mobileNumber) {
        Pattern p = Pattern.compile("(0/91)?[6-9][0-9]{9}");
        Matcher m = p.matcher(mobileNumber);
        return (m.find() && m.group().equals(mobileNumber));
    }

    /**
     * This method is used for Email validation.
     * @param email
     * @return Boolean
     */
    public static boolean isEmailValid(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\." +
                "[a-zA-Z0-9_+&*-]+)*@" +
                "(?:[a-zA-Z0-9-]+\\.)+[a-z" +
                "A-Z]{2,7}$";

        Pattern pat = Pattern.compile(emailRegex);
        if (email == null)
            return false;
        return pat.matcher(email).matches();
    }

    public Map<String, String> message(HttpStatus status, String message){
        Map<String, String> msg = new HashMap<>();
        if(status.is2xxSuccessful()){
            msg.put("msg",StringUtils.isEmpty(message)?CommonConstants.APP_SUCCESS:message);
        }
        else {
            msg.put("msg", StringUtils.isEmpty(message)?CommonConstants.APP_FAILED:message);
        }
        return msg;
    }

    public static void main(String[] args) {
        new CommonUtils().getZoneAndOffSet3();
    }
    public Map<String, String> getZoneAndOffSet3() {

        LocalDateTime localDateTime = LocalDateTime.now();
        System.out.println(ZoneId.getAvailableZoneIds().size());
        Map<String, String> result = ZoneId.getAvailableZoneIds()
                .stream()
                .map(ZoneId::of)
                .map(zoneId -> new SimpleEntry<>(zoneId.toString(), "(UTC"+ localDateTime.atZone(zoneId)
                        .getOffset()
                        .getId()
                        .replaceAll("Z", "+00:00") + ")"))
                .sorted(SORT_BY_REGION
                        ? Map.Entry.comparingByKey()
                        : Map.Entry.<String, String>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        SimpleEntry::getKey,
                        SimpleEntry::getValue,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));

        return  result;

    }

}